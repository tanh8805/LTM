// Owner: Nguoi1

package exam.common.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import exam.common.model.AlertLevel;
import exam.common.model.Metrics;
import exam.common.model.MlMode;
import exam.common.model.MonitoringRules;
import exam.common.model.QuestionView;
import exam.common.model.RateMode;
import exam.common.model.Role;
import exam.common.model.ViolationType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Với MỖI message: tạo object -> encode -> decode -> kiểm tra.
 * Mỗi test đọc từ trên xuống dưới là hiểu, không có logic ẩn.
 */
class MessageCodecTest {

    private final MessageCodec codec = new MessageCodec();

    /**
     * encode rồi decode, kiểm tra:
     *  - ra một dòng duy nhất (không có xuống dòng bên trong)
     *  - decode ra đúng class
     *  - field "type" đúng
     *  - encode lại cho đúng dòng JSON ban đầu (nghĩa là KHÔNG mất field nào)
     */
    private <T extends Message> T encodeThenDecode(T original, String expectedType, Class<T> expectedClass) {
        String jsonLine = codec.encode(original);

        assertFalse(jsonLine.contains("\n"), "Message phải nằm trên một dòng");

        Message decoded = codec.decode(jsonLine);
        assertInstanceOf(expectedClass, decoded);
        assertEquals(expectedType, decoded.type);
        assertEquals(jsonLine, codec.encode(decoded));

        return expectedClass.cast(decoded);
    }

    private Metrics sampleMetrics() {
        return new Metrics(12.5, 30.0, 8, 4, 35.5, 60.0, 120, 2);
    }

    @Test
    void login() {
        LoginMessage original = new LoginMessage("SV001", "123456", Role.STUDENT, "CA001");
        LoginMessage decoded = encodeThenDecode(original, "LOGIN", LoginMessage.class);

        assertEquals("SV001", decoded.username);
        assertEquals("123456", decoded.password);
        assertEquals(Role.STUDENT, decoded.role);
        assertEquals("CA001", decoded.examCode);
    }

    @Test
    void loginOk() {
        LoginOkMessage original = new LoginOkMessage("token-abc", 7, "Nguyen Van An", Role.STUDENT, 1000L);
        LoginOkMessage decoded = encodeThenDecode(original, "LOGIN_OK", LoginOkMessage.class);

        assertEquals("token-abc", decoded.token);
        assertEquals(7, decoded.userId);
        assertEquals("Nguyen Van An", decoded.fullName);
        assertEquals(Role.STUDENT, decoded.role);
        assertEquals(1000L, decoded.serverTime);
    }

    @Test
    void loginFail() {
        LoginFailMessage original = new LoginFailMessage("Sai mật khẩu");
        LoginFailMessage decoded = encodeThenDecode(original, "LOGIN_FAIL", LoginFailMessage.class);

        assertEquals("Sai mật khẩu", decoded.reason);
    }

    @Test
    void reconnect() {
        ReconnectMessage original = new ReconnectMessage("token-abc", 5);
        ReconnectMessage decoded = encodeThenDecode(original, "RECONNECT", ReconnectMessage.class);

        assertEquals("token-abc", decoded.token);
        assertEquals(5, decoded.lastAnswerSeq);
    }

    @Test
    void reconnectOk() {
        Map<Integer, Integer> answers = new HashMap<>();
        answers.put(1, 2);
        answers.put(5, 0);
        ReconnectOkMessage original = new ReconnectOkMessage(9_000_000L, answers, 2000L);
        ReconnectOkMessage decoded = encodeThenDecode(original, "RECONNECT_OK", ReconnectOkMessage.class);

        assertEquals(9_000_000L, decoded.endTimeServer);
        assertEquals(2, decoded.answers.size());
        assertEquals(2, decoded.answers.get(1));
        assertEquals(0, decoded.answers.get(5));
        assertEquals(2000L, decoded.serverTime);
    }

    @Test
    void heartbeat() {
        HeartbeatMessage original = new HeartbeatMessage(3, sampleMetrics());
        HeartbeatMessage decoded = encodeThenDecode(original, "HEARTBEAT", HeartbeatMessage.class);

        assertEquals(3, decoded.seq);
        assertEquals(12.5, decoded.summary.kbSent);
        assertEquals(2, decoded.summary.focusLostCount);
    }

