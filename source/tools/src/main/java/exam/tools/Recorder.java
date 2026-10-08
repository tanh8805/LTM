// Owner: Nguoi4

package exam.tools;

import exam.client.TeacherClient;
import exam.client.monitor.OshiMetricsSource;
import exam.client.net.TcpServerLink;
import exam.common.model.Metrics;
import exam.common.protocol.ResponseMessage;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ghi lại số liệu giám sát ra file trace CSV trong data/traces/ để dùng cho thực nghiệm (xem TraceCsv).
 *
 * Chế độ 1 - đo máy này bằng OSHI:
 *   java -cp "source/tools/target/tools.jar:source/tools/target/lib/*" exam.tools.Recorder
 *        --out data/traces/cpu_01.csv [--session cpu_01] [--machine LOCAL] [--seconds 600] [--interval 10]
 *        [--scenario mo-ta-ngan] [--focus-probe]
 *   Mỗi --interval giây đo một vector 8 chiều, ghi tới khi đủ --seconds (ghi từng dòng, Ctrl+C vẫn giữ được dữ liệu).
 *   File CSV (định dạng v2) chỉ có số liệu + sessionId/machineId/time/seq, KHÔNG có nhãn. Cạnh nó có <tên>.meta.json
 *   (thông tin phiên ghi). Ground truth nằm ở <tên>.events.json do script scripts/dataset/collect_trace.py ghi (xem TraceEvents).
 *   --focus-probe: mở hai cửa sổ Swing thật (cần DISPLAY, ví dụ Xvfb) để đo focusLostCount; nhận lệnh FOCUS_LOSS qua stdin.
 *
 * Chế độ 2 - lấy số liệu BASELINE mà Server đã lưu (giáo viên bật BASELINE, mọi máy gửi mỗi 1 giây):
 *   ... exam.tools.Recorder --from-server localhost 5000 gv01 teacher123 --out data/traces/baseline.csv
 *        [--machine SV001] [--rate-mode BASELINE]
 */
public class Recorder {

    private Recorder() {
    }

