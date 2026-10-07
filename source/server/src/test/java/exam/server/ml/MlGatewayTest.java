// Owner: Nguoi4

package exam.server.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import exam.common.ml.MlResult;
import exam.common.model.Metrics;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test MlGatewayImpl với một "ml-service" giả (HttpServer của JDK):
 * batch một request cho mọi máy, 6 metric, cửa sổ 60 điểm, timeout, fallback khi lỗi, quy tắc 3 lần liên tiếp.
 */
class MlGatewayTest {

    private HttpServer fakeService;
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger statusToReturn = new AtomicInteger(200);
    private volatile long delayMillis = 0;
    /** Khoảng dự báo giả mà "ml-service" trả về cho mọi metric. */
    private volatile double forecastLow = 0;
    private volatile double forecastHigh = 100;
    private MlGatewayImpl gateway;

    @BeforeEach
    void startFakeService() throws Exception {
        fakeService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeService.createContext("/score", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedBodies.add(body);
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String response = statusToReturn.get() == 200 ? buildForecastResponse(body) : "{\"error\":\"model_unavailable\"}";
            reply(exchange, statusToReturn.get(), response);
        });
        // Nhiều thread: một request bị giữ chậm không được chặn các request sau (giống ml-service thật).
        fakeService.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        fakeService.start();