    @Test
    void heartbeatAck() {
        HeartbeatAckMessage original = new HeartbeatAckMessage(3, 3000L);
        HeartbeatAckMessage decoded = encodeThenDecode(original, "HEARTBEAT_ACK", HeartbeatAckMessage.class);

        assertEquals(3, decoded.seq);
        assertEquals(3000L, decoded.serverTime);
    }

    @Test
    void request() {
        JsonObject data = new JsonObject();
        data.addProperty("examCode", "CA001");
        RequestMessage original = new RequestMessage("req-1", "LIST_RESULTS", data);
        RequestMessage decoded = encodeThenDecode(original, "REQUEST", RequestMessage.class);

        assertEquals("req-1", decoded.requestId);
        assertEquals("LIST_RESULTS", decoded.action);
        assertEquals("CA001", decoded.data.get("examCode").getAsString());
    }

    @Test
    void response() {
        JsonObject data = new JsonObject();
        data.addProperty("count", 30);
        ResponseMessage original = new ResponseMessage("req-1", true, data, null);
        ResponseMessage decoded = encodeThenDecode(original, "RESPONSE", ResponseMessage.class);

        assertEquals("req-1", decoded.requestId);
        assertTrue(decoded.ok);
        assertEquals(30, decoded.data.get("count").getAsInt());
        assertEquals(null, decoded.error);
    }

    @Test
    void examStart() {
        QuestionView question = new QuestionView(11, "Thủ đô của Việt Nam?", List.of("Hà Nội", "Huế", "Đà Nẵng", "Cần Thơ"));
        ExamStartMessage original = new ExamStartMessage(List.of(question), 9_000_000L);
        ExamStartMessage decoded = encodeThenDecode(original, "EXAM_START", ExamStartMessage.class);

        assertEquals(9_000_000L, decoded.endTimeServer);
        assertEquals(1, decoded.questions.size());
        assertEquals(11, decoded.questions.get(0).questionId);
        assertEquals("Thủ đô của Việt Nam?", decoded.questions.get(0).content);
        assertEquals(4, decoded.questions.get(0).options.size());
        assertEquals("Huế", decoded.questions.get(0).options.get(1));
    }

    @Test
    void timeSync() {
        TimeSyncMessage original = new TimeSyncMessage(4000L, 1500L);
        TimeSyncMessage decoded = encodeThenDecode(original, "TIME_SYNC", TimeSyncMessage.class);

        assertEquals(4000L, decoded.serverTime);
        assertEquals(1500L, decoded.remainingSeconds);
    }

    @Test
    void answer() {
        AnswerMessage original = new AnswerMessage(6, 11, 2);
        AnswerMessage decoded = encodeThenDecode(original, "ANSWER", AnswerMessage.class);

        assertEquals(6, decoded.seq);
        assertEquals(11, decoded.questionId);
        assertEquals(2, decoded.choice);
    }

    @Test
    void answerAck() {
        AnswerAckMessage original = new AnswerAckMessage(6, 11, true);
        AnswerAckMessage decoded = encodeThenDecode(original, "ANSWER_ACK", AnswerAckMessage.class);

        assertEquals(6, decoded.seq);
        assertEquals(11, decoded.questionId);
        assertTrue(decoded.saved);
    }

    @Test
    void submit() {
        SubmitMessage original = new SubmitMessage(7);
        SubmitMessage decoded = encodeThenDecode(original, "SUBMIT", SubmitMessage.class);

        assertEquals(7, decoded.seq);
    }

    @Test
    void submitOk() {
        SubmitOkMessage original = new SubmitOkMessage(28);
        SubmitOkMessage decoded = encodeThenDecode(original, "SUBMIT_OK", SubmitOkMessage.class);

        assertEquals(28, decoded.answeredCount);
    }

    @Test
    void examEnd() {
        ExamEndMessage original = new ExamEndMessage("TIME_UP");
        ExamEndMessage decoded = encodeThenDecode(original, "EXAM_END", ExamEndMessage.class);

        assertEquals("TIME_UP", decoded.reason);
    }

