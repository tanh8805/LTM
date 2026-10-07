// Owner: Nguoi1

package exam.server.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.common.model.AlertLevel;
import exam.common.model.Metrics;
import exam.common.model.RateMode;
import exam.common.model.Role;
import exam.common.model.ViolationType;
import exam.common.protocol.AlertMessage;
import exam.common.protocol.AnswerAckMessage;
import exam.common.protocol.AnswerMessage;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.ExamEndMessage;
import exam.common.protocol.ExamStartMessage;
import exam.common.protocol.HeartbeatAckMessage;
import exam.common.protocol.HeartbeatMessage;
import exam.common.protocol.LoginFailMessage;
import exam.common.protocol.LoginMessage;
import exam.common.protocol.LoginOkMessage;
import exam.common.protocol.Message;
import exam.common.protocol.NoticeMessage;
import exam.common.protocol.ReconnectMessage;
import exam.common.protocol.ReconnectOkMessage;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import exam.common.protocol.RoomStatsMessage;
import exam.common.protocol.RulesConfigMessage;
import exam.common.protocol.SetRateMessage;
import exam.common.protocol.SubmitMessage;
import exam.common.protocol.SubmitOkMessage;
import exam.common.protocol.TimeSyncMessage;
import exam.common.protocol.ViolationMessage;
import exam.server.ServerApp;
import exam.server.session.ClientSession;
import exam.server.testutil.TestClient;
import exam.server.testutil.TestServer;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test mạng: Server thật (TCP + virtual thread + SQLite tạm) và client thô nói đúng giao thức.
 * Gồm đăng nhập, lỗi, heartbeat, mất heartbeat, reconnect, ca thi, vi phạm/ALERT, rate control, NOTICE.
 */
class ServerNetworkTest {

    @TempDir
    Path tempDirectory;

    private ServerApp server;
    private final java.util.List<TestClient> clients = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        for (TestClient client : clients) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    private void startServer() throws Exception {
        startServer(new Properties(), new Properties());
    }

    private void startServer(Properties serverOverrides, Properties mlOverrides) throws Exception {
        server = TestServer.start(tempDirectory, serverOverrides, mlOverrides);
    }

    private TestClient connect() throws Exception {
        TestClient client = new TestClient(server.getPort());
        clients.add(client);
        return client;
    }

    private TestClient loginTeacher() throws Exception {
        TestClient teacher = connect();
        teacher.login("gv01", "teacher123", Role.TEACHER, null);
        return teacher;
    }

    private JsonObject json(String key, Object value) {
        JsonObject json = new JsonObject();
        if (value instanceof Number number) {
            json.addProperty(key, number);
        } else {
            json.addProperty(key, String.valueOf(value));
        }
        return json;
    }

    /** Giáo viên tạo đề ngẫu nhiên và ca thi; trả về mã ca. */
    private String createShift(TestClient teacher, String candidatesCsv, int durationSeconds) throws Exception {
        JsonObject exam = new JsonObject();
        exam.addProperty("title", "De mang");
        exam.addProperty("durationMinutes", 10);
        exam.addProperty("randomCount", 6);
        ResponseMessage examResponse = teacher.request("CREATE_EXAM", exam);
        assertTrue(examResponse.ok, examResponse.error);

        JsonObject shift = new JsonObject();
        shift.addProperty("examId", examResponse.data.get("examId").getAsInt());
        shift.addProperty("durationSeconds", durationSeconds);
        shift.addProperty("candidatesCsv", candidatesCsv);
        ResponseMessage shiftResponse = teacher.request("CREATE_SHIFT", shift);
        assertTrue(shiftResponse.ok, shiftResponse.error);
        return shiftResponse.data.get("code").getAsString();
    }

    private TestClient loginStudent(String studentCode, String examCode) throws Exception {
        TestClient student = connect();
        student.login(studentCode, "123456", Role.STUDENT, examCode);
        return student;
    }

    // ------------------------------------------------------------------
    // Đăng nhập
    // ------------------------------------------------------------------

