// Owner: Nguoi4

package exam.tools;

import exam.client.ml.AnomalyDetector;
import exam.client.ml.IsolationForestScorer;
import exam.common.ml.ChronosJudge;
import exam.common.ml.MlDecision;
import exam.common.ml.MlResult;
import exam.common.ml.Quantiles;
import exam.common.model.Metrics;
import exam.common.model.MlMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Phát lại (replay) một bộ trace qua từng chế độ ml.mode và đo precision, recall, F1, độ trễ phát hiện.
 *
 * Mỗi trace là một phiên độc lập (một "máy") và có thể dài ngắn khác nhau. Cách phát lại, với mỗi bước t:
 *   Isolation Forest : mỗi trace có một AnomalyDetector riêng (học `trainSamples` mẫu đầu rồi chấm từng mẫu) -
 *                      đúng quy trình client thật.
 *   Chronos          : tại mỗi bước, gom `window` điểm gần nhất (trước bước t) của mọi trace còn dữ liệu thành MỘT batch
 *                      gửi ml-service, so giá trị thật ở bước t với khoảng q0.1-q0.9 (ChronosJudge, N lần liên tiếp).
 *   Ghép theo ml.mode: MlDecision (NONE, IF, CHRONOS, BOTH_OR, BOTH_AND).
 * Chỉ các mẫu SAU giai đoạn học (t >= trainSamples) được đánh giá, để mọi chế độ so sánh công bằng.
 *
 * Nhãn (TraceRow.label/category) chỉ dùng để TÍNH KẾT QUẢ, không bao giờ đưa vào mô hình.
 * Sau run(), getDetails() trả điểm và dự báo thô của từng mẫu để phân tích sâu (ScoreDump).
 */
public class ExperimentEngine {

    /** Tham số thực nghiệm (mặc định giống cấu hình thật). */
    public static class Config {
        public int trainSamples = 30;
        public int window = 60;
        public int chronosMinPoints = 12;
        public double ifThresholdMargin = 0.05;
        public int ifConsecutive = 3;
        public int chronosConsecutive = 3;
    }

    /** Dữ liệu thô của một mẫu: số liệu, nhãn (chỉ để đánh giá), điểm IF và dự báo Chronos. */
    public static class SampleDetail {
        public String traceId;
        public int t;
        public long time;
        public int label;
        public String category;
        public String scenario;
        public double[] vector;
        public boolean ifTraining;
        public double ifScore;
        public double ifThreshold;
        public int ifConsecutive;
        public boolean ifFlag;
        /** null nếu chưa đủ dữ liệu cho Chronos hoặc không chạy Chronos. Khóa: tên metric. */
        public Map<String, Quantiles> chronos;
        public int chronosWorstCount;
        public boolean chronosFlag;
    }

    /** Dữ liệu của một trace theo thứ tự thời gian. */
    private static class MachineTrace {
        final String machineId;
        final List<TraceRow> rows = new ArrayList<>();
        final List<SampleDetail> details = new ArrayList<>();

        MachineTrace(String machineId) {
            this.machineId = machineId;
        }
    }

    private final List<SampleDetail> allDetails = new ArrayList<>();

    public List<SampleDetail> getDetails() {
        return allDetails;
    }

