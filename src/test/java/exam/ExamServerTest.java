// Test server thật trên cổng 0 với client TCP giả: đăng nhập, gửi đề, nộp bài, mất kết nối, vào lại.
package exam;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import exam.server.ExamRoom;
import exam.server.ExamServer;
import exam.server.Session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExamServerTest {
    static final String EXAM_JSON = "{\"title\":\"De thu\",\"durationMinutes\":1,\"questions\":["
            + "{\"id\":1,\"text\":\"Q1\",\"options\":[\"a\",\"b\",\"c\"],\"answer\":1},"
            + "{\"id\":2,\"text\":\"Q2\",\"options\":[\"a\",\"b\"],\"answer\":0}]}";

    /** Client TCP đơn giản: send, và next() đọc bỏ qua tin khác loại cho tới khi gặp type cần tìm. */
    static class TestClient implements AutoCloseable {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;

        TestClient(int port) throws IOException {
            socket = new Socket("localhost", port);
            in = Protocol.reader(socket);
            out = Protocol.writer(socket);
        }

        void send(JsonObject m) {
            Protocol.send(out, m);
        }

        void sendRaw(String line) {
            out.print(line + "\n");
            out.flush();
        }

        /** Trả null nếu hết giờ mà chưa thấy tin loại này. */
        JsonObject tryNext(String type, int timeoutMs) throws IOException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (true) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return null;
                }
                socket.setSoTimeout((int) left);
                try {
                    String line = in.readLine();
                    if (line == null) {
                        return null;
                    }
                    JsonObject m = Protocol.parse(line);
                    if (Protocol.type(m).equals(type)) {
                        return m;
                    }
                } catch (SocketTimeoutException e) {
                    return null;
                }
            }
        }

        JsonObject next(String type, int timeoutMs) throws IOException {
            JsonObject m = tryNext(type, timeoutMs);
            assertNotNull(m, "Không nhận được " + type);
            return m;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @TempDir Path tmp;
    ExamServer server;
    final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        JsonObject rules = new JsonObject();
        JsonArray black = new JsonArray();
        black.add("zalo.exe");
        rules.add("blacklistApps", black);
        server = new ExamServer(rules);
        server.room().setResultsRoot(tmp.resolve("ket-qua"));
        Path exam = tmp.resolve("exam.json");
        Files.writeString(exam, EXAM_JSON, StandardCharsets.UTF_8);
        server.room().loadExam(exam);
        server.start(0);
    }

    @AfterEach
    void tearDown() throws IOException {
        for (TestClient c : clients) {
            c.close();
        }
        server.stop();
    }

    private TestClient connect() throws IOException {
        TestClient c = new TestClient(server.tcpPort());
        clients.add(c);
        return c;
    }

    private JsonObject loginMsg(String id, String name) {
        JsonObject m = Protocol.msg(Protocol.LOGIN);
        m.addProperty("studentId", id);
        m.addProperty("name", name);
        return m;
    }

    private TestClient login(String id) throws IOException {
        TestClient c = connect();
        c.send(loginMsg(id, "Sinh viên " + id));
        c.next(Protocol.LOGIN_OK, 2000);
        return c;
    }

    private void precheck(TestClient c, boolean ok) {
        JsonObject m = Protocol.msg(Protocol.PRECHECK_RESULT);
        m.addProperty("ok", ok);
        m.add("issues", new JsonArray());
        c.send(m);
    }

    /** Đăng nhập + PRECHECK đạt + giáo viên bắt đầu (nếu chưa) + nhận EXAM_START. */
    private TestClient loginAndStart(String id) throws IOException {
        TestClient c = login(id);
        precheck(c, true);
        if (!server.room().isStarted()) {
            server.room().startExam();
        }
        c.next(Protocol.EXAM_START, 2000);
        return c;
    }

    private JsonObject submitCorrect(TestClient c) throws IOException {
        JsonObject answers = new JsonObject();
        answers.addProperty("1", 1);
        answers.addProperty("2", 0);
        JsonObject m = Protocol.msg(Protocol.SUBMIT);
        m.add("answers", answers);
        c.send(m);
        return c.next(Protocol.RESULT, 2000);
    }

    private ExamRoom.Row row(String id) {
        for (ExamRoom.Row r : server.room().rows()) {
            if (r.studentId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    private boolean waitStatus(String id, String status, int ms) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            ExamRoom.Row r = row(id);
            if (r != null && r.status().equals(status)) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    @Test
    void loginOkContainsRules() throws IOException {
        TestClient c = connect();
        c.send(loginMsg("S1", "An"));
        JsonObject ok = c.next(Protocol.LOGIN_OK, 2000);
        assertEquals("zalo.exe", ok.getAsJsonObject("rules").getAsJsonArray("blacklistApps").get(0).getAsString());
    }

    private int loginOkInterval(JsonObject rules) throws IOException {
        ExamServer other = new ExamServer(rules);
        other.start(0);
        try (TestClient c = new TestClient(other.tcpPort())) {
            c.send(loginMsg("S1", "An"));
            return c.next(Protocol.LOGIN_OK, 2000).get("featureIntervalSeconds").getAsInt();
        } finally {
            other.stop();
        }
    }

    @Test
    void loginOkCarriesFeatureInterval() throws IOException {
        JsonObject rules = new JsonObject();
        rules.addProperty("featureIntervalSeconds", 20);
        assertEquals(20, loginOkInterval(rules));
    }

    @Test
    void invalidIntervalFallsBackTo30() throws IOException {
        JsonObject tooBig = new JsonObject();
        tooBig.addProperty("featureIntervalSeconds", 99);
        assertEquals(30, loginOkInterval(tooBig));
        assertEquals(30, loginOkInterval(new JsonObject()), "thiếu thì dùng 30");
    }

    @Test
    void emptyIdIsRejected() throws IOException {
        TestClient c = connect();
        c.send(loginMsg("", "An"));
        assertEquals("Thiếu mã SV", Protocol.str(c.next(Protocol.LOGIN_FAIL, 2000), "reason"));
    }

    @Test
    void duplicateLoginWhileConnectedFails() throws IOException {
        login("S1");
        TestClient second = connect();
        second.send(loginMsg("S1", "Máy khác"));
        JsonObject fail = second.next(Protocol.LOGIN_FAIL, 2000);
        assertEquals("Mã SV này đang đăng nhập ở máy khác", Protocol.str(fail, "reason"));
    }

    @Test
    void examNotSentBeforePrecheck() throws IOException {
        TestClient c = login("S1");
        server.room().startExam();
        assertNull(c.tryNext(Protocol.EXAM_START, 500));
        precheck(c, true);
        assertNotNull(c.next(Protocol.EXAM_START, 2000));
    }

    @Test
    void examQuestionsHaveNoAnswers() throws IOException {
        TestClient c = login("S1");
        precheck(c, true);
        server.room().startExam();
        JsonObject start = c.next(Protocol.EXAM_START, 2000);
        JsonArray qs = start.getAsJsonArray("questions");
        assertEquals(2, qs.size());
        for (JsonElement e : qs) {
            JsonObject q = e.getAsJsonObject();
            assertFalse(q.has("answer"));
            assertTrue(q.has("id") && q.has("text") && q.has("options"));
        }
        assertEquals("De thu", Protocol.str(start, "title"));
    }

    /** Đóng kết nối cũ rồi vào lại cùng mã SV; trả về EXAM_START nhận được. */
    private JsonObject loginAgainForExam(String id) throws IOException {
        clients.get(clients.size() - 1).close();
        TestClient c = connect();
        for (int i = 0; i < 100; i++) {
            c.send(loginMsg(id, "Sinh viên " + id));
            JsonObject m = c.tryNext(Protocol.LOGIN_OK, 100);
            if (m != null) {
                return c.next(Protocol.EXAM_START, 2000);
            }
        }
        fail("Không đăng nhập lại được");
        return null;
    }

    @Test
    void submitReturnsResult() throws IOException {
        TestClient c = loginAndStart("S1");
        JsonObject r = submitCorrect(c);
        assertEquals(10.0, r.get("score").getAsDouble());
        assertEquals("Đã nộp", row("S1").status());
    }

    @Test
    void reconnectGetsExamWithRemainingTime() throws Exception {
        TestClient c = loginAndStart("S1");
        c.close();
        assertTrue(waitStatus("S1", Session.ST_DISCONNECTED, 2000));
        JsonObject start = loginAgainForExam("S1");
        int remaining = start.get("remainingSeconds").getAsInt();
        assertTrue(remaining > 0 && remaining <= 60, "remaining = " + remaining);
    }

    @Test
    void disconnectOfOldSocketDoesNotKickNewOne() throws Exception {
        TestClient a = login("S1");
        a.close();
        TestClient b = connect();
        for (int i = 0; i < 100; i++) { // B có thể đến trước khi server kịp thấy A đóng
            b.send(loginMsg("S1", "Sinh viên S1"));
            if (b.tryNext(Protocol.LOGIN_OK, 100) != null) {
                break;
            }
        }
        Thread.sleep(1000);
        assertNotEquals(Session.ST_DISCONNECTED, row("S1").status());
    }

    @Test
    void submitAfterEndIsAcceptedAndWritten() throws IOException {
        TestClient c = loginAndStart("S1");
        server.room().endExam("test");
        c.next(Protocol.EXAM_END, 2000);
        JsonObject r = submitCorrect(c);
        assertEquals(10.0, r.get("score").getAsDouble());
        String csv = Files.readString(server.room().examDir().resolve("results.csv"), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFstudentId,name,score,correct,total,violations,maxAnomaly,status"));
        assertTrue(csv.contains("S1,Sinh viên S1,10.0,2,2,0,,Đã nộp"), csv);
    }

    @Test
    void garbageLineDoesNotCloseConnection() throws IOException {
        TestClient c = connect();
        c.sendRaw("abc{");
        c.send(loginMsg("S1", "An"));
        assertNotNull(c.next(Protocol.LOGIN_OK, 2000));
    }

    @Test
    void badFeaturesIgnored() throws IOException {
        TestClient c = loginAndStart("S1");
        JsonObject f = Protocol.msg(Protocol.FEATURES);
        f.addProperty("window", 1);
        JsonArray three = new JsonArray();
        three.add(1);
        three.add(2);
        three.add(3);
        f.add("values", three);
        c.send(f);
        assertNotNull(submitCorrect(c));
    }

    @Test
    void violationCountedAndShownInRows() throws Exception {
        TestClient c = loginAndStart("S1");
        JsonObject v = Protocol.msg(Protocol.VIOLATION);
        v.addProperty("kind", "USB");
        v.addProperty("detail", "E:\\");
        v.addProperty("time", "10:00:00");
        c.send(v);
        for (int i = 0; i < 100 && row("S1").violations() == 0; i++) {
            Thread.sleep(20);
        }
        assertEquals(1, row("S1").violations());
        assertTrue(row("S1").lastViolation().contains("USB"));
    }

    /** Đóng kết nối cũ rồi đăng nhập lại cùng mã SV (thử lại tới khi server nhận ra kết nối cũ đã đóng). */
    private TestClient reconnect(TestClient old, String id) throws IOException {
        old.close();
        TestClient c = connect();
        for (int i = 0; i < 100; i++) {
            c.send(loginMsg(id, "Sinh viên " + id));
            if (c.tryNext(Protocol.LOGIN_OK, 100) != null) {
                return c;
            }
        }
        fail("Không đăng nhập lại được");
        return null;
    }

    @Test
    void loginAfterSubmitGetsResultAgain() throws IOException {
        TestClient first = loginAndStart("S1");
        submitCorrect(first);
        // đã nộp thì đăng nhập lại (thi lượt mới hoặc tắt app mở lại) nhận ngay RESULT cũ, không cần PRECHECK
        TestClient again = reconnect(first, "S1");
        assertEquals(10.0, again.next(Protocol.RESULT, 2000).get("score").getAsDouble());
        assertNull(again.tryNext(Protocol.EXAM_START, 300), "Đã nộp thì không được gửi đề lại");

        // kết thúc rồi vẫn vậy
        server.room().endExam("test");
        TestClient afterEnd = reconnect(again, "S1");
        assertEquals(10.0, afterEnd.next(Protocol.RESULT, 2000).get("score").getAsDouble());
    }

    @Test
    void loginAfterEndWithoutSubmitIsRejected() throws IOException {
        TestClient s1 = loginAndStart("S1");
        submitCorrect(s1);
        server.room().endExam("test");
        TestClient stranger = connect();
        stranger.send(loginMsg("S9", "Chưa từng vào"));
        assertEquals("Bài thi đã kết thúc", Protocol.str(stranger.next(Protocol.LOGIN_FAIL, 2000), "reason"));
    }

    @Test
    void sameStudentCanLoginAfterNewSession() throws Exception {
        TestClient first = loginAndStart("S1");
        submitCorrect(first);
        server.room().endExam("test");
        first.close();
        server.room().newSession();

        // phòng mới trống: mã SV cũ là người mới, phải qua PRECHECK và nhận đề kỳ mới
        TestClient again = login("S1");
        assertNull(again.tryNext(Protocol.RESULT, 300), "Kỳ mới không có kết quả cũ");
        server.room().startExam();
        precheck(again, true);
        again.next(Protocol.EXAM_START, 2000);
        assertEquals(Session.ST_IN_EXAM, row("S1").status());
    }

    @Test
    void remainingIsZeroAfterEnd() throws IOException {
        loginAndStart("S1");
        assertTrue(server.room().remainingSeconds() > 0);
        server.room().endExam("test");
        assertEquals(0, server.room().remainingSeconds());
    }

    @Test
    void loginAfterEndIsHandled() throws IOException {
        TestClient s1 = loginAndStart("S1");
        TestClient s2 = loginAndStart("S2");
        submitCorrect(s1);
        server.room().endExam("test");
        s2.next(Protocol.EXAM_END, 2000);

        // máy mới đăng nhập sau khi kết thúc: bị từ chối với lý do rõ ràng, không treo ở màn chờ
        TestClient late = connect();
        late.send(loginMsg("S3", "Đến muộn"));
        assertEquals("Bài thi đã kết thúc", Protocol.str(late.next(Protocol.LOGIN_FAIL, 2000), "reason"));

        // máy đã nộp bài vào lại: nhận lại đúng RESULT cũ
        TestClient again = reconnect(s1, "S1");
        assertEquals(10.0, again.next(Protocol.RESULT, 2000).get("score").getAsDouble());

        // máy chưa nộp (rớt mạng đúng lúc kết thúc) vào lại: nhận EXAM_END để tự nộp, nộp muộn vẫn được chấm
        TestClient back = reconnect(s2, "S2");
        back.next(Protocol.EXAM_END, 2000);
        assertEquals(10.0, submitCorrect(back).get("score").getAsDouble());
    }

    @Test
    void submittedStudentGetsResultAgainWhenReconnectingDuringExam() throws IOException {
        TestClient c = loginAndStart("S1");
        submitCorrect(c);                              // RESULT có thể đã mất do rớt mạng: vào lại phải nhận lại
        TestClient again = reconnect(c, "S1");
        assertEquals(10.0, again.next(Protocol.RESULT, 2000).get("score").getAsDouble());
    }

    @Test
    void silentClientDuringExamIsDropped() throws Exception {
        loginAndStart("S1");
        server.idleTimeoutMs = 50;
        Thread.sleep(100);
        server.checkIdle();
        assertEquals(Session.ST_DISCONNECTED, row("S1").status());
    }

    @Test
    void longWaitBeforeStartIsNotDropped() throws Exception {
        TestClient c = login("S1");
        precheck(c, true);                             // PRECHECK xong rồi chờ giáo viên bấm Bắt đầu, client không gửi gì thêm
        server.idleTimeoutMs = 50;
        Thread.sleep(150);                             // chờ lâu hơn ngưỡng im lặng
        server.room().startExam();
        server.checkIdle();
        assertEquals(Session.ST_IN_EXAM, row("S1").status());
        assertNotNull(c.tryNext(Protocol.EXAM_START, 2000), "Vẫn phải nhận EXAM_START");
    }

    @Test
    void silentAfterReceivingExamIsStillDropped() throws Exception {
        TestClient c = login("S1");
        precheck(c, true);
        server.idleTimeoutMs = 100;
        Thread.sleep(150);
        server.room().startExam();
        c.next(Protocol.EXAM_START, 2000);
        Thread.sleep(200);                             // nhận đề rồi mà im lặng thật sự thì vẫn bị đá
        server.checkIdle();
        assertEquals(Session.ST_DISCONNECTED, row("S1").status());
    }

    @Test
    void newSessionAfterEndAllowsStart() throws Exception {
        TestClient c = loginAndStart("S1");
        assertThrows(IllegalStateException.class, () -> server.room().newSession(), "Chưa kết thúc thì không được làm mới");
        server.room().endExam("test");
        c.next(Protocol.EXAM_END, 2000);
        Path firstDir = server.room().examDir();
        server.room().newSession();
        assertFalse(server.room().isStarted());
        assertFalse(server.room().isEnded());
        assertEquals("De thu", server.room().examTitle(), "Đề đang nạp phải được giữ lại");
        assertNull(server.room().examDir());
        server.room().startExam();
        assertTrue(server.room().isStarted());
        assertNotEquals(firstDir, server.room().examDir());
        assertTrue(Files.exists(firstDir.resolve("results.csv")), "Kết quả kỳ trước vẫn còn nguyên");
        assertTrue(Files.readString(firstDir.resolve("events.csv"), StandardCharsets.UTF_8).contains(",S1,"));
        assertFalse(Files.readString(server.room().examDir().resolve("events.csv"), StandardCharsets.UTF_8).contains(",S1,"),
            "events.csv của kỳ mới không lẫn dữ liệu kỳ trước");
    }

    @Test
    void canLoadAnotherExamAfterNewSession() throws Exception {
        loginAndStart("S1");
        server.room().endExam("test");
        Path other = tmp.resolve("other.json");
        Files.writeString(other, EXAM_JSON.replace("De thu", "De khac"), StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> server.room().loadExam(other), "Đã bắt đầu thì chưa đổi đề được");
        server.room().newSession();
        server.room().loadExam(other);
        assertEquals("De khac", server.room().examTitle());
    }

    @Test
    void newSessionClearsSamplesAndSessions() throws Exception {
        TestClient c = loginAndStart("S1");
        JsonObject f = Protocol.msg(Protocol.FEATURES);
        f.addProperty("window", 1);
        JsonArray values = new JsonArray();
        for (int i = 0; i < 6; i++) { values.add(i); }
        f.add("values", values);
        c.send(f);
        JsonObject v = Protocol.msg(Protocol.VIOLATION);
        v.addProperty("kind", "USB");
        v.addProperty("detail", "E:");
        c.send(v);
        long deadline = System.currentTimeMillis() + 2000;
        while ((row("S1").samples() == 0 || row("S1").violations() == 0) && System.currentTimeMillis() < deadline) { Thread.sleep(20); }
        assertEquals(1, row("S1").samples());
        server.room().endExam("test");
        server.room().newSession();
        assertTrue(server.room().rows().isEmpty());
        assertNull(c.tryNext(Protocol.EXAM_START, 300));
        assertEquals(-1, c.socket.getInputStream().read(), "Server phải đóng kết nối của máy còn lại");
        Thread.sleep(150);                             // luồng đọc cũ kết thúc sau đó không được làm bẩn kỳ mới
        assertTrue(server.room().history("S1").isEmpty());
        assertTrue(server.room().rows().isEmpty());
        TestClient again = login("S1");
        assertEquals(0, row("S1").samples());
        assertEquals(0, row("S1").violations());
        assertNull(row("S1").anomalyScore());
    }

    @Test
    void sameStudentCanLoginInNewSession() throws Exception {
        TestClient c = loginAndStart("S1");
        assertEquals(10.0, submitCorrect(c).get("score").getAsDouble());
        server.room().endExam("test");
        server.room().newSession();
        TestClient again = login("S1");                // không bị "đang đăng nhập ở máy khác", không nhận lại RESULT cũ
        assertNull(row("S1").score());
        precheck(again, true);
        server.room().startExam();
        JsonObject start = again.next(Protocol.EXAM_START, 2000);
        assertTrue(start.get("remainingSeconds").getAsInt() > 55, "Kỳ mới có đủ thời gian làm bài");
        assertEquals(10.0, submitCorrect(again).get("score").getAsDouble());
    }

    @Test
    void unsubmittedClientGetsExamEndWhenNewSessionStarts() throws Exception {
        TestClient c = loginAndStart("S1");
        server.room().endExam("test");
        server.room().newSession();
        assertNotNull(c.tryNext(Protocol.EXAM_END, 500), "Máy chưa nộp phải được báo kết thúc trước khi đóng");
    }

    @Test
    void rowCarriesOsFocusLostAndViolationTime() throws Exception {
        TestClient c = connect();
        JsonObject login = loginMsg("S1", "An");
        login.addProperty("os", "Windows 11");
        c.send(login);
        c.next(Protocol.LOGIN_OK, 2000);
        precheck(c, true);
        server.room().startExam();
        c.next(Protocol.EXAM_START, 2000);
        for (int window = 1; window <= 2; window++) {          // hai cửa sổ, mỗi cửa sổ rời cửa sổ thi 3 lần
            JsonObject f = Protocol.msg(Protocol.FEATURES);
            f.addProperty("window", window);
            JsonArray values = new JsonArray();
            for (int v : new int[] {0, 3, 10, 0, 0, 1}) { values.add(v); }
            f.add("values", values);
            c.send(f);
        }
        JsonObject v = Protocol.msg(Protocol.VIOLATION);
        v.addProperty("kind", "NET");
        v.addProperty("detail", "chatgpt.com (1.2.3.4)");
        c.send(v);
        long deadline = System.currentTimeMillis() + 2000;
        while ((row("S1").samples() < 2 || row("S1").violations() == 0) && System.currentTimeMillis() < deadline) { Thread.sleep(20); }
        assertEquals("Windows 11", row("S1").os());
        assertEquals(6, row("S1").focusLost());
        assertTrue(row("S1").lastViolationAt().matches("\\d\\d:\\d\\d"), row("S1").lastViolationAt());
    }

    @Test
    void loadExamRejectsBadAnswerIndex() throws IOException {
        Path bad = tmp.resolve("bad.json");
        Files.writeString(bad, EXAM_JSON.replace("\"answer\":0", "\"answer\":7"), StandardCharsets.UTF_8);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> server.room().loadExam(bad));
        assertTrue(e.getMessage().contains("2"), e.getMessage());
    }
}
