// Owner: Nguoi4

package exam.tools;

import exam.common.model.MlMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chạy thực nghiệm: phát lại một bộ trace qua từng chế độ ml.mode và so sánh kết quả.
 *
 *   java -cp "source/tools/target/tools.jar:source/tools/target/lib/*" exam.tools.ExperimentRunner
 *        --trace data/traces/sim.csv[,data/traces/b.csv ...] | --trace-dir data/traces --include cpu_01,normal_01
 *        [--modes NONE,IF,CHRONOS,BOTH_OR,BOTH_AND] [--ml-url http://localhost:8000]
 *        [--train-samples 30] [--window 60] [--chronos-min-points 12]
 *        [--if-margin 0.05] [--if-consecutive 3] [--chronos-consecutive 3]
 *        [--out statics/results/experiment.csv] [--dump-scores statics/results/scores.csv]
 *        [--experiment-id E01 --log statics/results/experiment_log.csv]
 *
 * Chế độ cần Chronos (CHRONOS, BOTH_OR, BOTH_AND) cần ml-service đang chạy. Nếu ml-service không dùng được,
 * các chế độ đó được ghi rõ là SKIPPED (không im lặng bỏ qua) còn NONE và IF vẫn chạy.
 * Bảng kết quả in ra màn hình và ghi vào file CSV (cùng thư mục có thêm <out>-by-scenario.csv).
 * Với --experiment-id, mỗi lần chạy được NỐI THÊM vào file log để mọi cấu hình thử đều được lưu lại, kể cả kết quả xấu.
 */
public class ExperimentRunner {

    private ExperimentRunner() {
    }

    public static void main(String[] args) throws IOException {
        Args options = new Args(args);
        Path out = Path.of(options.get("out", "statics/results/experiment.csv"));
        List<MlMode> modes = parseModes(options.get("modes", "NONE,IF,CHRONOS,BOTH_OR,BOTH_AND"));

        ExperimentEngine.Config config = new ExperimentEngine.Config();
        config.trainSamples = options.getInt("train-samples", config.trainSamples);
        config.window = options.getInt("window", config.window);
        config.chronosMinPoints = options.getInt("chronos-min-points", config.chronosMinPoints);
        config.ifThresholdMargin = options.getDouble("if-margin", config.ifThresholdMargin);
        config.ifConsecutive = options.getInt("if-consecutive", config.ifConsecutive);
        config.chronosConsecutive = options.getInt("chronos-consecutive", config.chronosConsecutive);

        List<Path> traceFiles = resolveTraceFiles(options);
        List<TraceRow> rows = new ArrayList<>();
        for (Path file : traceFiles) {
            List<TraceRow> traceRows = TraceCsv.read(file);
            System.out.println("[Experiment] Trace " + file + ": " + traceRows.size() + " dòng");
            rows.addAll(traceRows);
        }

        ChronosService chronos = new HttpChronosService(options.get("ml-url", "http://localhost:8000"));
        ExperimentEngine engine = new ExperimentEngine();
        List<ModeResult> results = engine.run(rows, modes, chronos, config);

        System.out.println(formatTable(results));
        System.out.print(formatScenarioTable(results));
        writeCsv(out, results);
        writeScenarioCsv(scenarioPath(out), results);
        System.out.println("[Experiment] Đã ghi " + out);

        if (options.has("dump-scores")) {
            Path dump = Path.of(options.require("dump-scores"));
            ScoreDump.write(dump, engine.getDetails());
            System.out.println("[Experiment] Đã ghi điểm từng mẫu " + dump);
        }
        if (options.has("experiment-id")) {
            appendToLog(Path.of(options.get("log", "statics/results/experiment_log.csv")),
                    options.require("experiment-id"), traceFiles, config, results);
        }
    }

    /** --trace a.csv,b.csv hoặc --trace-dir thư-mục [--include tên1,tên2] (tên không có đuôi .csv). */
    static List<Path> resolveTraceFiles(Args options) throws IOException {
        List<Path> files = new ArrayList<>();
        if (options.has("trace")) {
            for (String part : options.require("trace").split(",")) {
                files.add(Path.of(part.trim()));
            }
        } else if (options.has("trace-dir")) {
            Path dir = Path.of(options.require("trace-dir"));
            for (String name : options.require("include").split(",")) {
                Path file = dir.resolve(name.trim() + ".csv");
                if (!Files.exists(file)) {
                    throw new IOException("Không có trace " + file);
                }
                files.add(file);
            }
        } else {
            throw new IllegalArgumentException("Thiếu --trace hoặc --trace-dir/--include");
        }
        return files;
    }

    static List<MlMode> parseModes(String text) {
        List<MlMode> modes = new ArrayList<>();
        for (String part : text.split(",")) {
            try {
                modes.add(MlMode.valueOf(part.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("ml.mode không hợp lệ: " + part + " (chọn NONE, IF, CHRONOS, BOTH_OR, BOTH_AND)", e);
            }
        }
        return modes;
    }

    /** Bảng dễ đọc để in ra màn hình. */
    static String formatTable(List<ModeResult> results) {
        StringBuilder text = new StringBuilder();
        text.append(String.format("%-9s %9s %7s %7s %7s %5s %8s %9s %10s %9s%n",
                "mode", "precision", "recall", "F1", "FPR", "FP", "episodes", "windows", "delay(mau)", "time(ms)"));
        for (ModeResult result : results) {
            if (result.skippedReason != null) {
                text.append(String.format("%-9s SKIPPED (%s)%n", result.mode, result.skippedReason));
                continue;
            }
            text.append(String.format(Locale.ROOT, "%-9s %9.3f %7.3f %7.3f %7.3f %5d %8d %4d/%-4d %10s %9d%n",
                    result.mode, result.precision(), result.recall(), result.f1(), result.falsePositiveRate(),
                    result.falsePositives, result.falseAlarmEpisodes,
                    result.detectedWindows, result.totalWindows, formatDelay(result), result.elapsedMillis));
        }
        return text.toString();
    }

    static String formatScenarioTable(List<ModeResult> results) {
        StringBuilder text = new StringBuilder("\nTheo kịch bản (mẫu phát hiện / mẫu, đoạn phát hiện / đoạn):\n");
        for (ModeResult result : results) {
            if (result.skippedReason != null || result.byScenario.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, ModeResult.ScenarioStats> entry : result.byScenario.entrySet()) {
                ModeResult.ScenarioStats stats = entry.getValue();
                text.append(String.format(Locale.ROOT, "  %-9s %-18s mẫu %3d/%-3d  đoạn %2d/%-2d%n",
                        result.mode, entry.getKey(), stats.detectedSamples, stats.samples, stats.detectedWindows, stats.windows));
            }
        }
        return text.toString();
    }

    private static String formatDelay(ModeResult result) {
        return Double.isNaN(result.averageDelaySamples()) ? "-" : String.format(Locale.ROOT, "%.1f", result.averageDelaySamples());
    }

    static final String RESULT_HEADER = "mode,precision,recall,f1,fpr,tp,fp,fn,tn,falseAlarmEpisodes,fpNormal,normalSamples,fpBenign,benignSamples,"
            + "windowsDetected,windowsTotal,avgDelaySamples,timeMs,skipped";

    private static String resultLine(ModeResult r) {
        return String.format(Locale.ROOT, "%s,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%d,%s",
                r.mode, r.precision(), r.recall(), r.f1(), r.falsePositiveRate(),
                r.truePositives, r.falsePositives, r.falseNegatives, r.trueNegatives,
                r.falseAlarmEpisodes, r.falsePositivesNormal, r.normalSamples, r.falsePositivesBenign, r.benignSamples,
                r.detectedWindows, r.totalWindows,
                Double.isNaN(r.averageDelaySamples()) ? "" : String.format(Locale.ROOT, "%.2f", r.averageDelaySamples()),
                r.elapsedMillis, r.skippedReason == null ? "" : r.skippedReason.replace(',', ';'));
    }

    static void writeCsv(Path out, List<ModeResult> results) throws IOException {
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder csv = new StringBuilder(RESULT_HEADER).append('\n');
        for (ModeResult r : results) {
            csv.append(resultLine(r)).append('\n');
        }
        Files.writeString(out, csv.toString(), StandardCharsets.UTF_8);
    }

    static Path scenarioPath(Path out) {
        String name = out.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return out.resolveSibling(base + "-by-scenario.csv");
    }

    static void writeScenarioCsv(Path out, List<ModeResult> results) throws IOException {
        StringBuilder csv = new StringBuilder("mode,scenario,samples,detectedSamples,windows,detectedWindows,avgDelaySamples\n");
        for (ModeResult r : results) {
            for (Map.Entry<String, ModeResult.ScenarioStats> entry : r.byScenario.entrySet()) {
                ModeResult.ScenarioStats s = entry.getValue();
                csv.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%s%n", r.mode, entry.getKey(), s.samples, s.detectedSamples,
                        s.windows, s.detectedWindows,
                        s.detectedWindows == 0 ? "" : String.format(Locale.ROOT, "%.2f", (double) s.totalDelaySamples / s.detectedWindows)));
            }
        }
        Files.writeString(out, csv.toString(), StandardCharsets.UTF_8);
    }

    /** Nối kết quả vào file log chung: mỗi dòng = (thí nghiệm, chế độ). Không bao giờ ghi đè dòng cũ. */
    static void appendToLog(Path log, String experimentId, List<Path> traces, ExperimentEngine.Config config,
                            List<ModeResult> results) throws IOException {
        Path parent = log.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        boolean isNew = !Files.exists(log);
        StringBuilder csv = new StringBuilder();
        if (isNew) {
            csv.append("timestamp,experimentId,traces,trainSamples,window,chronosMinPoints,ifMargin,ifConsecutive,chronosConsecutive,")
                    .append(RESULT_HEADER).append('\n');
        }
        StringBuilder names = new StringBuilder();
        for (Path trace : traces) {
            String name = trace.getFileName().toString();
            names.append(names.length() == 0 ? "" : "|").append(name.endsWith(".csv") ? name.substring(0, name.length() - 4) : name);
        }
        for (ModeResult r : results) {
            csv.append(Instant.now()).append(',').append(experimentId).append(',').append(names).append(',')
                    .append(config.trainSamples).append(',').append(config.window).append(',').append(config.chronosMinPoints).append(',')
                    .append(config.ifThresholdMargin).append(',').append(config.ifConsecutive).append(',').append(config.chronosConsecutive).append(',')
                    .append(resultLine(r)).append('\n');
        }
        Files.writeString(log, csv.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("[Experiment] Đã nối vào log " + log + " (thí nghiệm " + experimentId + ")");
    }
}
