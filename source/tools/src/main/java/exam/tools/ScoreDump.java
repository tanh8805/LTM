// Owner: Nguoi4

package exam.tools;

import exam.common.ml.Quantiles;
import exam.common.model.Metrics;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Ghi điểm và dự báo THÔ của từng mẫu ra CSV để phân tích sâu bằng công cụ khác (scripts/dataset/analyze.py):
 * điểm Isolation Forest, threshold, dự báo q0.1/q0.5/q0.9 của Chronos cho 6 metric, bộ đếm liên tiếp.
 * Các cột label/category/scenario chỉ để đối chiếu khi phân tích, không phải đầu vào của mô hình.
 */
public final class ScoreDump {

    private ScoreDump() {
    }

    public static String header() {
        StringBuilder text = new StringBuilder("trace,t,time,label,category,scenario");
        for (String name : Metrics.VECTOR_NAMES) {
            text.append(',').append(name);
        }
        text.append(",ifTraining,ifScore,ifThreshold,ifConsecutive,ifFlag");
        for (int i = 0; i < Metrics.CHRONOS_DIMENSIONS; i++) {
            String name = Metrics.VECTOR_NAMES[i];
            text.append(',').append(name).append("_q10,").append(name).append("_q50,").append(name).append("_q90");
        }
        text.append(",chronosWorstCount,chronosFlag");
        return text.toString();
    }

    public static void write(Path file, List<ExperimentEngine.SampleDetail> details) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(header());
            writer.newLine();
            for (ExperimentEngine.SampleDetail d : details) {
                StringBuilder line = new StringBuilder();
                line.append(d.traceId).append(',').append(d.t).append(',').append(d.time).append(',')
                        .append(d.label).append(',').append(d.category).append(',').append(d.scenario);
                for (double value : d.vector) {
                    line.append(',').append(number(value));
                }
                line.append(',').append(d.ifTraining ? 1 : 0).append(',').append(number(d.ifScore)).append(',')
                        .append(number(d.ifThreshold)).append(',').append(d.ifConsecutive).append(',').append(d.ifFlag ? 1 : 0);
                for (int i = 0; i < Metrics.CHRONOS_DIMENSIONS; i++) {
                    Quantiles q = d.chronos == null ? null : d.chronos.get(Metrics.VECTOR_NAMES[i]);
                    if (q == null) {
                        line.append(",,,");
                    } else {
                        line.append(',').append(number(q.q10)).append(',').append(number(q.q50)).append(',').append(number(q.q90));
                    }
                }
                line.append(',').append(d.chronosWorstCount).append(',').append(d.chronosFlag ? 1 : 0);
                writer.write(line.toString());
                writer.newLine();
            }
        }
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
