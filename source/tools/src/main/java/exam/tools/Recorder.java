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
 *        --out data/traces/may-cua-toi.csv [--machine SV001] [--seconds 600] [--interval 10]
 *        [--label 0] [--scenario recorded]
 *   Mỗi --interval giây đo một vector 8 chiều, ghi tới khi đủ --seconds (ghi từng dòng, Ctrl+C vẫn giữ được dữ liệu).
 *   --label 1 --scenario ten-kich-ban dùng khi bạn CỐ Ý làm hành vi gian lận trong lúc ghi (để có dữ liệu có nhãn).
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

    private static void recordLocally(Path out, Args options) throws IOException, InterruptedException {
        String machineId = options.get("machine", "LOCAL");
        int seconds = options.getInt("seconds", 600);
        int intervalSeconds = options.getInt("interval", 10);
        int label = options.getInt("label", 0);
        String scenario = options.get("scenario", "recorded");

        OshiMetricsSource source = new OshiMetricsSource();
        source.collectMetrics(); // lần đầu chỉ làm mốc cho KB/giây

        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        System.out.println("[Recorder] Ghi " + seconds + " giây, mỗi " + intervalSeconds + " giây một mẫu, vào " + out);

        int samples = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writer.write(TraceCsv.HEADER);
            writer.newLine();
            long end = System.currentTimeMillis() + seconds * 1000L;
            while (System.currentTimeMillis() < end) {
                Thread.sleep(intervalSeconds * 1000L);
                Metrics metrics = source.collectMetrics();
                writer.write(TraceCsv.toLine(new TraceRow(machineId, System.currentTimeMillis(), label, scenario, metrics)));
                writer.newLine();
                writer.flush();
                samples++;
            }
        }
        System.out.println("[Recorder] Xong: " + samples + " mẫu");
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
