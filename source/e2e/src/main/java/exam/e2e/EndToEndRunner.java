// Owner: Nguoi1

package exam.e2e;

import exam.client.api.AnomalyScorer;
import exam.client.api.MetricsSource;
import exam.client.ml.IsolationForestScorer;
import exam.client.monitor.OshiMetricsSource;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Chạy kịch bản end-to-end với TIẾN TRÌNH THẬT:
 *
 *   ml-service (Python, uvicorn)  <--HTTP--  Server (Java, SQLite)  <--TCP--  Teacher, Student (client thật, OSHI thật)
 *
 * Runner tự khởi động ml-service và Server (mỗi cái một process riêng, log ghi vào --work-dir), chạy ExamScenario,
 * rồi tắt hết. Mã thoát 0 nếu mọi bước đạt, 1 nếu có bước thất bại.
 *
 *   cd <thư mục gốc repo>  (sau khi mvn package)
 *   java -cp "source/e2e/target/e2e.jar:source/e2e/target/lib/*" exam.e2e.EndToEndRunner
 *        [--python ml-service/.venv/bin/python] [--backend naive|chronos] [--work-dir /tmp/ltm-e2e]
 *
 * --backend naive   : ml-service chạy dự báo đơn giản (dev/test), dùng được khi không tải được model Chronos.
 * --backend chronos : ml-service nạp Chronos-Bolt thật (cần Internet lần đầu hoặc LTM_ML_MODEL trỏ tới model local).
 *                     Nếu model không nạp được, /score trả 503 và Server tự bỏ qua ML (kịch bản vẫn phải đạt).
 *
 * Cần Linux hoặc macOS: gây vi phạm bằng cách chạy một process tên "anydesk", tạm dừng ml-service bằng "kill -STOP".
 */
public class EndToEndRunner {

    private final List<Process> startedProcesses = new ArrayList<>();
    private Process mlProcess;
    private Path mlLog;
    private Path workDirectory;

    public static void main(String[] args) throws Exception {
        int exitCode = new EndToEndRunner().run(new RunnerArgs(args));
        System.exit(exitCode);
    }

    private int run(RunnerArgs args) throws Exception {
        workDirectory = args.workDirectory;
        Files.createDirectories(workDirectory.resolve("config"));
        Files.createDirectories(workDirectory.resolve("bin"));

        int mlPort = freePort();
        int serverPort = freePort();
        System.out.println("[Runner] Thư mục làm việc: " + workDirectory);
        System.out.println("[Runner] ml-service cổng " + mlPort + " (backend " + args.backend + "), Server cổng " + serverPort);

        writeConfigs(serverPort, mlPort);
        try {
            startMlService(args, mlPort);
            startServer(args, serverPort);

            StepReport report = new ExamScenario(new RealEnvironment(serverPort, mlPort)).run();

            System.out.println();
            System.out.println("[Runner] ml-service /model: " + httpGet("http://localhost:" + mlPort + "/model"));
            System.out.println("[Runner] " + report.summary());
            if (!report.allPassed()) {
                System.out.println("[Runner] Xem log: " + workDirectory.resolve("server.log") + " và " + mlLog);
            }
            return report.allPassed() ? 0 : 1;
        } finally {
            stopEverything();
        }
    }

    // ------------------------------------------------------------------
    // Khởi động tiến trình
    // ------------------------------------------------------------------

    private void writeConfigs(int serverPort, int mlPort) throws IOException {
        Files.writeString(workDirectory.resolve("config/server.properties"),
                "server.port = " + serverPort + "\n"
                        + "db.path = data/exam.db\n"
                        + "heartbeat.timeout.seconds = 4\n"
                        + "monitor.interval.seconds = 1\n"
                        + "time.sync.interval.seconds = 2\n"
                        + "room.min.machines = 3\n", StandardCharsets.UTF_8);
        Files.writeString(workDirectory.resolve("config/ml.properties"),
                "ml.mode = BOTH_OR\n"
                        + "ml.service.url = http://localhost:" + mlPort + "\n"
                        + "ml.service.timeout.ms = 1000\n"
                        + "ml.client.training.seconds = 5\n"
                        + "ml.chronos.min.points = 4\n", StandardCharsets.UTF_8);
        // Mỗi lần chạy dùng database mới để kết quả không phụ thuộc lần chạy trước
        Files.deleteIfExists(workDirectory.resolve("data/exam.db"));
    }

