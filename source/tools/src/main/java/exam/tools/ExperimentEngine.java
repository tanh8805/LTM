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
 * Cách phát lại: với mỗi bước thời gian t (đồng bộ giữa các máy):
 *   Isolation Forest : mỗi máy có một AnomalyDetector riêng (học `trainSamples` mẫu đầu rồi chấm từng mẫu) -
 *                      đúng quy trình client thật.
 *   Chronos          : tại mỗi bước, gom 60 điểm gần nhất (trước bước t) của MỌI máy thành MỘT batch gửi ml-service,
 *                      so giá trị thật ở bước t với khoảng q0.1-q0.9 (ChronosJudge, 3 lần liên tiếp) - đúng quy trình Server thật.
 *   Ghép theo ml.mode: MlDecision (NONE, IF, CHRONOS, BOTH_OR, BOTH_AND).
 * Chỉ các mẫu SAU giai đoạn học (t >= trainSamples) được đánh giá, để mọi chế độ so sánh công bằng.
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

    /** Dữ liệu của một máy theo thứ tự thời gian. */
    private static class MachineTrace {
        final String machineId;
        final List<TraceRow> rows = new ArrayList<>();

        MachineTrace(String machineId) {
            this.machineId = machineId;
        }
    }

    public List<ModeResult> run(List<TraceRow> rows, List<MlMode> modes, ChronosService chronos, Config config) {
        Map<String, MachineTrace> traces = groupByMachine(rows);
        int steps = shortestLength(traces);
        if (steps <= config.trainSamples) {
            throw new IllegalArgumentException("Trace chỉ có " + steps + " mẫu mỗi máy, cần nhiều hơn " + config.trainSamples + " (giai đoạn học)");
        }

        boolean needIf = false;
        boolean needChronos = false;
        for (MlMode mode : modes) {
            needIf |= MlDecision.usesIsolationForest(mode);
            needChronos |= MlDecision.usesChronos(mode);
        }

        long ifStart = System.currentTimeMillis();
        Map<String, boolean[]> ifFlags = needIf ? computeIsolationForestFlags(traces, steps, config) : new HashMap<>();
        long ifMillis = System.currentTimeMillis() - ifStart;

        Map<String, boolean[]> chronosFlags = new HashMap<>();
        String chronosProblem = null;
        long chronosMillis = 0;
        if (needChronos) {
            long chronosStart = System.currentTimeMillis();
            try {
                if (chronos == null) {
                    throw new IOException("không có ml-service");
                }
                chronosFlags = computeChronosFlags(traces, steps, chronos, config);
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
                boolean[] ifFlag = ifFlags.getOrDefault(trace.machineId, new boolean[steps]);
                boolean[] chronosFlag = chronosFlags.getOrDefault(trace.machineId, new boolean[steps]);
                boolean[] flag = new boolean[steps];
                for (int t = 0; t < steps; t++) {
                    flag[t] = MlDecision.isSuspicious(mode, ifFlag[t], chronosFlag[t]);
                }
                evaluate(result, trace, flag, steps, config.trainSamples);
            }
            result.elapsedMillis = (MlDecision.usesIsolationForest(mode) ? ifMillis : 0)
                    + (MlDecision.usesChronos(mode) ? chronosMillis : 0);
            results.add(result);
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

    private int shortestLength(Map<String, MachineTrace> traces) {
        int shortest = Integer.MAX_VALUE;
        for (MachineTrace trace : traces.values()) {
            shortest = Math.min(shortest, trace.rows.size());
        }
        return traces.isEmpty() ? 0 : shortest;
    }

    private Map<String, boolean[]> computeIsolationForestFlags(Map<String, MachineTrace> traces, int steps, Config config) {
        Map<String, boolean[]> flags = new HashMap<>();
        for (MachineTrace trace : traces.values()) {
            AnomalyDetector detector = new AnomalyDetector(new IsolationForestScorer(),
                    config.trainSamples, config.ifThresholdMargin, config.ifConsecutive);
            boolean[] machineFlags = new boolean[steps];
            for (int t = 0; t < steps; t++) {
                machineFlags[t] = detector.observe(trace.rows.get(t).metrics.toVector()).anomalous;
            }
            flags.put(trace.machineId, machineFlags);
        }
        return flags;
    }

    /** Mỗi bước một batch cho mọi máy, giống Server thật. Ném IOException nếu ml-service lỗi. */
    private Map<String, boolean[]> computeChronosFlags(Map<String, MachineTrace> traces, int steps,
                                                      ChronosService chronos, Config config) throws IOException {
        ChronosJudge judge = new ChronosJudge(config.chronosConsecutive);
        Map<String, boolean[]> flags = new HashMap<>();
        for (String machineId : traces.keySet()) {
            flags.put(machineId, new boolean[steps]);
        }

        for (int t = config.chronosMinPoints; t < steps; t++) {
            Map<String, Map<String, List<Double>>> batch = new LinkedHashMap<>();
            Map<String, Map<String, Double>> actualValues = new HashMap<>();
            for (MachineTrace trace : traces.values()) {
                Map<String, List<Double>> context = new LinkedHashMap<>();
                Map<String, Double> actual = new HashMap<>();
                int from = Math.max(0, t - config.window);
                for (int metric = 0; metric < Metrics.CHRONOS_DIMENSIONS; metric++) {
                    List<Double> values = new ArrayList<>();
                    for (int i = from; i < t; i++) {
                        values.add(trace.rows.get(i).metrics.toVector()[metric]);
                    }
                    context.put(Metrics.VECTOR_NAMES[metric], values);
                    actual.put(Metrics.VECTOR_NAMES[metric], trace.rows.get(t).metrics.toVector()[metric]);
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
                flags.get(entry.getKey())[t] = verdict.suspicious;
            }
        }
        return flags;
    }

    /** Cộng dồn TP/FP/FN/TN và số đoạn gian lận được phát hiện, chỉ xét t >= trainSamples. */
    static void evaluate(ModeResult result, MachineTrace trace, boolean[] flag, int steps, int trainSamples) {
        for (int t = trainSamples; t < steps; t++) {
            boolean actualAnomaly = trace.rows.get(t).label == 1;
            if (actualAnomaly && flag[t]) {
                result.truePositives++;
            } else if (actualAnomaly) {
                result.falseNegatives++;
            } else if (flag[t]) {
                result.falsePositives++;
            } else {
                result.trueNegatives++;
            }
        }

        // Từng đoạn gian lận liên tiếp: phát hiện nếu có báo ít nhất một lần trong đoạn đó.
        int t = trainSamples;
        while (t < steps) {
            if (trace.rows.get(t).label != 1) {
                t++;
                continue;
            }
            int start = t;
            while (t < steps && trace.rows.get(t).label == 1) {
                t++;
            }
            int end = t - 1;
            result.totalWindows++;
            for (int i = start; i <= end; i++) {
                if (flag[i]) {
                    result.detectedWindows++;
                    result.totalDelaySamples += i - start;
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
