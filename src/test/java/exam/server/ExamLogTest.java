// Test ghi sự kiện ở mức ExamRoom, không cần mạng: điều khiển phòng trực tiếp bằng PrintWriter giả (Người 3).
package exam.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.TestStudents;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExamLogTest {
    @TempDir Path tmp;

    private ExamServer serverWithExam() throws IOException {
        ExamServer s = new ExamServer(new JsonObject());
        s.room().setStudents(TestStudents.of("S1"));
        s.room().setResultsRoot(tmp.resolve("ket-qua"));
        Path exam = tmp.resolve("exam.json");
        Files.writeString(exam, "{\"title\":\"De thu\",\"durationMinutes\":1,\"questions\":["
            + "{\"id\":1,\"text\":\"Q1\",\"options\":[\"a\",\"b\"],\"answer\":1}]}", StandardCharsets.UTF_8);
        s.room().loadExam(exam);
        return s;
    }

    private static JsonObject scores(String id, double score, boolean anomaly) {
        JsonObject r = new JsonObject();
        r.addProperty("studentId", id);
        r.addProperty("score", score);
        r.addProperty("anomaly", anomaly);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        arr.add(r);
        JsonObject resp = new JsonObject();
        resp.addProperty("status", "ok");
        resp.add("results", arr);
        return resp;
    }

    @Test
    void anomalyNeverTerminates() throws IOException {
        ExamServer s = serverWithExam();
        ExamRoom room = s.room();
        JsonObject rules = room.rules();
        rules.addProperty("maxViolations", 1);
        room.updateRules(rules);
        room.login("S1", "An", TestStudents.PASSWORD, "", null, new PrintWriter(new StringWriter()));
        room.startExam();
        room.applyScores(scores("S1", 1.0, true));
        ExamRoom.Row row = room.rows().get(0);
        assertTrue(row.anomaly());
        assertNotEquals(Session.ST_TERMINATED, row.status());
        assertNull(row.score(), "ML chỉ cảnh báo, không cho điểm 0");
        assertFalse(room.history("S1").stream().anyMatch(e -> e.type().equals("TERMINATED")));
    }

    @Test
    void anomalyOnDetailListsReasons() throws IOException {
        ExamServer s = serverWithExam();
        ExamRoom room = s.room();
        room.login("S1", "An", TestStudents.PASSWORD, "", null, new PrintWriter(new StringWriter()));
        JsonObject resp = scores("S1", 1.0, true);
        JsonObject reason = new JsonObject();
        reason.addProperty("feature", 1);
        reason.addProperty("value", 6.0);
        reason.addProperty("room", 0.3);
        reason.addProperty("total", 18.0);
        reason.addProperty("room_total", 0.9);
        reason.addProperty("windows", 3);
        JsonArray reasons = new JsonArray();
        reasons.add(reason);
        resp.getAsJsonArray("results").get(0).getAsJsonObject().add("reasons", reasons);
        room.applyScores(resp);
        ExamLog.Event on = room.history("S1").get(1);
        assertEquals("ANOMALY_ON", on.type());
        assertEquals("Mức bất thường: Rất cao (1.00). Trong 1 phút 30 giây gần nhất, máy này: "
            + "rời khỏi bài thi 18 lần (các máy khác thường 1 lần)", on.detail());
    }

    @Test
    void anomalyOnAndOffAreRecorded() throws IOException {
        ExamServer s = serverWithExam();
        ExamRoom room = s.room();
        room.login("S1", "An", TestStudents.PASSWORD, "", null, new PrintWriter(new StringWriter()));
        room.applyScores(scores("S1", 0.9, true));
        room.applyScores(scores("S1", 0.9, true));     // vẫn bị cờ: không ghi lại lần hai
        room.applyScores(scores("S1", 0.2, false));
        List<String> types = room.history("S1").stream().map(ExamLog.Event::type).toList();
        assertEquals(List.of("LOGIN", "ANOMALY_ON", "ANOMALY_OFF"), types);
    }

    @Test
    void reconnectAndDisconnectAreRecorded() throws IOException {
        ExamServer s = serverWithExam();
        ExamRoom room = s.room();
        java.net.Socket sock = new java.net.Socket();
        Session a = room.login("S1", "An", TestStudents.PASSWORD, "", sock, new PrintWriter(new StringWriter()));
        room.disconnected(a, sock);
        room.login("S1", "An", TestStudents.PASSWORD, "", null, new PrintWriter(new StringWriter()));
        List<String> types = room.history("S1").stream().map(ExamLog.Event::type).toList();
        assertEquals(List.of("LOGIN", "DISCONNECT", "RECONNECT"), types);
    }

    @Test
    void loginFailIsRecordedWithReason() throws IOException {
        ExamServer s = serverWithExam();
        s.room().login("", "An", "", "", null, new PrintWriter(new StringWriter()));
        ExamLog.Event e = s.room().history("").get(0);
        assertEquals("LOGIN_FAIL", e.type());
        assertTrue(e.detail().contains("Thiếu mã SV"), e.detail());
    }

    @Test
    void staleScoresAreIgnoredAfterNewSession() throws IOException {
        ExamServer s = serverWithExam();
        ExamRoom room = s.room();
        room.login("S1", "An", TestStudents.PASSWORD, "", null, new PrintWriter(new StringWriter()));
        room.startExam();
        int epoch = room.epoch();                      // lần chấm ML bắt đầu ở kỳ cũ
        room.endExam("test");
        room.newSession();
        room.login("S1", "An", TestStudents.PASSWORD, "", null, new PrintWriter(new StringWriter()));
        room.applyScores(scores("S1", 0.9, true), epoch);   // trả về muộn, sau khi đã sang kỳ mới
        assertNull(room.rows().get(0).anomalyScore());
        assertEquals(List.of("LOGIN"), room.history("S1").stream().map(ExamLog.Event::type).toList());
    }
}