    private void startMlService(RunnerArgs args, int mlPort) throws Exception {
        mlLog = workDirectory.resolve("ml-service.log");
        ProcessBuilder builder = new ProcessBuilder(args.python, "-m", "uvicorn", "app:app", "--port", String.valueOf(mlPort))
                .directory(args.mlDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(mlLog.toFile());
        builder.environment().put("LTM_ML_BACKEND", args.backend);
        builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");
        mlProcess = builder.start();
        startedProcesses.add(mlProcess);

        waitUntil("ml-service /health", 90_000, () -> httpGet("http://localhost:" + mlPort + "/health").contains("\"ok\""));
        System.out.println("[Runner] ml-service sẵn sàng: " + httpGet("http://localhost:" + mlPort + "/health"));
    }

    private void startServer(RunnerArgs args, int serverPort) throws Exception {
        Path serverLog = workDirectory.resolve("server.log");
        ProcessBuilder builder = new ProcessBuilder(args.javaCommand, "-Dstdout.encoding=UTF-8", "-cp", args.serverClasspath,
                "exam.server.ServerMain")
                .directory(workDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(serverLog.toFile());
        Process server = builder.start();
        startedProcesses.add(server);

        waitUntil("Server mở cổng " + serverPort, 60_000, () -> {
            try (java.net.Socket socket = new java.net.Socket("localhost", serverPort)) {
                return socket.isConnected();
            } catch (IOException e) {
                return false;
            }
        });
        System.out.println("[Runner] Server sẵn sàng");
    }

    private void stopEverything() {
        for (Process process : startedProcesses) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        for (Process process : startedProcesses) {
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ------------------------------------------------------------------
    // Hàm phụ
    // ------------------------------------------------------------------

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private interface Check {
        boolean ok() throws Exception;
    }

    private void waitUntil(String what, long timeoutMillis, Check check) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (check.ok()) {
                    return;
                }
            } catch (Exception e) {
                // chưa sẵn sàng: thử lại
            }
            Thread.sleep(300);
        }
        throw new IllegalStateException("Quá " + timeoutMillis + " ms chờ: " + what);
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(5000);
        try {
            return new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    // ------------------------------------------------------------------
    // Môi trường thật cho kịch bản
    // ------------------------------------------------------------------

    private class RealEnvironment implements ExamScenario.Environment {
        private final int serverPort;
        private final int mlPort;
        private Process fakeCheatingProcess;

        RealEnvironment(int serverPort, int mlPort) {
            this.serverPort = serverPort;
            this.mlPort = mlPort;
        }

        public String serverHost() {
            return "localhost";
        }

        public int serverPort() {
            return serverPort;
        }

        public int heartbeatIntervalMs() {
            return 1000;
        }

        public MetricsSource metricsSourceFor(String studentCode) {
            return new OshiMetricsSource();
        }

        public AnomalyScorer scorerFor(String studentCode) {
            return new IsolationForestScorer();
        }

        /** Chạy một process THẬT tên "anydesk" (bản sao của lệnh sleep): OSHI thấy nó và luật 1 báo PROCESS_DENYLIST. */
        public String triggerViolation(String studentCode) throws Exception {
            if (fakeCheatingProcess == null) {
                Path sleepBinary = findExecutable("sleep");
                Path copy = workDirectory.resolve("bin/anydesk");
                Files.copy(sleepBinary, copy, StandardCopyOption.REPLACE_EXISTING);
                if (!copy.toFile().setExecutable(true)) {
                    throw new IOException("Không đặt được quyền chạy cho " + copy);
                }
                fakeCheatingProcess = new ProcessBuilder(copy.toString(), "600").start();
                startedProcesses.add(fakeCheatingProcess);
            }
            return "anydesk";
        }

        public boolean canForceIsolationForestAnomaly() {
            return false;
        }

        public void forceIsolationForestAnomaly(String studentCode) {
            throw new UnsupportedOperationException("OSHI thật không ép được lưu lượng bất thường");
        }

        /** Tạm dừng ml-service bằng SIGSTOP: nó nhận kết nối nhưng không trả lời, Server phải timeout. */
        public void makeMlServiceSlow() throws Exception {
            signalMlService("STOP");
        }

        public void restoreMlService() throws Exception {
            signalMlService("CONT");
        }

        private void signalMlService(String signal) throws Exception {
            Process kill = new ProcessBuilder("kill", "-" + signal, String.valueOf(mlProcess.pid())).inheritIO().start();
            if (kill.waitFor() != 0) {
                throw new IOException("kill -" + signal + " thất bại");
            }
        }

        /** Đếm các dòng access log "POST /score" của uvicorn. */
        public int mlRequestCount() throws Exception {
            int count = 0;
            for (String line : Files.readAllLines(mlLog, StandardCharsets.UTF_8)) {
                if (line.contains("\"POST /score")) {
                    count++;
                }
            }
            return count;
        }

        public int heartbeatTimeoutSeconds() {
            return 4;
        }

        private Path findExecutable(String name) throws IOException {
            for (String directory : System.getenv("PATH").split(File.pathSeparator)) {
                Path candidate = Path.of(directory, name);
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
            throw new IOException("Không tìm thấy lệnh " + name + " (runner cần Linux hoặc macOS)");
        }
    }
}