    public List<ModeResult> run(List<TraceRow> rows, List<MlMode> modes, ChronosService chronos, Config config) {
        allDetails.clear();
        Map<String, MachineTrace> traces = groupByMachine(rows);
        removeTooShortTraces(traces, config.trainSamples);
        if (traces.isEmpty()) {
            throw new IllegalArgumentException("Không có trace nào dài hơn " + config.trainSamples + " mẫu (giai đoạn học)");
        }
        for (MachineTrace trace : traces.values()) {
            createDetails(trace);
        }

        boolean needIf = false;
        boolean needChronos = false;
        for (MlMode mode : modes) {
            needIf |= MlDecision.usesIsolationForest(mode);
            needChronos |= MlDecision.usesChronos(mode);
        }

        long ifStart = System.currentTimeMillis();
        if (needIf) {
            computeIsolationForest(traces, config);
        }
        long ifMillis = System.currentTimeMillis() - ifStart;

        String chronosProblem = null;
        long chronosMillis = 0;
        if (needChronos) {
            long chronosStart = System.currentTimeMillis();
            try {
                if (chronos == null) {
                    throw new IOException("không có ml-service");
                }
                computeChronos(traces, chronos, config);
            } catch (IOException e) {
                chronosProblem = "ml-service không dùng được: " + e.getMessage();
            }
            chronosMillis = System.currentTimeMillis() - chronosStart;
        }

        List<ModeResult> results = new ArrayList<>();
        for (MlMode mode : modes) {
            ModeResult result = new ModeResult(mode);
            if (MlDecision.usesChronos(mode) && chronosProblem != null) {
                result.skippedReason = chronosProblem;
                results.add(result);
                continue;
            }
            for (MachineTrace trace : traces.values()) {
                boolean[] flag = new boolean[trace.rows.size()];
                for (int t = 0; t < flag.length; t++) {
                    SampleDetail detail = trace.details.get(t);
                    flag[t] = MlDecision.isSuspicious(mode, detail.ifFlag, detail.chronosFlag);
                }
                evaluate(result, trace, flag, flag.length, config.trainSamples);
            }
            result.elapsedMillis = (MlDecision.usesIsolationForest(mode) ? ifMillis : 0)
                    + (MlDecision.usesChronos(mode) ? chronosMillis : 0);
            results.add(result);
        }

        for (MachineTrace trace : traces.values()) {
            allDetails.addAll(trace.details);
        }
        return results;
    }

    private Map<String, MachineTrace> groupByMachine(List<TraceRow> rows) {
        Map<String, MachineTrace> traces = new TreeMap<>();
        for (TraceRow row : rows) {
            traces.computeIfAbsent(row.machineId, MachineTrace::new).rows.add(row);
        }
        for (MachineTrace trace : traces.values()) {
            trace.rows.sort((a, b) -> Long.compare(a.time, b.time));
        }
        return traces;
    }

    /** Trace không dài hơn giai đoạn học thì không có mẫu nào để đánh giá: bỏ và nói rõ. */
    private void removeTooShortTraces(Map<String, MachineTrace> traces, int trainSamples) {
        List<String> tooShort = new ArrayList<>();
        for (MachineTrace trace : traces.values()) {
            if (trace.rows.size() <= trainSamples) {
                tooShort.add(trace.machineId);
            }
        }
        for (String machineId : tooShort) {
            System.out.println("[Experiment] Bỏ trace " + machineId + ": chỉ " + traces.get(machineId).rows.size()
                    + " mẫu, cần nhiều hơn " + trainSamples);
            traces.remove(machineId);
        }
    }

    private void createDetails(MachineTrace trace) {
        for (int t = 0; t < trace.rows.size(); t++) {
            TraceRow row = trace.rows.get(t);
            SampleDetail detail = new SampleDetail();
            detail.traceId = trace.machineId;
            detail.t = t;
            detail.time = row.time;
            detail.label = row.label;
            detail.category = row.category;
            detail.scenario = row.scenario;
            detail.vector = row.metrics.toVector();
            detail.ifTraining = true;
            trace.details.add(detail);
        }
    }

    private void computeIsolationForest(Map<String, MachineTrace> traces, Config config) {
        for (MachineTrace trace : traces.values()) {
            AnomalyDetector detector = new AnomalyDetector(new IsolationForestScorer(),
                    config.trainSamples, config.ifThresholdMargin, config.ifConsecutive);
            for (int t = 0; t < trace.rows.size(); t++) {
                AnomalyDetector.Result result = detector.observe(trace.details.get(t).vector);
                SampleDetail detail = trace.details.get(t);
                detail.ifTraining = result.phase == AnomalyDetector.Phase.TRAINING;
                detail.ifScore = result.score;
                detail.ifThreshold = result.threshold;
                detail.ifConsecutive = result.consecutive;
                detail.ifFlag = result.anomalous;
            }
        }
    }