    public static void main(String[] args) throws Exception {
        // "--from-server host port user pass" có 4 giá trị liền nhau nên xử lý riêng trước khi đọc các --tùy-chọn khác.
        List<String> remaining = new ArrayList<>();
        String[] serverArgs = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--from-server")) {
                if (i + 4 >= args.length) {
                    throw new IllegalArgumentException("--from-server cần: host port username password");
                }
                serverArgs = new String[] {args[i + 1], args[i + 2], args[i + 3], args[i + 4]};
                i += 4;
            } else {
                remaining.add(args[i]);
            }
        }
        Args options = new Args(remaining.toArray(new String[0]));
        Path out = Path.of(options.require("out"));

        if (serverArgs != null) {
            recordFromServer(serverArgs, out, options.get("machine", ""), options.get("rate-mode", "BASELINE"));
        } else {
            recordLocally(out, options);
        }
    }

    // ------------------------------------------------------------------
    // Chế độ 1: đo máy này
    // ------------------------------------------------------------------

    private static void recordLocally(Path out, Args options) throws Exception {
        String machineId = options.get("machine", "LOCAL");
        String sessionId = options.get("session", defaultSessionId(out));
        String scenario = options.get("scenario", "recorded");
        int seconds = options.getInt("seconds", 600);
        int intervalSeconds = options.getInt("interval", 10);
        boolean useFocusProbe = options.has("focus-probe");

        OshiMetricsSource source = new OshiMetricsSource();
        source.collectMetrics(); // lần đầu chỉ làm mốc cho KB/giây

        FocusProbe focusProbe = null;
        if (useFocusProbe) {
            focusProbe = new FocusProbe();
            focusProbe.open();
            startFocusCommandReader(focusProbe);
        }

        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        System.out.println("[Recorder] Phiên " + sessionId + ": ghi " + seconds + " giây, mỗi " + intervalSeconds
                + " giây một mẫu, vào " + out);

        long startTime = System.currentTimeMillis();
        int samples = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writer.write(TraceCsv.HEADER_V2);
            writer.newLine();
            long end = startTime + seconds * 1000L;
            long nextSampleAt = startTime + intervalSeconds * 1000L;
            while (System.currentTimeMillis() < end) {
                // Đặt lịch theo mốc tuyệt đối (không cộng dồn sai số của sleep) để các mẫu cách đều 10 giây.
                long wait = nextSampleAt - System.currentTimeMillis();
                if (wait > 0) {
                    Thread.sleep(wait);
                }
                nextSampleAt += intervalSeconds * 1000L;

                Metrics metrics = source.collectMetrics();
                if (focusProbe != null) {
                    metrics.focusLostCount = focusProbe.getLostCount();
                }
                samples++;
                writer.write(TraceCsv.toLineV2(sessionId, machineId, System.currentTimeMillis(), samples, metrics));
                writer.newLine();
                writer.flush();
            }
        }
        long endTime = System.currentTimeMillis();
        if (focusProbe != null) {
            focusProbe.close();
        }
        writeMeta(TraceEvents.metaPathFor(out), sessionId, machineId, scenario, intervalSeconds, startTime, endTime, samples, useFocusProbe);
        System.out.println("[Recorder] Xong: " + samples + " mẫu");
        System.exit(0); // cửa sổ Swing (nếu có) không giữ JVM sống
    }

    /** sessionId mặc định = tên file không có đuôi (cpu_01.csv -> cpu_01). */
    private static String defaultSessionId(Path out) {
        String name = out.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Đọc lệnh từ stdin khi chạy với --focus-probe: mỗi dòng "FOCUS_LOSS" là một lần mất focus.
     * Script thu thập gửi lệnh vào đây và ghi lại thời điểm gửi làm ground truth.
     */
    private static void startFocusCommandReader(FocusProbe focusProbe) {
        Thread.startVirtualThread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().equals("FOCUS_LOSS")) {
                        focusProbe.triggerFocusLoss();
                    }
                }
            } catch (IOException | InterruptedException | java.lang.reflect.InvocationTargetException e) {
                System.out.println("[Recorder] Dừng đọc lệnh focus: " + e);
            }
        });
    }

    /** Thông tin về phiên ghi (KHÔNG chứa nhãn). Nhãn nằm ở file events.json do script thu thập ghi. */
    private static void writeMeta(Path metaFile, String sessionId, String machineId, String scenario, int intervalSeconds,
                                  long startTime, long endTime, int samples, boolean focusProbe) throws IOException {
        JsonObject meta = new JsonObject();
        meta.addProperty("sessionId", sessionId);
        meta.addProperty("machineId", machineId);
        meta.addProperty("scenario", scenario);
        meta.addProperty("intervalSeconds", intervalSeconds);
        meta.addProperty("startTime", startTime);
        meta.addProperty("endTime", endTime);
        meta.addProperty("samples", samples);
        meta.addProperty("source", "OSHI " + oshi.SystemInfo.getCurrentPlatform());
        meta.addProperty("focusProbe", focusProbe);
        meta.addProperty("osName", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        meta.addProperty("cpuCores", Runtime.getRuntime().availableProcessors());
        meta.addProperty("java", System.getProperty("java.version"));
        meta.addProperty("totalMemoryMb", new oshi.SystemInfo().getHardware().getMemory().getTotal() / (1024 * 1024));
        Files.writeString(metaFile, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(meta), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Chế độ 2: lấy từ Server
    // ------------------------------------------------------------------

    private static void recordFromServer(String[] serverArgs, Path out, String machineId, String rateMode) throws Exception {
        TcpServerLink link = new TcpServerLink(500, 5);
        TeacherClient client = new TeacherClient(link, serverArgs[0], Integer.parseInt(serverArgs[1]));
        client.setView(new SilentTeacherView());
        try {
            client.login(serverArgs[2], serverArgs[3]);
            long deadline = System.currentTimeMillis() + 10_000;
            while (!client.isLoggedIn() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            if (!client.isLoggedIn()) {
                throw new IOException("Không đăng nhập được vào Server " + serverArgs[0] + ":" + serverArgs[1]);
            }

            JsonObject request = new JsonObject();
            request.addProperty("machineId", machineId);
            request.addProperty("rateMode", rateMode);
            ResponseMessage response = client.request("EXPORT_METRICS_CSV", request).get(60, TimeUnit.SECONDS);
            if (!response.ok) {
                throw new IOException("Server từ chối: " + response.error);
            }

            List<TraceRow> rows = convertServerCsv(response.data.get("csv").getAsString(), rateMode);
            TraceCsv.write(out, rows);
            System.out.println("[Recorder] Đã ghi " + rows.size() + " dòng (" + rateMode + ") từ Server vào " + out);
        } finally {
            client.close();
        }
    }

    /**
     * Đổi CSV Server xuất (machineId,time,kind,rateMode,kbSent,...) sang định dạng trace.
     * Chỉ lấy các dòng DETAIL: ở BASELINE mỗi máy gửi METRICS_DETAIL mỗi 1 giây.
     */
    static List<TraceRow> convertServerCsv(String serverCsv, String scenario) {
        List<TraceRow> rows = new ArrayList<>();
        String[] lines = serverCsv.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split(",", -1);
            if (f.length != 12 || !"DETAIL".equals(f[2])) {
                continue;
            }
            Metrics metrics = new Metrics(Double.parseDouble(f[4]), Double.parseDouble(f[5]),
                    Integer.parseInt(f[6]), Integer.parseInt(f[7]), Double.parseDouble(f[8]),
                    Double.parseDouble(f[9]), Integer.parseInt(f[10]), Integer.parseInt(f[11]));
            rows.add(new TraceRow(f[0], Long.parseLong(f[1]), 0, scenario.toLowerCase(), metrics));
        }
        return rows;
    }

    /** View không làm gì: Recorder chạy trong terminal, không có giao diện. */
    private static class SilentTeacherView implements exam.client.teacher.TeacherView {
        @Override
        public void showStatus(String text) {
        }

        @Override
        public void showLogin(boolean ok, String message) {
        }

        @Override
        public void showAlert(exam.common.protocol.AlertMessage alert) {
        }

        @Override
        public void showError(String text) {
            System.out.println("[Recorder] Server báo lỗi: " + text);
        }
    }
}
