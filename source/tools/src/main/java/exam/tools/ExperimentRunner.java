// Owner: Nguoi4

package exam.tools;

import exam.common.model.MlMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chạy thực nghiệm: phát lại một bộ trace qua từng chế độ ml.mode và so sánh kết quả.
 *
 *   java -cp "source/tools/target/tools.jar:source/tools/target/lib/*" exam.tools.ExperimentRunner
 *        --trace data/traces/sim.csv [--modes NONE,IF,CHRONOS,BOTH_OR,BOTH_AND]
 *        [--ml-url http://localhost:8000] [--train-samples 30] [--window 60]
 *        [--out statics/results/experiment.csv]
 *
 * Chế độ cần Chronos (CHRONOS, BOTH_OR, BOTH_AND) cần ml-service đang chạy. Nếu ml-service không dùng được,
 * các chế độ đó được ghi rõ là SKIPPED (không im lặng bỏ qua) còn NONE và IF vẫn chạy.
 * Bảng kết quả in ra màn hình và ghi vào file CSV (mặc định statics/results/experiment.csv).
 */
public class ExperimentRunner {

    private ExperimentRunner() {
    }

    public static void main(String[] args) throws IOException {
        Args options = new Args(args);
        Path trace = Path.of(options.require("trace"));
        Path out = Path.of(options.get("out", "statics/results/experiment.csv"));
        List<MlMode> modes = parseModes(options.get("modes", "NONE,IF,CHRONOS,BOTH_OR,BOTH_AND"));

        ExperimentEngine.Config config = new ExperimentEngine.Config();
        config.trainSamples = options.getInt("train-samples", config.trainSamples);
        config.window = options.getInt("window", config.window);
        config.chronosMinPoints = options.getInt("chronos-min-points", config.chronosMinPoints);

        List<TraceRow> rows = TraceCsv.read(trace);
        System.out.println("[Experiment] Trace " + trace + ": " + rows.size() + " dòng");

        ChronosService chronos = new HttpChronosService(options.get("ml-url", "http://localhost:8000"));
        List<ModeResult> results = new ExperimentEngine().run(rows, modes, chronos, config);

        String table = formatTable(results);
        System.out.println(table);
        writeCsv(out, results);
        System.out.println("[Experiment] Đã ghi " + out);
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
        text.append(String.format("%-9s %9s %7s %7s %7s %9s %10s %9s%n",
                "mode", "precision", "recall", "F1", "FPR", "windows", "delay(mau)", "time(ms)"));
        for (ModeResult result : results) {
            if (result.skippedReason != null) {
                text.append(String.format("%-9s SKIPPED (%s)%n", result.mode, result.skippedReason));
                continue;
            }
            text.append(String.format(Locale.ROOT, "%-9s %9.3f %7.3f %7.3f %7.3f %4d/%-4d %10s %9d%n",
                    result.mode, result.precision(), result.recall(), result.f1(), result.falsePositiveRate(),
                    result.detectedWindows, result.totalWindows, formatDelay(result), result.elapsedMillis));
        }
        return text.toString();
    }

    private static String formatDelay(ModeResult result) {
        return Double.isNaN(result.averageDelaySamples()) ? "-" : String.format(Locale.ROOT, "%.1f", result.averageDelaySamples());
    }

    static void writeCsv(Path out, List<ModeResult> results) throws IOException {
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder csv = new StringBuilder("mode,precision,recall,f1,fpr,tp,fp,fn,tn,windowsDetected,windowsTotal,avgDelaySamples,timeMs,skipped\n");
        for (ModeResult r : results) {
            csv.append(String.format(Locale.ROOT, "%s,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%d,%d,%d,%s,%d,%s%n",
                    r.mode, r.precision(), r.recall(), r.f1(), r.falsePositiveRate(),
                    r.truePositives, r.falsePositives, r.falseNegatives, r.trueNegatives,
                    r.detectedWindows, r.totalWindows,
                    Double.isNaN(r.averageDelaySamples()) ? "" : String.format(Locale.ROOT, "%.2f", r.averageDelaySamples()),
                    r.elapsedMillis, r.skippedReason == null ? "" : r.skippedReason.replace(',', ';')));
        }
        Files.writeString(out, csv.toString(), StandardCharsets.UTF_8);
    }
}