    /** Mỗi bước một batch cho mọi trace còn dữ liệu, giống Server thật. Ném IOException nếu ml-service lỗi. */
    private void computeChronos(Map<String, MachineTrace> traces, ChronosService chronos, Config config) throws IOException {
        ChronosJudge judge = new ChronosJudge(config.chronosConsecutive);
        int longest = 0;
        for (MachineTrace trace : traces.values()) {
            longest = Math.max(longest, trace.rows.size());
        }

        for (int t = config.chronosMinPoints; t < longest; t++) {
            Map<String, Map<String, List<Double>>> batch = new LinkedHashMap<>();
            Map<String, Map<String, Double>> actualValues = new HashMap<>();
            for (MachineTrace trace : traces.values()) {
                if (t >= trace.rows.size()) {
                    continue;
                }
                Map<String, List<Double>> context = new LinkedHashMap<>();
                Map<String, Double> actual = new HashMap<>();
                int from = Math.max(0, t - config.window);
                for (int metric = 0; metric < Metrics.CHRONOS_DIMENSIONS; metric++) {
                    List<Double> values = new ArrayList<>();
                    for (int i = from; i < t; i++) {
                        values.add(trace.details.get(i).vector[metric]);
                    }
                    context.put(Metrics.VECTOR_NAMES[metric], values);
                    actual.put(Metrics.VECTOR_NAMES[metric], trace.details.get(t).vector[metric]);
                }
                batch.put(trace.machineId, context);
                actualValues.put(trace.machineId, actual);
            }

            Map<String, Map<String, Quantiles>> forecasts = chronos.forecast(batch);
            for (Map.Entry<String, Map<String, Quantiles>> entry : forecasts.entrySet()) {
                Map<String, Double> actual = actualValues.get(entry.getKey());
                if (actual == null) {
                    continue;
                }
                MlResult verdict = judge.judge(entry.getKey(), actual, entry.getValue());
                SampleDetail detail = traces.get(entry.getKey()).details.get(t);
                detail.chronos = entry.getValue();
                detail.chronosWorstCount = verdict.consecutiveOutOfRange;
                detail.chronosFlag = verdict.suspicious;
            }
        }
    }

    /** Cộng dồn TP/FP/FN/TN, số đoạn bất thường được phát hiện, báo nhầm theo loại; chỉ xét t >= trainSamples. */
    static void evaluate(ModeResult result, MachineTrace trace, boolean[] flag, int steps, int trainSamples) {
        boolean previousFalseAlarm = false;
        for (int t = trainSamples; t < steps; t++) {
            TraceRow row = trace.rows.get(t);
            boolean actualAnomaly = row.label == 1;
            boolean benign = TraceRow.CATEGORY_BENIGN.equals(row.category);
            boolean falseAlarm = !actualAnomaly && flag[t];

            if (actualAnomaly && flag[t]) {
                result.truePositives++;
            } else if (actualAnomaly) {
                result.falseNegatives++;
            } else if (flag[t]) {
                result.falsePositives++;
            } else {
                result.trueNegatives++;
            }

            if (actualAnomaly) {
                ModeResult.ScenarioStats stats = result.scenario(row.scenario);
                stats.samples++;
                if (flag[t]) {
                    stats.detectedSamples++;
                }
            } else if (benign) {
                result.benignSamples++;
                if (flag[t]) {
                    result.falsePositivesBenign++;
                }
            } else {
                result.normalSamples++;
                if (flag[t]) {
                    result.falsePositivesNormal++;
                }
            }

            if (falseAlarm && !previousFalseAlarm) {
                result.falseAlarmEpisodes++;
            }
            previousFalseAlarm = falseAlarm;
        }

        // Từng đoạn bất thường liên tiếp: phát hiện nếu có báo ít nhất một lần trong đoạn đó.
        int t = trainSamples;
        while (t < steps) {
            if (trace.rows.get(t).label != 1) {
                t++;
                continue;
            }
            int start = t;
            String scenario = trace.rows.get(t).scenario;
            while (t < steps && trace.rows.get(t).label == 1) {
                t++;
            }
            int end = t - 1;
            result.totalWindows++;
            result.scenario(scenario).windows++;
            for (int i = start; i <= end; i++) {
                if (flag[i]) {
                    result.detectedWindows++;
                    result.totalDelaySamples += i - start;
                    result.scenario(scenario).detectedWindows++;
                    result.scenario(scenario).totalDelaySamples += i - start;
                    break;
                }
            }
        }
    }

    /** Dùng trong test: đánh giá một dãy cờ cho một máy. */
    public static ModeResult evaluateForTest(MlMode mode, List<TraceRow> machineRows, boolean[] flag, int trainSamples) {
        MachineTrace trace = new MachineTrace(machineRows.get(0).machineId);
        trace.rows.addAll(machineRows);
        ModeResult result = new ModeResult(mode);
        evaluate(result, trace, flag, machineRows.size(), trainSamples);
        return result;
    }
}
