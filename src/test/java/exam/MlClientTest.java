// Test nối server thi với service ML, dùng service giả dựng bằng HttpServer của JDK (không cần Python).
package exam;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import exam.common.Protocol;
import exam.server.ExamRoom;
import exam.server.ExamServer;
import exam.server.MlClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MlClientTest {
    static final String OK_BODY = "{\"status\":\"ok\",\"results\":[{\"studentId\":\"S1\",\"score\":0.9,\"anomaly\":true}]}";

    HttpServer fake;
    volatile String fakeBody;
    ExamServer server;
    @TempDir Path tmp;

    /** Service giả: ghi lại body nhận được và trả status/body cố định. */
    private AtomicReference<String> startFake(int status, String body) throws IOException {
        AtomicReference<String> received = new AtomicReference<>();
        fakeBody = body;
        fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext("/score", ex -> {
            received.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = fakeBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        fake.start();
        return received;
    }

    private String fakeUrl() {
        return "http://127.0.0.1:" + fake.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (fake != null) {
            fake.stop(0);
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void sendsSamplesAndParsesResult() throws Exception {
        AtomicReference<String> received = startFake(200, OK_BODY);
        JsonArray samples = new JsonArray();
        JsonObject s = new JsonObject();
        s.addProperty("studentId", "S1");
        s.addProperty("window", 1);
        samples.add(s);
        JsonObject result = new MlClient(fakeUrl()).score(samples);
        assertTrue(Protocol.parse(received.get()).has("samples"));
        assertEquals("ok", result.get("status").getAsString());
        assertEquals(0.9, result.getAsJsonArray("results").get(0).getAsJsonObject().get("score").getAsDouble());
    }

    @Test
    void non200ThrowsIOException() throws Exception {
        startFake(500, "{}");
        assertThrows(IOException.class, () -> new MlClient(fakeUrl()).score(new JsonArray()));
    }

    /** Server thi thật, một máy S1 đã vào thi và gửi một FEATURES. */
    private void startExamWithFeatures() throws Exception {
        server = new ExamServer(new JsonObject());
        server.room().setResultsFile(tmp.resolve("results.csv"));
        Path exam = tmp.resolve("exam.json");
        Files.writeString(exam, ExamServerTest.EXAM_JSON, StandardCharsets.UTF_8);
        server.room().loadExam(exam);
        server.start(0);
        ExamServerTest.TestClient c = new ExamServerTest.TestClient(server.tcpPort());
        JsonObject login = Protocol.msg(Protocol.LOGIN);
        login.addProperty("studentId", "S1");
        login.addProperty("name", "An");
        c.send(login);
        c.next(Protocol.LOGIN_OK, 2000);
        JsonObject pre = Protocol.msg(Protocol.PRECHECK_RESULT);
        pre.addProperty("ok", true);
        c.send(pre);
        server.room().startExam();
        c.next(Protocol.EXAM_START, 2000);
        JsonObject f = Protocol.msg(Protocol.FEATURES);
        f.addProperty("window", 1);
        JsonArray values = new JsonArray();
        for (int i = 0; i < 6; i++) {
            values.add(i);
        }
        f.add("values", values);
        c.send(f);
    }

    private ExamRoom.Row s1() {
        return server.room().rows().get(0);
    }

    /** FEATURES đến server bất đồng bộ nên gọi scoreRoomNow lặp lại cho tới khi điểm xuất hiện. */
    private void scoreUntilApplied() throws InterruptedException {
        for (int i = 0; i < 100 && s1().anomalyScore() == null; i++) {
            server.scoreRoomNow();
            Thread.sleep(20);
        }
    }

    @Test
    void serverAppliesScores() throws Exception {
        AtomicReference<String> received = startFake(200, OK_BODY);
        startExamWithFeatures();
        server.setMlClient(new MlClient(fakeUrl()));
        scoreUntilApplied();
        assertEquals(0.9, s1().anomalyScore());
        assertTrue(s1().anomaly());
        JsonObject sent = Protocol.parse(received.get()).getAsJsonArray("samples").get(0).getAsJsonObject();
        assertEquals("S1", sent.get("studentId").getAsString());
        assertEquals(6, sent.getAsJsonArray("values").size());
    }

    @Test
    void mlDownKeepsOldScore() throws Exception {
        startFake(200, OK_BODY);
        startExamWithFeatures();
        server.setMlClient(new MlClient(fakeUrl()));
        scoreUntilApplied();
        fake.stop(0);                                  // service tắt: cổng không còn ai nghe
        assertDoesNotThrow(() -> server.scoreRoomNow());
        assertEquals(0.9, s1().anomalyScore());
        assertTrue(s1().anomaly());
    }
    private List<String> captureLog() {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        server.setListener(lines::add);
        return lines;
    }

    private void waitSamples(int n) throws InterruptedException {
        for (int i = 0; i < 100 && s1().samples() < n; i++) {
            Thread.sleep(20);
        }
        assertEquals(n, s1().samples());
    }

    private long count(List<String> lines, String part) {
        synchronized (lines) {
            return lines.stream().filter(l -> l.contains(part)).count();
        }
    }

    @Test
    void rowShowsSampleCount() throws Exception {
        startFake(200, OK_BODY);
        startExamWithFeatures();
        waitSamples(1);
    }

    @Test
    void logsFlagTransitionsOnlyOnce() throws Exception {
        startFake(200, OK_BODY);
        startExamWithFeatures();
        List<String> lines = captureLog();
        server.setMlClient(new MlClient(fakeUrl()));
        waitSamples(1);
        server.scoreRoomNow();
        server.scoreRoomNow();
        assertEquals(1, count(lines, "Bất thường: S1 (0.90)"), "bị cờ liên tiếp chỉ log một lần");
        fakeBody = "{\"status\":\"ok\",\"results\":[{\"studentId\":\"S1\",\"score\":0.2,\"anomaly\":false}]}";
        server.scoreRoomNow();
        server.scoreRoomNow();
        assertEquals(1, count(lines, "Hết bất thường: S1"));
        assertEquals(1, count(lines, "Bất thường: S1"));
    }

    @Test
    void logsNotEnoughDataOnlyWhenCountChanges() throws Exception {
        startFake(200, "{\"status\":\"not_enough_data\",\"results\":[]}");
        startExamWithFeatures();
        List<String> lines = captureLog();
        server.setMlClient(new MlClient(fakeUrl()));
        waitSamples(1);
        server.scoreRoomNow();
        server.scoreRoomNow();
        server.scoreRoomNow();
        assertEquals(1, count(lines, "ML: chưa đủ dữ liệu (1/20 mẫu)"));
        assertEquals(1, count(lines, "chưa đủ dữ liệu"));
    }

    @Test
    void resultsCsvHasSamplesAndFlaggedCount() throws Exception {
        startFake(200, OK_BODY);
        startExamWithFeatures();
        server.setMlClient(new MlClient(fakeUrl()));
        waitSamples(1);
        server.scoreRoomNow();
        server.scoreRoomNow();
        server.room().endExam("test");
        List<String> csv = Files.readAllLines(tmp.resolve("results.csv"), StandardCharsets.UTF_8);
        assertTrue(csv.get(0).endsWith(",maxAnomaly,status,samples,flaggedCount"), csv.get(0));
        assertTrue(csv.get(1).endsWith(",1,2"), csv.get(1));     // 1 mẫu FEATURES, bị cờ trong 2 lần chấm
    }
}
