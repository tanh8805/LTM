// Test lưu hồ sơ kỳ thi: events.csv, results.csv, server.log nằm trong một thư mục riêng cho mỗi kỳ thi (Người 3).
package exam;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import exam.server.ExamLog;
import exam.server.ExamServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExamRecordsTest {
    @TempDir Path tmp;
    final List<ExamServer> servers = new ArrayList<>();
    final List<ExamServerTest.TestClient> clients = new ArrayList<>();
    ExamServer server;
    Path root;

    @BeforeEach
    void setUp() throws IOException {
        root = tmp.resolve("ket-qua");
        server = newServer("De thu");
    }

    @AfterEach
    void tearDown() throws IOException {
        for (ExamServerTest.TestClient c : clients) { c.close(); }
        for (ExamServer s : servers) { s.stop(); }
    }

    private ExamServer newServer(String title) throws IOException {
        ExamServer s = new ExamServer(new JsonObject());
        s.room().setStudents(TestStudents.of("S1", "S2"));
        s.room().setResultsRoot(root);
        Path exam = tmp.resolve(title.hashCode() + ".json");
        Files.writeString(exam, ExamServerTest.EXAM_JSON.replace("De thu", title), StandardCharsets.UTF_8);
        s.room().loadExam(exam);
        s.start(0);
        servers.add(s);
        return s;
    }

    private ExamServerTest.TestClient login(ExamServer s, String id) throws IOException {
        ExamServerTest.TestClient c = new ExamServerTest.TestClient(s.tcpPort());
        clients.add(c);
        JsonObject m = Protocol.msg(Protocol.LOGIN);
        m.addProperty("studentId", id);
        m.addProperty("password", TestStudents.PASSWORD);
        c.send(m);
        c.next(Protocol.LOGIN_OK, 2000);
        return c;
    }

    private void precheck(ExamServerTest.TestClient c, boolean ok, String... issues) {
        JsonObject m = Protocol.msg(Protocol.PRECHECK_RESULT);
        m.addProperty("ok", ok);
        JsonArray arr = new JsonArray();
        for (String i : issues) { arr.add(i); }
        m.add("issues", arr);
        c.send(m);
    }

    private static void waitFor(BooleanSupplier cond) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) { Thread.sleep(20); }
    }

    private String events(ExamServer s) throws IOException {
        return Files.readString(s.room().examDir().resolve("events.csv"), StandardCharsets.UTF_8);
    }

    @Test
    void violationIsWrittenToEventsFile() throws Exception {
        ExamServerTest.TestClient c = login(server, "S1");
        precheck(c, true);
        server.room().startExam();
        c.next(Protocol.EXAM_START, 2000);
        JsonObject v = Protocol.msg(Protocol.VIOLATION);
        v.addProperty("kind", "APP");
        v.addProperty("detail", "Đang mở ứng dụng bị cấm: Zalo");
        c.send(v);
        waitFor(() -> server.room().history("S1").stream().anyMatch(e -> e.type().equals("VIOLATION")));
        String csv = events(server);
        assertTrue(csv.startsWith("﻿time,studentId,name,type,detail\n"), csv);
        assertTrue(csv.contains(",S1,Sinh viên S1,VIOLATION,APP: Đang mở ứng dụng bị cấm: Zalo"), csv);
    }

    @Test
    void eventsBeforeStartAreKept() throws Exception {
        ExamServerTest.TestClient a = login(server, "S1");
        precheck(a, false, "Đang cắm USB: E:", "Đang mở ứng dụng bị cấm: Zalo");
        ExamServerTest.TestClient b = login(server, "S2");
        precheck(b, true);
        waitFor(() -> server.room().history("S2").size() >= 2);
        assertNull(server.room().examDir(), "Chưa bắt đầu thì chưa có thư mục");
        List<String> types = server.room().history("S1").stream().map(ExamLog.Event::type).toList();
        assertEquals(List.of("LOGIN", "PRECHECK_FAIL"), types);
        server.room().startExam();
        String csv = events(server);
        assertTrue(csv.contains(",S1,Sinh viên S1,LOGIN,"), csv);
        assertTrue(csv.contains("PRECHECK_FAIL,"), csv);
        assertTrue(csv.contains("Đang cắm USB: E:") && csv.contains("Zalo"), csv);
        assertTrue(csv.contains(",S2,Sinh viên S2,PRECHECK_OK,"), csv);
        assertTrue(csv.contains(",S2,Sinh viên S2,EXAM_SENT,"), csv);
        assertTrue(Files.readString(server.room().examDir().resolve("server.log"), StandardCharsets.UTF_8)
            .contains("Đăng nhập: S1"), "server.log cũng phải có dòng log trước giờ thi");
    }

    @Test
    void historyForStudentListsOnlyThatStudent() throws Exception {
        ExamServerTest.TestClient a = login(server, "S1");
        ExamServerTest.TestClient b = login(server, "S2");
        precheck(a, true);
        precheck(b, true);
        waitFor(() -> server.room().history("S2").size() >= 2);
        server.room().startExam();
        a.next(Protocol.EXAM_START, 2000);
        JsonObject v = Protocol.msg(Protocol.VIOLATION);
        v.addProperty("kind", "USB");
        v.addProperty("detail", "E:");
        b.send(v);
        waitFor(() -> server.room().history("S2").size() >= 4);
        List<ExamLog.Event> h = server.room().history("S1");
        assertFalse(h.isEmpty());
        assertTrue(h.stream().allMatch(e -> e.studentId().equals("S1")), h.toString());
        assertTrue(h.stream().noneMatch(e -> e.type().equals("VIOLATION")));
        assertTrue(server.room().history("S2").stream().anyMatch(e -> e.type().equals("VIOLATION")));
    }

    @Test
    void eachExamGetsOwnFolder() throws Exception {
        ExamServer other = newServer("Đề khác");
        server.room().startExam();
        other.room().startExam();
        Path d1 = server.room().examDir(), d2 = other.room().examDir();
        assertNotEquals(d1, d2);
        assertEquals(root, d1.getParent());
        assertTrue(d1.getFileName().toString().matches("\\d{8}-\\d{6}_De_thu(-\\d+)?"), d1.toString());
        assertTrue(d2.getFileName().toString().matches("\\d{8}-\\d{6}_De_khac(-\\d+)?"), d2.toString());
        for (Path d : List.of(d1, d2)) {
            assertTrue(Files.exists(d.resolve("events.csv")));
            assertTrue(Files.exists(d.resolve("server.log")));
        }
        server.room().endExam("test");
        assertTrue(Files.exists(d1.resolve("results.csv")));
        try (Stream<Path> top = Files.list(root)) {
            assertEquals(2, top.count(), "Thư mục gốc chỉ chứa hai thư mục kỳ thi, không có results.csv lẻ");
        }
    }

    @Test
    void folderNameStripsAccents() {
        assertEquals("20261014-090507_Kiem_tra_giua_ky_Lap_trinh_mang_D1", ExamLog.folderName(
            java.time.LocalDateTime.of(2026, 10, 14, 9, 5, 7), "Kiểm tra giữa kỳ - Lập trình mạng (Đ1)"));
    }
}
