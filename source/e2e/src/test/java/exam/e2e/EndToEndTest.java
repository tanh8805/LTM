// Owner: Nguoi1

package exam.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import exam.client.api.AnomalyScorer;
import exam.client.api.MetricsSource;
import exam.server.ServerApp;
import exam.server.ServerConfig;
import exam.server.ml.MlConfig;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end trong một JVM: Server THẬT (TCP + SQLite) + Teacher/Student THẬT (TcpServerLink, StudentClient, TeacherClient,
 * MonitoringLoop, RuleEngine, Isolation Forest) + một ml-service GIẢ bằng HttpServer của JDK (để điều khiển được timeout).
 * Kịch bản 20 bước nằm trong ExamScenario; cùng kịch bản đó cũng chạy với ml-service Python thật trong EndToEndRunner.
 */
class EndToEndTest {

    @TempDir
    Path tempDirectory;

    @Test
    @Timeout(300)
    void twentyStepScenarioPassesAgainstARealServer() throws Exception {
        FakeMlService mlService = new FakeMlService();
        ServerApp server = startServer(mlService.getPort());
        try {
            ExamScenario.Environment environment = new TestEnvironment(server.getPort(), mlService);

            StepReport report = new ExamScenario(environment).run();

            System.out.println("== " + report.summary());
            assertTrue(report.allPassed(), report.summary());
            assertTrue(report.getTotal() >= 40, "kịch bản phải có đủ các bước kiểm tra, chỉ có " + report.getTotal());
        } finally {
            server.stop();
            mlService.stop();
        }
    }

    private ServerApp startServer(int mlPort) throws Exception {
        Properties serverProperties = new Properties();
        serverProperties.setProperty("server.port", "0");
        serverProperties.setProperty("db.path", tempDirectory.resolve("e2e.db").toString());
        serverProperties.setProperty("heartbeat.timeout.seconds", "4");
        serverProperties.setProperty("monitor.interval.seconds", "1");
        serverProperties.setProperty("time.sync.interval.seconds", "2");
        serverProperties.setProperty("room.min.machines", "3");

        Properties mlProperties = new Properties();
        mlProperties.setProperty("ml.mode", "BOTH_OR");
        mlProperties.setProperty("ml.service.url", "http://localhost:" + mlPort);
        mlProperties.setProperty("ml.service.timeout.ms", "1000");
        mlProperties.setProperty("ml.client.training.seconds", "5");
        mlProperties.setProperty("ml.chronos.min.points", "4");

        return ServerApp.start(ServerConfig.fromProperties(serverProperties), MlConfig.fromProperties(mlProperties));
    }

    // ------------------------------------------------------------------

    /** Môi trường giả: nguồn số liệu điều khiển được, ml-service giả. */
    private static class TestEnvironment implements ExamScenario.Environment {
        private final int serverPort;
        private final FakeMlService mlService;
        private final Map<String, ControllableMetricsSource> sources = new ConcurrentHashMap<>();

        TestEnvironment(int serverPort, FakeMlService mlService) {
            this.serverPort = serverPort;
            this.mlService = mlService;
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
            return sources.computeIfAbsent(studentCode, code -> new ControllableMetricsSource());
        }

        /** Scorer theo kịch bản: số liệu tăng vọt (KB gửi > 1000) -> điểm 0.95, bình thường -> 0.4. */
        public AnomalyScorer scorerFor(String studentCode) {
            return new AnomalyScorer() {
                public void fit(java.util.List<double[]> samples) {
                }

                public double score(double[] sample) {
                    return sample[0] > 1000 ? 0.95 : 0.4;
                }
            };
        }

        public String triggerViolation(String studentCode) {
            sources.get(studentCode).usbDevices.add("SanDisk Cruzer [e2e]");
            return "SanDisk Cruzer";
        }

        public boolean canForceIsolationForestAnomaly() {
            return true;
        }

        public void forceIsolationForestAnomaly(String studentCode) {
            sources.get(studentCode).spike = true;
        }

        public void makeMlServiceSlow() {
            mlService.delayMillis = 3000;
        }

        public void restoreMlService() {
            mlService.delayMillis = 0;
        }

        public int mlRequestCount() {
            return mlService.requestCount.get();
        }

        public int heartbeatTimeoutSeconds() {
            return 4;
        }
    }

    /** ml-service giả: trả khoảng dự báo rất rộng (không bao giờ đáng ngờ), đếm request, có thể trả lời chậm. */
    private static class FakeMlService {
        final AtomicInteger requestCount = new AtomicInteger();
        volatile long delayMillis = 0;
        private final HttpServer httpServer;

        FakeMlService() throws Exception {
            httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.createContext("/score", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] response = buildResponse(body).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(response);
                }
                requestCount.incrementAndGet();
            });
            httpServer.start();
        }

        int getPort() {
            return httpServer.getAddress().getPort();
        }

        void stop() {
            httpServer.stop(0);
        }

        private String buildResponse(String requestBody) {
            JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
            JsonArray results = new JsonArray();
            for (JsonElement machine : request.getAsJsonArray("machines")) {
                JsonObject forecast = new JsonObject();
                for (String metric : machine.getAsJsonObject().getAsJsonObject("series").keySet()) {
                    JsonObject quantiles = new JsonObject();
                    quantiles.addProperty("q10", -1e9);
                    quantiles.addProperty("q50", 0);
                    quantiles.addProperty("q90", 1e9);
                    forecast.add(metric, quantiles);
                }
                JsonObject result = new JsonObject();
                result.addProperty("machineId", machine.getAsJsonObject().get("machineId").getAsString());
                result.add("forecast", forecast);
                results.add(result);
            }
            JsonObject response = new JsonObject();
            response.addProperty("backend", "fake");
            response.add("results", results);
            return response.toString();
        }
    }
}
