package exam.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudentAccountsTest {
    private static final String CSV = "studentId,name,password\nSV001,Nguyễn Văn An,123456\nSIM-X,Máy giả,abc\n";

    private final ExamRoom room = new ExamServer(new JsonObject()).room();
    private final StringWriter sent = new StringWriter();

    private Session login(String id, String password) {
        PrintWriter out = new PrintWriter(sent);
        return room.login(id, "", password, "", null, out);
    }

    private JsonObject lastReply() {
        String[] lines = sent.toString().strip().split("\n");
        return Protocol.parse(lines[lines.length - 1]);
    }

    @Test
    void loginWithWrongPasswordFails() {
        room.setStudents(StudentList.parse(CSV));
        assertNull(login("SV001", "sai"));
        JsonObject reply = lastReply();
        assertEquals(Protocol.LOGIN_FAIL, Protocol.type(reply));
        assertEquals("Sai mã sinh viên hoặc mật khẩu", Protocol.str(reply, "reason"));
        assertNull(login("KHONGCO", "123456"));
        assertEquals("Sai mã sinh viên hoặc mật khẩu", Protocol.str(lastReply(), "reason"));
        assertEquals("LOGIN_FAIL", room.history("SV001").get(0).type());
    }

    @Test
    void loginWithCorrectPasswordUsesNameFromList() {
        room.setStudents(StudentList.parse(CSV));
        Session s = login(" sv001 ", "123456");        // không phân biệt hoa thường, bỏ khoảng trắng
        assertNotNull(s);
        assertEquals("SV001", s.id);
        assertEquals("Nguyễn Văn An", s.name);
        assertEquals("Nguyễn Văn An", Protocol.str(lastReply(), "name"));
    }

    @Test
    void noListAllowsAnyone() {
        assertEquals(0, room.studentCount());
        Session s = login("BATKY", "");
        assertNotNull(s);
        assertEquals("BATKY", s.name);
        assertEquals(Protocol.LOGIN_OK, Protocol.type(lastReply()));
    }

    private List<String> rowIds() {
        List<String> ids = new ArrayList<>();
        for (ExamRoom.Row row : room.rows()) {
            ids.add(row.studentId());
        }
        return ids;
    }

    @Test
    void newListRemovesSessionsNotInIt() {
        List<String> log = new ArrayList<>();
        ExamServer server = new ExamServer(new JsonObject());
        server.setListener(log::add);
        ExamRoom own = server.room();
        for (String id : List.of("SIM01", "SIM02", "SV001")) {
            own.login(id, "", "", "", null, new PrintWriter(new StringWriter()));
        }
        own.setStudents(StudentList.parse("studentId,name,password\nSIM02,Hai,x\nSV001,An,1\n"));
        List<String> ids = new ArrayList<>();
        for (ExamRoom.Row row : own.rows()) {
            ids.add(row.studentId());
        }
        assertEquals(List.of("SIM02", "SV001"), ids);
        assertTrue(log.stream().anyMatch(l -> l.contains("Đã xoá khỏi phòng: SIM01 (không có trong danh sách)")), log.toString());
    }

    @Test
    void emptyListDoesNotRemoveAnyone() {
        login("SIM01", "");
        room.setStudents(null);
        assertEquals(List.of("SIM01"), rowIds());
    }

    @Test
    void listCanOnlyBeLoadedBeforeStartOrAfterNewSession(@TempDir Path tmp) throws IOException {
        Path exam = tmp.resolve("exam.json");
        Files.writeString(exam, "{\"title\":\"De\",\"durationMinutes\":1,\"questions\":["
            + "{\"id\":1,\"text\":\"Q\",\"options\":[\"a\",\"b\"],\"answer\":1}]}");
        room.setResultsRoot(tmp.resolve("ket-qua"));
        room.loadExam(exam);
        room.setStudents(StudentList.parse(CSV));
        room.startExam();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> room.setStudents(StudentList.parse(CSV)));
        assertEquals("Chỉ nạp danh sách khi chưa bắt đầu thi", e.getMessage());
        room.endExam("test");
        room.newSession();
        room.setStudents(StudentList.parse(CSV));
        assertEquals(2, room.studentCount());
    }

    @Test
    void badCsvLineReportsLineNumber() {
        String csv = "studentId,name,password\nSV001,An,123456\nSV002,Bình\nSV003,,123456\nsv001,Trùng,1\n";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> StudentList.parse(csv));
        List<String> lines = List.of(e.getMessage().split("\n"));
        assertTrue(lines.get(0).startsWith("Dòng 3:"), e.getMessage());
        assertTrue(lines.get(1).startsWith("Dòng 4:") && lines.get(1).contains("họ tên"), e.getMessage());
        assertTrue(lines.get(2).startsWith("Dòng 5:") && lines.get(2).contains("dòng 2"), e.getMessage());
        assertEquals(2, StudentList.parse("﻿" + CSV).size(), "chấp nhận file có BOM");
    }
}
