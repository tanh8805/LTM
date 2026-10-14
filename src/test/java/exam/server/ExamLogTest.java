// Test ghi sự kiện ở mức ExamRoom, không cần mạng: điều khiển phòng trực tiếp bằng PrintWriter giả (Người 3).
package exam.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
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
    void anomalyOnAndOffAreRecorded() throws IOException {
        ExamServer s = serverWithExam();
        ExamRoom room = s.room();
        room.login("S1", "An", null, new PrintWriter(new StringWriter()));
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
        Session a = room.login("S1", "An", sock, new PrintWriter(new StringWriter()));
        room.disconnected(a, sock);
        room.login("S1", "An", null, new PrintWriter(new StringWriter()));
        List<String> types = room.history("S1").stream().map(ExamLog.Event::type).toList();
        assertEquals(List.of("LOGIN", "DISCONNECT", "RECONNECT"), types);
    }

    @Test
    void loginFailIsRecordedWithReason() throws IOException {
        ExamServer s = serverWithExam();
        s.room().login("", "An", null, new PrintWriter(new StringWriter()));
        ExamLog.Event e = s.room().history("").get(0);
        assertEquals("LOGIN_FAIL", e.type());
        assertTrue(e.detail().contains("Thiếu mã SV"), e.detail());
    }
}
