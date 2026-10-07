// Owner: Nguoi4

package exam.tools;

import exam.common.model.Metrics;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc/ghi file trace CSV trong data/traces/. Định dạng:
 *
 *   machineId,time,label,scenario,kbSent,kbReceived,connectionCount,distinctDestinationCount,cpuPercent,ramPercent,processCount,focusLostCount
 *
 * Dòng đầu là tiêu đề. Số dùng dấu chấm thập phân.
 */
public final class TraceCsv {

    public static final String HEADER = "machineId,time,label,scenario,kbSent,kbReceived,connectionCount,"
            + "distinctDestinationCount,cpuPercent,ramPercent,processCount,focusLostCount";

    private TraceCsv() {
    }

    /** Dòng CSV của một TraceRow (không có xuống dòng). */
    public static String toLine(TraceRow row) {
        double[] v = row.metrics.toVector();
        return row.machineId + "," + row.time + "," + row.label + "," + row.scenario + ","
                + v[0] + "," + v[1] + "," + (int) v[2] + "," + (int) v[3] + ","
                + v[4] + "," + v[5] + "," + (int) v[6] + "," + (int) v[7];
    }

    public static void write(Path file, List<TraceRow> rows) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (TraceRow row : rows) {
                writer.write(toLine(row));
                writer.newLine();
            }
        }
    }

    /** Đọc trace. Ném IOException (có số dòng) nếu dòng nào sai định dạng. */
    public static List<TraceRow> read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<TraceRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            try {
                rows.add(parseLine(lines.get(i)));
            } catch (RuntimeException e) {
                throw new IOException("Trace " + file + " dòng " + (i + 1) + " sai định dạng: " + lines.get(i), e);
            }
        }
        return rows;
    }

    static TraceRow parseLine(String line) {
        String[] f = line.split(",", -1);
        if (f.length != 12) {
            throw new IllegalArgumentException("cần 12 cột, có " + f.length);
        }
        Metrics metrics = new Metrics(
                Double.parseDouble(f[4]), Double.parseDouble(f[5]),
                (int) Double.parseDouble(f[6]), (int) Double.parseDouble(f[7]),
                Double.parseDouble(f[8]), Double.parseDouble(f[9]),
                (int) Double.parseDouble(f[10]), (int) Double.parseDouble(f[11]));
        return new TraceRow(f[0], Long.parseLong(f[1]), Integer.parseInt(f[2]), f[3], metrics);
    }
}