    @Test
    void rulesConfig() {
        RulesConfigMessage original = new RulesConfigMessage(MonitoringRules.createDefault(), MlMode.BOTH_OR);
        RulesConfigMessage decoded = encodeThenDecode(original, "RULES_CONFIG", RulesConfigMessage.class);

        assertEquals(MlMode.BOTH_OR, decoded.mlMode);
        assertEquals(3, decoded.rules.focusLossThreshold);
        assertTrue(decoded.rules.processDenylist.contains("Zalo"));
        assertTrue(decoded.rules.blockedDomains.contains("chatgpt.com"));
    }

    @Test
    void metricsDetail() {
        MetricsDetailMessage original = new MetricsDetailMessage(
                12, 5000L, sampleMetrics(), List.of("java", "chrome"), List.of("1.2.3.4:443"));
        MetricsDetailMessage decoded = encodeThenDecode(original, "METRICS_DETAIL", MetricsDetailMessage.class);

        assertEquals(12, decoded.seq);
        assertEquals(5000L, decoded.time);
        assertEquals(35.5, decoded.metrics.cpuPercent);
        assertEquals(List.of("java", "chrome"), decoded.processNames);
        assertEquals(List.of("1.2.3.4:443"), decoded.remoteAddresses);
    }

    @Test
    void violation() {
        ViolationMessage original = new ViolationMessage(ViolationType.PROCESS_DENYLIST, "Zalo.exe", 6000L);
        ViolationMessage decoded = encodeThenDecode(original, "VIOLATION", ViolationMessage.class);

        assertEquals(ViolationType.PROCESS_DENYLIST, decoded.violationType);
        assertEquals("Zalo.exe", decoded.evidence);
        assertEquals(6000L, decoded.time);
    }

    @Test
    void setRate() {
        SetRateMessage original = new SetRateMessage(RateMode.HIGH, 2000);
        SetRateMessage decoded = encodeThenDecode(original, "SET_RATE", SetRateMessage.class);

        assertEquals(RateMode.HIGH, decoded.mode);
        assertEquals(2000, decoded.intervalMs);
    }

    @Test
    void roomStats() {
        Map<String, Double> medians = new HashMap<>();
        medians.put("kbSent", 10.0);
        Map<String, Double> mads = new HashMap<>();
        mads.put("kbSent", 2.5);
        RoomStatsMessage original = new RoomStatsMessage(7000L, medians, mads);
        RoomStatsMessage decoded = encodeThenDecode(original, "ROOM_STATS", RoomStatsMessage.class);

        assertEquals(7000L, decoded.time);
        assertEquals(10.0, decoded.medians.get("kbSent"));
        assertEquals(2.5, decoded.mads.get("kbSent"));
    }

    @Test
    void alert() {
        AlertMessage original = new AlertMessage("SV003", AlertLevel.YELLOW, "kbSent gấp 5.2 lần trung vị phòng");
        AlertMessage decoded = encodeThenDecode(original, "ALERT", AlertMessage.class);

        assertEquals("SV003", decoded.machineId);
        assertEquals(AlertLevel.YELLOW, decoded.level);
        assertEquals("kbSent gấp 5.2 lần trung vị phòng", decoded.reason);
    }

    @Test
    void notice() {
        NoticeMessage original = new NoticeMessage("ALL", "Còn 5 phút");
        NoticeMessage decoded = encodeThenDecode(original, "NOTICE", NoticeMessage.class);

        assertEquals("ALL", decoded.target);
        assertEquals("Còn 5 phút", decoded.text);
    }

    @Test
    void error() {
        ErrorMessage original = new ErrorMessage("BAD_MESSAGE", "Message sai định dạng");
        ErrorMessage decoded = encodeThenDecode(original, "ERROR", ErrorMessage.class);

        assertEquals("BAD_MESSAGE", decoded.code);
        assertEquals("Message sai định dạng", decoded.message);
    }

    @Test
    void encodePutsTypeFirstSoLogsAreEasyToRead() {
        String jsonLine = codec.encode(new HeartbeatAckMessage(3, 3000L));

        assertEquals("{\"type\":\"HEARTBEAT_ACK\",\"seq\":3,\"serverTime\":3000}", jsonLine);
    }

    // ----- Các trường hợp lỗi: decode phải báo lỗi rõ ràng thay vì trả về null -----

    @Test
    void decodeRejectsUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("{\"type\":\"HELLO\"}"));
    }

    @Test
    void decodeRejectsMissingType() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("{\"seq\":1}"));
    }

    @Test
    void decodeRejectsNotJson() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("hello world"));
    }
}
