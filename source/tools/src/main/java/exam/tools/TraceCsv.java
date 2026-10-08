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
 *
 * Hai định dạng:
 *   v1 (HEADER)    : có cột label và scenario (Simulator, trace cũ).
 *   v2 (HEADER_V2) : KHÔNG có nhãn: sessionId,machineId,time,seq,<8 metric>. Nhãn nằm ở file riêng
 *                    <tên>.events.json (xem TraceEvents); read() tự đọc file đó nếu có và gán nhãn cho từng dòng.
 */
public final class TraceCsv {

    public static final String HEADER = "machineId,time,label,scenario,kbSent,kbReceived,connectionCount,"
            + "distinctDestinationCount,cpuPercent,ramPercent,processCount,focusLostCount";

    public static final String HEADER_V2 = "sessionId,machineId,time,seq,kbSent,kbReceived,connectionCount,"
            + "distinctDestinationCount,cpuPercent,ramPercent,processCount,focusLostCount";

    /** Chu kỳ lấy mẫu mặc định (ms) dùng để gán nhãn cho trace v2 nếu meta.json không ghi. */
    private static final long DEFAULT_INTERVAL_MS = 10_000L;

    private TraceCsv() {
    }

    /** Dòng CSV v2: số liệu thuần, không nhãn. */
    public static String toLineV2(String sessionId, String machineId, long time, int seq, Metrics metrics) {
        double[] v = metrics.toVector();
        return sessionId + "," + machineId + "," + time + "," + seq + ","
                + v[0] + "," + v[1] + "," + (int) v[2] + "," + (int) v[3] + ","
                + v[4] + "," + v[5] + "," + (int) v[6] + "," + (int) v[7];
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

    /** Đọc trace (v1 hoặc v2). Ném IOException (có số dòng) nếu dòng nào sai định dạng. */
    public static List<TraceRow> read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (!lines.isEmpty() && lines.get(0).startsWith("sessionId,")) {
            return readV2(file, lines);
        }
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

    private static List<TraceRow> readV2(Path file, List<String> lines) throws IOException {
        Path eventsFile = TraceEvents.pathFor(file);
        TraceEvents events = Files.exists(eventsFile) ? TraceEvents.read(eventsFile) : null;
        long intervalMs = readIntervalMs(file);

        List<TraceRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            try {
                String[] f = lines.get(i).split(",", -1);
                if (f.length != 12) {
                    throw new IllegalArgumentException("cần 12 cột, có " + f.length);
                }
                Metrics metrics = new Metrics(
                        Double.parseDouble(f[4]), Double.parseDouble(f[5]),
                        (int) Double.parseDouble(f[6]), (int) Double.parseDouble(f[7]),
                        Double.parseDouble(f[8]), Double.parseDouble(f[9]),
                        (int) Double.parseDouble(f[10]), (int) Double.parseDouble(f[11]));
                long time = Long.parseLong(f[2]);

                TraceEvents.Event event = events == null ? null : events.eventAt(time, intervalMs);
                if (event == null) {
                    rows.add(new TraceRow(f[0], time, 0, "normal", TraceRow.CATEGORY_NORMAL, metrics));
                } else {
                    boolean anomaly = TraceRow.CATEGORY_ANOMALY.equals(event.category);
                    rows.add(new TraceRow(f[0], time, anomaly ? 1 : 0, event.type, event.category, metrics));
                }
            } catch (RuntimeException e) {
                throw new IOException("Trace " + file + " dòng " + (i + 1) + " sai định dạng: " + lines.get(i), e);
            }
        }
        return rows;
    }

    /** Chu kỳ lấy mẫu ghi trong <tên>.meta.json (intervalSeconds); thiếu thì dùng 10 giây. */
    private static long readIntervalMs(Path file) throws IOException {
        Path metaFile = TraceEvents.metaPathFor(file);
        if (!Files.exists(metaFile)) {
            return DEFAULT_INTERVAL_MS;
        }
        com.google.gson.JsonObject meta = com.google.gson.JsonParser.parseString(
                Files.readString(metaFile, StandardCharsets.UTF_8)).getAsJsonObject();
        return meta.has("intervalSeconds") ? meta.get("intervalSeconds").getAsLong() * 1000L : DEFAULT_INTERVAL_MS;
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