        gateway = new MlGatewayImpl(createConfig(500));
    }

    @AfterEach
    void stopFakeService() {
        fakeService.stop(0);
    }

    private MlConfig createConfig(int timeoutMillis) {
        Properties properties = new Properties();
        properties.setProperty("ml.mode", "CHRONOS");
        properties.setProperty("ml.service.url", "http://localhost:" + fakeService.getAddress().getPort());
        properties.setProperty("ml.service.timeout.ms", String.valueOf(timeoutMillis));
        properties.setProperty("ml.chronos.min.points", "5");
        properties.setProperty("ml.chronos.window", "60");
        properties.setProperty("ml.chronos.consecutive", "3");
        return MlConfig.fromProperties(properties);
    }

    private void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Trả về dự báo [forecastLow, forecastHigh] cho đủ 6 metric của mọi máy trong request. */
    private String buildForecastResponse(String requestBody) {
        JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
        JsonArray results = new JsonArray();
        for (JsonElement machine : request.getAsJsonArray("machines")) {
            JsonObject forecast = new JsonObject();
            for (String metric : machine.getAsJsonObject().getAsJsonObject("series").keySet()) {
                JsonObject quantiles = new JsonObject();
                quantiles.addProperty("q10", forecastLow);
                quantiles.addProperty("q50", (forecastLow + forecastHigh) / 2);
                quantiles.addProperty("q90", forecastHigh);
                forecast.add(metric, quantiles);
            }
            JsonObject result = new JsonObject();
            result.addProperty("machineId", machine.getAsJsonObject().get("machineId").getAsString());
            result.add("forecast", forecast);
            results.add(result);
        }
        JsonObject response = new JsonObject();
        response.addProperty("model", "fake");
        response.add("results", results);
        return response.toString();
    }

    private void feed(String machineId, int count, double kbSent) {
        for (int i = 0; i < count; i++) {
            gateway.recordMetrics(machineId, new Metrics(kbSent, 5, 4, 3, 20, 40, 100, 0));
        }
    }

    private Map<String, MlResult> score() throws Exception {
        return gateway.scoreAllMachines().get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------

    @Test
    void allMachinesAreSentInOneBatchRequest() throws Exception {
        feed("SV001", 20, 10);
        feed("SV002", 20, 10);
        feed("SV003", 20, 10);

        Map<String, MlResult> results = score();

        assertEquals(1, receivedBodies.size(), "phải chỉ có MỘT request cho cả ba máy");
        JsonObject request = JsonParser.parseString(receivedBodies.get(0)).getAsJsonObject();
        assertEquals(3, request.getAsJsonArray("machines").size());
        assertEquals(3, results.size());
    }

    @Test
    void onlySixChronosMetricsAreSent() throws Exception {
        feed("SV001", 20, 10);

        score();

        JsonObject series = JsonParser.parseString(receivedBodies.get(0)).getAsJsonObject()
                .getAsJsonArray("machines").get(0).getAsJsonObject().getAsJsonObject("series");
        assertEquals(6, series.size());
        assertTrue(series.has("kbSent") && series.has("cpuPercent") && series.has("ramPercent"));
        assertFalse(series.has("processCount"));
        assertFalse(series.has("focusLostCount"));
    }

    @Test
    void contextWindowIsCappedAtSixtyPointsAndExcludesTheLatestPoint() throws Exception {
        feed("SV001", 100, 10);

        score();

        JsonObject series = JsonParser.parseString(receivedBodies.get(0)).getAsJsonObject()
                .getAsJsonArray("machines").get(0).getAsJsonObject().getAsJsonObject("series");
        assertEquals(60, series.getAsJsonArray("kbSent").size());
    }

    @Test
    void machineWithTooFewPointsIsNotSent() throws Exception {
        feed("SV001", 3, 10);

        Map<String, MlResult> results = score();

        assertTrue(receivedBodies.isEmpty(), "chưa đủ điểm thì không gọi ml-service");
        assertTrue(results.isEmpty());
    }

    @Test
    void samePointIsNotScoredTwice() throws Exception {
        feed("SV001", 20, 10);
        score();

        Map<String, MlResult> second = score();

        assertEquals(1, receivedBodies.size(), "không có điểm mới thì không gọi lại");
        assertTrue(second.isEmpty());
    }

    @Test
    void valueInsideForecastRangeIsNotSuspicious() throws Exception {
        feed("SV001", 20, 10); // 10 nằm trong [0, 100]

        MlResult result = score().get("SV001");

        assertNotNull(result);
        assertFalse(result.suspicious);
        assertEquals(0, result.consecutiveOutOfRange);
    }

    @Test
    void suspiciousOnlyAfterThreeConsecutiveOutOfRangePoints() throws Exception {
        feed("SV001", 20, 10);
        forecastLow = 0;
        forecastHigh = 20; // kbSent 500 sẽ nằm ngoài khoảng

        feed("SV001", 1, 500);
        assertFalse(score().get("SV001").suspicious);
        feed("SV001", 1, 500);
        assertFalse(score().get("SV001").suspicious);
        feed("SV001", 1, 500);
        MlResult third = score().get("SV001");

        assertTrue(third.suspicious);
        assertEquals(3, third.consecutiveOutOfRange);
        assertTrue(third.reason.contains("kbSent"));
    }

    @Test
    void servicePretendingToBeDownReturns503AndGatewayFallsBackToEmptyResults() throws Exception {
        feed("SV001", 20, 10);
        statusToReturn.set(503);

        Map<String, MlResult> results = score();

        assertTrue(results.isEmpty(), "ml-service lỗi thì bỏ qua ML, không ném exception");

        // Khi ml-service sống lại thì ML tự hoạt động trở lại
        statusToReturn.set(200);
        feed("SV001", 1, 10);
        assertEquals(1, score().size());
    }

    @Test
    void garbageResponseIsIgnored() throws Exception {
        feed("SV001", 20, 10);
        fakeService.removeContext("/score");
        fakeService.createContext("/score", exchange -> reply(exchange, 200, "<html>not json</html>"));

        assertTrue(score().isEmpty());
    }

    @Test
    void slowServiceTimesOutAndGatewayKeepsWorking() throws Exception {
        feed("SV001", 20, 10);
        delayMillis = 2000; // ml-service chậm hơn timeout (500 ms)

        long start = System.currentTimeMillis();
        Map<String, MlResult> results = score();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(results.isEmpty(), "timeout thì bỏ qua ML");
        assertTrue(elapsed < 1500, "phải bỏ cuộc gần timeout 500ms chứ không chờ đủ 2 giây, thực tế " + elapsed + " ms");

        // Sau timeout, lần gọi tiếp theo vẫn chạy bình thường
        delayMillis = 0;
        feed("SV001", 1, 10);
        assertEquals(1, score().size());
    }

    @Test
    void serviceThatIsNotRunningAtAllIsHandled() throws Exception {
        MlGatewayImpl orphanGateway = new MlGatewayImpl(createConfig(500));
        fakeService.stop(0); // đóng cổng: kết nối sẽ bị từ chối
        for (int i = 0; i < 20; i++) {
            orphanGateway.recordMetrics("SV001", new Metrics(10, 5, 4, 3, 20, 40, 100, 0));
        }

        Map<String, MlResult> results = orphanGateway.scoreAllMachines().get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(results.isEmpty());
    }

    @Test
    void secondCallWhileFirstIsStillRunningIsSkipped() throws Exception {
        feed("SV001", 20, 10);
        delayMillis = 300;

        CompletableFuture<Map<String, MlResult>> first = gateway.scoreAllMachines();
        feed("SV001", 1, 10);
        Map<String, MlResult> second = gateway.scoreAllMachines().get(1, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(second.isEmpty(), "lần gọi chồng lên lần đang chạy bị bỏ qua");
        assertEquals(1, first.get(5, java.util.concurrent.TimeUnit.SECONDS).size());
        assertEquals(1, receivedBodies.size());
    }
}