    @Test
    void teacherLoginSucceedsAndReturnsToken() throws Exception {
        startServer();
        TestClient client = connect();

        LoginOkMessage ok = client.login("gv01", "teacher123", Role.TEACHER, null);

        assertNotNull(ok.token);
        assertEquals(Role.TEACHER, ok.role);
        assertEquals("Giảng viên Mẫu", ok.fullName);
        assertTrue(ok.serverTime > 0);
    }

    @Test
    void studentLoginSucceedsWhenInCandidateList() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient client = connect();

        LoginOkMessage ok = client.login("SV001", "123456", Role.STUDENT, code);

        assertEquals(Role.STUDENT, ok.role);
    }

    @Test
    void wrongPasswordGivesLoginFail() throws Exception {
        startServer();
        TestClient client = connect();

        client.send(new LoginMessage("gv01", "sai-mat-khau", Role.TEACHER, null));

        assertTrue(client.read(3000) instanceof LoginFailMessage);
    }

    @Test
    void wrongRoleGivesLoginFail() throws Exception {
        startServer();
        TestClient client = connect();

        // SV001 là sinh viên, không đăng nhập được bằng vai trò giáo viên (và ngược lại)
        client.send(new LoginMessage("SV001", "123456", Role.TEACHER, null));
        assertTrue(client.read(3000) instanceof LoginFailMessage);

        client.send(new LoginMessage("gv01", "teacher123", Role.STUDENT, "CA001"));
        assertTrue(client.read(3000) instanceof LoginFailMessage);
    }

    @Test
    void unknownUserAndMissingFieldsGiveLoginFail() throws Exception {
        startServer();
        TestClient client = connect();

        client.send(new LoginMessage("khong-co", "x", Role.TEACHER, null));
        assertTrue(client.read(3000) instanceof LoginFailMessage);

        client.send(new LoginMessage("gv01", null, Role.TEACHER, null));
        assertTrue(client.read(3000) instanceof LoginFailMessage);

        client.send(new LoginMessage("gv01", "teacher123", null, null));
        assertTrue(client.read(3000) instanceof LoginFailMessage);
    }

    @Test
    void studentWithoutExamCodeOrWithUnknownCodeIsRejected() throws Exception {
        startServer();
        TestClient client = connect();

        client.send(new LoginMessage("SV001", "123456", Role.STUDENT, null));
        assertTrue(client.read(3000) instanceof LoginFailMessage);

        client.send(new LoginMessage("SV001", "123456", Role.STUDENT, "KHONGCO"));
        LoginFailMessage fail = (LoginFailMessage) client.read(3000);
        assertTrue(fail.reason.contains("không tồn tại"));
    }

    @Test
    void studentNotInCandidateListIsRejected() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient client = connect();

        client.send(new LoginMessage("SV002", "123456", Role.STUDENT, code));

        LoginFailMessage fail = (LoginFailMessage) client.read(3000);
        assertTrue(fail.reason.contains("danh sách thí sinh"));
    }

    @Test
    void secondLoginOfSameStudentWhileOnlineIsRejected() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        loginStudent("SV001", code);
        TestClient second = connect();

        second.send(new LoginMessage("SV001", "123456", Role.STUDENT, code));

        LoginFailMessage fail = (LoginFailMessage) second.read(3000);
        assertTrue(fail.reason.contains("đang đăng nhập"));
    }

    @Test
    void tooManyFailedLoginsDisconnectTheClient() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("login.max.failures", "3");
        startServer(overrides, new Properties());
        TestClient client = connect();

        for (int i = 0; i < 3; i++) {
            client.send(new LoginMessage("gv01", "doan-mat-khau-" + i, Role.TEACHER, null));
            client.read(3000);
        }

        assertTrue(client.isClosedByServer(3000));
    }

    @Test
    void loginPasswordIsNotStoredInPlainText() throws Exception {
        startServer();

        java.sql.Connection connection = server.getDatabase().openConnection();
        try (connection; java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet resultSet = statement.executeQuery("SELECT password_hash FROM students WHERE username = 'SV001'")) {
            resultSet.next();
            assertFalse(resultSet.getString(1).contains("123456"));
        }
    }

    // ------------------------------------------------------------------
    // Message lỗi, chưa đăng nhập, phân quyền
    // ------------------------------------------------------------------

    @Test
    void messageBeforeLoginIsRejectedWithNotLoggedIn() throws Exception {
        startServer();
        TestClient client = connect();

        client.send(new HeartbeatMessage(1, new Metrics()));

        ErrorMessage error = (ErrorMessage) client.read(3000);
        assertEquals("NOT_LOGGED_IN", error.code);
    }

    @Test
    void malformedJsonGivesBadMessageAndConnectionStaysUsable() throws Exception {
        startServer();
        TestClient client = connect();

        client.sendRaw("đây không phải json");
        assertEquals("BAD_MESSAGE", ((ErrorMessage) client.read(3000)).code);

        client.sendRaw("{\"type\":\"KHONG_BIET\"}");
        assertEquals("BAD_MESSAGE", ((ErrorMessage) client.read(3000)).code);

        client.sendRaw("{\"seq\":1}");
        assertEquals("BAD_MESSAGE", ((ErrorMessage) client.read(3000)).code);

        client.sendRaw("[1,2,3]");
        assertEquals("BAD_MESSAGE", ((ErrorMessage) client.read(3000)).code);

        // Connection vẫn dùng được sau khi gửi rác
        assertNotNull(client.login("gv01", "teacher123", Role.TEACHER, null));
    }

    @Test
    void messageWithWrongFieldTypeDoesNotCrashServer() throws Exception {
        startServer();
        TestClient client = connect();

        client.sendRaw("{\"type\":\"LOGIN\",\"username\":123,\"password\":[1],\"role\":\"HACKER\"}");

        assertEquals("BAD_MESSAGE", ((ErrorMessage) client.read(3000)).code);
        assertNotNull(connect().login("gv01", "teacher123", Role.TEACHER, null)); // Server vẫn sống
    }

    @Test
    void oversizedLineDisconnectsTheClient() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("message.max.bytes", "1000");
        startServer(overrides, new Properties());
        TestClient client = connect();

        client.sendRaw("x".repeat(5000));

        assertTrue(client.isClosedByServer(3000));
    }

    @Test
    void studentCannotUseTeacherActions() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = loginStudent("SV001", code);

        student.send(new RequestMessage("x1", "LIST_QUESTIONS", new JsonObject()));
        assertEquals("FORBIDDEN", ((ErrorMessage) student.readUntil(ErrorMessage.class, 3000)).code);

        student.send(new NoticeMessage("ALL", "hack"));
        assertEquals("FORBIDDEN", ((ErrorMessage) student.readUntil(ErrorMessage.class, 3000)).code);
    }

    @Test
    void teacherCannotSendStudentOnlyMessages() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();

        teacher.send(new AnswerMessage(1, 1, 0));
        assertEquals("FORBIDDEN", ((ErrorMessage) teacher.readUntil(ErrorMessage.class, 3000)).code);

        teacher.send(new SubmitMessage(1));
        assertEquals("FORBIDDEN", ((ErrorMessage) teacher.readUntil(ErrorMessage.class, 3000)).code);
    }

    @Test
    void clientCannotSendServerOnlyMessages() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();

        teacher.send(new AlertMessage("SV001", AlertLevel.RED, "giả mạo"));

        assertEquals("UNEXPECTED_MESSAGE", ((ErrorMessage) teacher.readUntil(ErrorMessage.class, 3000)).code);
    }

    @Test
    void secondLoginOnSameConnectionIsRejected() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();

        teacher.send(new LoginMessage("gv01", "teacher123", Role.TEACHER, null));

        assertEquals("ALREADY_LOGGED_IN", ((ErrorMessage) teacher.read(3000)).code);
    }

    // ------------------------------------------------------------------
    // Heartbeat, mất heartbeat
    // ------------------------------------------------------------------

    @Test
    void heartbeatIsAcknowledgedWithSameSeq() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = loginStudent("SV001", code);

        student.send(new HeartbeatMessage(7, new Metrics(1, 2, 3, 4, 5, 6, 7, 0)));

        HeartbeatAckMessage ack = student.readUntil(HeartbeatAckMessage.class, 3000);
        assertEquals(7, ack.seq);
        ClientSession session = server.getSessionRegistry().findByMachineId("SV001");
        assertEquals(7, session.lastHeartbeatSeq);
        assertTrue(session.lastHeartbeatTime > 0);
    }

    @Test
    void silentStudentIsDetectedOfflineAndTeacherGetsAlert() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("heartbeat.timeout.seconds", "1");
        startServer(overrides, new Properties());
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = loginStudent("SV001", code);
        assertTrue(server.getSessionRegistry().findByMachineId("SV001").online);

        // Sinh viên không gửi HEARTBEAT nào: Server phải tự phát hiện sau ~1 giây
        assertTrue(student.isClosedByServer(5000), "Server phải đóng connection của client im lặng");

        assertFalse(server.getSessionRegistry().findByMachineId("SV001").online);
        AlertMessage alert = teacher.readUntil(AlertMessage.class, 3000);
        assertEquals("SV001", alert.machineId);
        assertEquals(AlertLevel.YELLOW, alert.level);
        assertTrue(alert.reason.contains("Mất kết nối"));
    }

    @Test
    void teacherIsNotDisconnectedForBeingSilent() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("heartbeat.timeout.seconds", "1");
        startServer(overrides, new Properties());
        TestClient teacher = loginTeacher();

        Thread.sleep(1800); // giáo viên không gửi heartbeat

        ResponseMessage response = teacher.request("LIST_EXAMS", new JsonObject());
        assertTrue(response.ok);
    }

    @Test
    void heartbeatKeepsStudentAliveBeyondTimeout() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("heartbeat.timeout.seconds", "1");
        startServer(overrides, new Properties());
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = loginStudent("SV001", code);

        for (int seq = 1; seq <= 4; seq++) {
            Thread.sleep(500);
            student.send(new HeartbeatMessage(seq, new Metrics()));
            assertNotNull(student.readUntil(HeartbeatAckMessage.class, 3000));
        }

        assertTrue(server.getSessionRegistry().findByMachineId("SV001").online);
    }

    // ------------------------------------------------------------------
    // Reconnect
    // ------------------------------------------------------------------

    @Test
    void reconnectRestoresSessionAndSavedAnswers() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = connect();
        LoginOkMessage loginOk = student.login("SV001", "123456", Role.STUDENT, code);

        teacher.request("START_SHIFT", json("code", code));
        ExamStartMessage start = student.readUntil(ExamStartMessage.class, 3000);
        int questionId = start.questions.get(0).questionId;
        student.send(new AnswerMessage(1, questionId, 2));
        assertTrue(student.readUntil(AnswerAckMessage.class, 3000).saved);

        // Mất mạng đột ngột
        student.close();
        waitUntilOffline("SV001");
        ClientSession before = server.getSessionRegistry().findByMachineId("SV001");

        // Nối lại bằng token trên connection MỚI
        TestClient newConnection = connect();
        newConnection.send(new ReconnectMessage(loginOk.token, 1));
        ReconnectOkMessage reconnectOk = newConnection.readUntil(ReconnectOkMessage.class, 3000);

        assertNotNull(reconnectOk);
        assertEquals(2, reconnectOk.answers.get(questionId), "đáp án đã lưu phải được khôi phục");
        assertTrue(reconnectOk.endTimeServer > System.currentTimeMillis());
        ClientSession after = server.getSessionRegistry().findByMachineId("SV001");
        assertTrue(before == after, "phải dùng lại session cũ, không tạo session mới");
        assertEquals(loginOk.token, after.token);
        assertTrue(after.online);

        // Sau reconnect vẫn làm bài tiếp được
        newConnection.send(new AnswerMessage(2, questionId, 3));
        assertTrue(newConnection.readUntil(AnswerAckMessage.class, 3000).saved);
    }

    @Test
    void reconnectWithInvalidTokenIsRejected() throws Exception {
        startServer();
        TestClient client = connect();

        client.send(new ReconnectMessage("token-khong-ton-tai", 0));

        assertEquals("INVALID_TOKEN", ((ErrorMessage) client.read(3000)).code);
        client.send(new ReconnectMessage(null, 0));
        assertEquals("INVALID_TOKEN", ((ErrorMessage) client.read(3000)).code);
    }

    @Test
    void reconnectAfterTokenExpiredIsRejected() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("reconnect.window.seconds", "1");
        startServer(overrides, new Properties());
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = connect();
        LoginOkMessage loginOk = student.login("SV001", "123456", Role.STUDENT, code);
        student.close();
        waitUntilOffline("SV001");

        Thread.sleep(1300);
        TestClient late = connect();
        late.send(new ReconnectMessage(loginOk.token, 0));

        assertEquals("TOKEN_EXPIRED", ((ErrorMessage) late.read(3000)).code);
    }

    @Test
    void reconnectWhileOldConnectionStillLooksAliveTakesOver() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient oldConnection = connect();
        LoginOkMessage loginOk = oldConnection.login("SV001", "123456", Role.STUDENT, code);

        // Server chưa biết connection cũ đã "chết", nhưng client đã nối lại bằng connection mới.
        TestClient newConnection = connect();
        newConnection.send(new ReconnectMessage(loginOk.token, 0));
        assertNotNull(newConnection.readUntil(ReconnectOkMessage.class, 3000));

        assertTrue(oldConnection.isClosedByServer(3000), "connection cũ bị Server đóng");
        Thread.sleep(200);
        assertTrue(server.getSessionRegistry().findByMachineId("SV001").online,
                "đóng connection cũ không được đánh dấu session offline");
    }

    @Test
    void reconnectAfterExamEndedTellsStudentTheExamIsOver() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = connect();
        LoginOkMessage loginOk = student.login("SV001", "123456", Role.STUDENT, code);
        teacher.request("START_SHIFT", json("code", code));
        student.close();
        waitUntilOffline("SV001");
        teacher.request("END_SHIFT", json("code", code)); // kết thúc trong lúc sinh viên mất mạng

        TestClient newConnection = connect();
        newConnection.send(new ReconnectMessage(loginOk.token, 0));

        assertNotNull(newConnection.readUntil(ReconnectOkMessage.class, 3000));
        ExamEndMessage end = newConnection.readUntil(ExamEndMessage.class, 3000);
        assertNotNull(end);
    }

    // ------------------------------------------------------------------
    // Ca thi qua mạng
    // ------------------------------------------------------------------

    @Test
    void startingShiftSendsExamStartAndRulesToOnlineStudent() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001\nSV002", 600);
        TestClient student = loginStudent("SV001", code);

        teacher.request("START_SHIFT", json("code", code));

        ExamStartMessage start = student.readUntil(ExamStartMessage.class, 3000);
        assertEquals(6, start.questions.size());
        assertTrue(start.endTimeServer > System.currentTimeMillis());
        RulesConfigMessage rules = student.readUntil(RulesConfigMessage.class, 3000);
        assertTrue(rules.rules.processDenylist.contains("Zalo"));
        assertEquals(3, rules.rules.focusLossThreshold);
    }

    @Test
    void studentLoggingInAfterStartGetsExamImmediately() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        teacher.request("START_SHIFT", json("code", code));

        TestClient student = connect();
        student.send(new LoginMessage("SV001", "123456", Role.STUDENT, code));

        assertTrue(student.read(3000) instanceof LoginOkMessage);
        assertNotNull(student.readUntil(ExamStartMessage.class, 3000));
    }

    @Test
    void answerSubmitAndGradingOverTheNetwork() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = loginStudent("SV001", code);
        teacher.request("START_SHIFT", json("code", code));
        ExamStartMessage start = student.readUntil(ExamStartMessage.class, 3000);

        for (int i = 0; i < 3; i++) {
            student.send(new AnswerMessage(i + 1, start.questions.get(i).questionId, 1));
            AnswerAckMessage ack = student.readUntil(AnswerAckMessage.class, 3000);
            assertEquals(i + 1, ack.seq);
            assertTrue(ack.saved);
        }
        student.send(new SubmitMessage(4));

        assertEquals(3, student.readUntil(SubmitOkMessage.class, 3000).answeredCount);

        // Sau SUBMIT không sửa được đáp án
        student.send(new AnswerMessage(5, start.questions.get(0).questionId, 3));
        assertFalse(student.readUntil(AnswerAckMessage.class, 3000).saved);

        ResponseMessage results = teacher.request("LIST_RESULTS", json("code", code));
        JsonObject row = results.data.getAsJsonArray("rows").get(0).getAsJsonObject();
        assertTrue(row.get("submitted").getAsBoolean());
        assertEquals(6, row.get("totalQuestions").getAsInt());
        assertEquals(3, row.get("answeredCount").getAsInt());
    }

    @Test
    void serverEndsExamByTimerAndSendsExamEnd() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 2); // ca 2 giây
        TestClient student = loginStudent("SV001", code);
        teacher.request("START_SHIFT", json("code", code));
        ExamStartMessage start = student.readUntil(ExamStartMessage.class, 3000);
        student.send(new AnswerMessage(1, start.questions.get(0).questionId, 0));
        assertTrue(student.readUntil(AnswerAckMessage.class, 3000).saved);

        ExamEndMessage end = student.readUntil(ExamEndMessage.class, 6000);

        assertNotNull(end, "Server phải tự gửi EXAM_END khi hết giờ");
        assertEquals("TIME_UP", end.reason);
        ResponseMessage results = teacher.request("LIST_RESULTS", json("code", code));
        JsonObject row = results.data.getAsJsonArray("rows").get(0).getAsJsonObject();
        assertTrue(row.get("submitted").getAsBoolean());
        assertTrue(row.get("autoSubmitted").getAsBoolean());
    }

    @Test
    void serverSendsTimeSyncToRunningStudents() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("time.sync.interval.seconds", "1");
        startServer(overrides, new Properties());
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = loginStudent("SV001", code);
        teacher.request("START_SHIFT", json("code", code));

        TimeSyncMessage sync = student.readUntil(TimeSyncMessage.class, 4000);

        assertNotNull(sync);
        assertTrue(sync.remainingSeconds > 590 && sync.remainingSeconds <= 600);
        assertTrue(Math.abs(sync.serverTime - System.currentTimeMillis()) < 5000);
    }

    @Test
    void teacherEndingShiftNotifiesStudent() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = loginStudent("SV001", code);
        teacher.request("START_SHIFT", json("code", code));

        teacher.request("END_SHIFT", json("code", code));

        assertEquals("TEACHER_ENDED", student.readUntil(ExamEndMessage.class, 3000).reason);
    }

    @Test
    void studentLoginToEndedShiftIsRejected() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        teacher.request("START_SHIFT", json("code", code));
        teacher.request("END_SHIFT", json("code", code));
        TestClient client = connect();

        client.send(new LoginMessage("SV001", "123456", Role.STUDENT, code));

        assertTrue(((LoginFailMessage) client.read(3000)).reason.contains("kết thúc"));
    }

    // ------------------------------------------------------------------
    // Giám sát, rate control, NOTICE
    // ------------------------------------------------------------------

    @Test
    void violationFromStudentBecomesAlertForTeacherAndRaisesRate() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = loginStudent("SV001", code);
        SetRateMessage initialRate = student.readUntil(SetRateMessage.class, 3000);
        assertEquals(RateMode.NORMAL, initialRate.mode);

        student.send(new ViolationMessage(ViolationType.PROCESS_DENYLIST, "Zalo.exe", System.currentTimeMillis()));

        AlertMessage alert = teacher.readUntil(AlertMessage.class, 3000);
        assertEquals("SV001", alert.machineId);
        assertEquals(AlertLevel.RED, alert.level);
        assertTrue(alert.reason.contains("Zalo.exe"));
        SetRateMessage highRate = student.readUntil(SetRateMessage.class, 3000);
        assertEquals(RateMode.HIGH, highRate.mode);
        assertEquals(2000, highRate.intervalMs);

        ResponseMessage machines = teacher.request("LIST_MACHINES", json("code", code));
        JsonObject row = machines.data.getAsJsonArray("machines").get(0).getAsJsonObject();
        assertEquals("RED", row.get("level").getAsString());
        assertTrue(row.get("online").getAsBoolean());
        assertEquals("HIGH", row.get("rateMode").getAsString());
    }

    @Test
    void periodicChecksSendRoomStatsToStudents() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("monitor.interval.seconds", "1");
        startServer(overrides, new Properties());
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001\nSV002", 600);
        TestClient student1 = loginStudent("SV001", code);
        TestClient student2 = loginStudent("SV002", code);
        student1.send(new HeartbeatMessage(1, new Metrics(10, 0, 0, 0, 0, 0, 0, 0)));
        student2.send(new HeartbeatMessage(1, new Metrics(30, 0, 0, 0, 0, 0, 0, 0)));

        RoomStatsMessage stats = student1.readUntil(RoomStatsMessage.class, 5000);

        assertNotNull(stats);
        assertEquals(20.0, stats.medians.get("kbSent"));
    }

    @Test
    void noticeToOneMachineReachesOnlyThatMachine() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001\nSV002", 600);
        TestClient student1 = loginStudent("SV001", code);
        TestClient student2 = loginStudent("SV002", code);

        teacher.send(new NoticeMessage("SV001", "Chỉ riêng bạn"));

        assertEquals("Chỉ riêng bạn", student1.readUntil(NoticeMessage.class, 3000).text);
        assertNull(student2.readUntil(NoticeMessage.class, 700));
    }

    @Test
    void noticeToAllReachesEveryStudent() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001\nSV002", 600);
        TestClient student1 = loginStudent("SV001", code);
        TestClient student2 = loginStudent("SV002", code);

        teacher.send(new NoticeMessage("ALL", "Còn 5 phút"));

        assertEquals("Còn 5 phút", student1.readUntil(NoticeMessage.class, 3000).text);
        assertEquals("Còn 5 phút", student2.readUntil(NoticeMessage.class, 3000).text);
    }

    @Test
    void noticeToOfflineMachineReportsErrorToTeacher() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();

        teacher.send(new NoticeMessage("SV009", "alo"));

        assertEquals("MACHINE_OFFLINE", ((ErrorMessage) teacher.readUntil(ErrorMessage.class, 3000)).code);
    }

    @Test
    void baselineSwitchesStudentsToOneSecondDetailMode() throws Exception {
        startServer();
        TestClient teacher = loginTeacher();
        String code = createShift(teacher, "SV001", 600);
        TestClient student = loginStudent("SV001", code);
        student.readUntil(SetRateMessage.class, 3000); // NORMAL ban đầu

        JsonObject on = new JsonObject();
        on.addProperty("enabled", true);
        assertTrue(teacher.request("SET_BASELINE", on).ok);

        SetRateMessage rate = student.readUntil(SetRateMessage.class, 3000);
        assertEquals(RateMode.BASELINE, rate.mode);
        assertEquals(1000, rate.intervalMs);
    }

    @Test
    void manyClientsCanBeServedAtTheSameTime() throws Exception {
        startServer();

        // 40 client đăng nhập đồng thời, mỗi client trên một thread riêng
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            threads.add(Thread.startVirtualThread(() -> {
                try (TestClient client = new TestClient(server.getPort())) {
                    client.login("gv01", "teacher123", Role.TEACHER, null);
                    client.send(new HeartbeatMessage(1, new Metrics()));
                    if (client.readUntil(HeartbeatAckMessage.class, 5000) != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.out.println("client lỗi: " + e);
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join(15000);
        }

        assertEquals(40, successCount.get());
    }

    @Test
    void fiveHundredIdleConnectionsDoNotBlockNewClients() throws Exception {
        startServer();

        // Mỗi connection chiếm một virtual thread đang chờ readLine(); 500 cái không được làm Server chậm lại.
        java.util.List<java.net.Socket> idleSockets = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 500; i++) {
                idleSockets.add(new java.net.Socket("localhost", server.getPort()));
            }

            long start = System.currentTimeMillis();
            try (TestClient client = new TestClient(server.getPort())) {
                client.login("gv01", "teacher123", Role.TEACHER, null);
                client.send(new HeartbeatMessage(1, new Metrics()));
                assertNotNull(client.readUntil(HeartbeatAckMessage.class, 3000));
            }
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[Test] Với 500 connection đang mở, client mới đăng nhập + heartbeat mất " + elapsed + " ms");
            assertTrue(elapsed < 3000, "client mới bị chậm: " + elapsed + " ms");
        } finally {
            for (java.net.Socket socket : idleSockets) {
                socket.close();
            }
        }
    }

    private void waitUntilOffline(String machineId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            ClientSession session = server.getSessionRegistry().findByMachineId(machineId);
            if (session != null && !session.online) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError(machineId + " chưa được Server đánh dấu offline");
    }
}
