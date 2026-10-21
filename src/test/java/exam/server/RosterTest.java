// Test phần logic thuần của bảng giám thị (lọc theo tab, tìm kiếm, chữ hiển thị); không mở cửa sổ (Người 2).
package exam.server;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RosterTest {
    private static ExamRoom.Row row(String id, String name, String status, int violations, boolean anomaly, Double score) {
        return new ExamRoom.Row(id, name, status, violations, violations > 0 ? "NET: chatgpt.com" : "", anomaly ? 0.9 : 0.1,
            anomaly, score, 3, "Windows 11", 0, violations > 0 ? "09:46" : "");
    }

    private final List<ExamRoom.Row> rows = List.of(
        row("B21DCCN101", "Phạm Quốc Anh", Session.ST_IN_EXAM, 0, false, null),
        row("B21DCCN104", "Lê Minh Châu", Session.ST_IN_EXAM, 2, false, null),
        row("B21DCCN135", "Hoàng Lan", Session.ST_IN_EXAM, 0, true, null),
        row("B21DCCN123", "Nguyễn Văn An", Session.ST_DISCONNECTED, 0, false, null),
        row("B21DCCN130", "Vũ Đức Huy", Session.ST_SUBMITTED, 0, false, 7.5));

    @Test
    void tabsCountAndFilterRows() {
        assertEquals(5, Roster.count(rows, Roster.Tab.ALL));
        assertEquals(2, Roster.count(rows, Roster.Tab.ATTENTION));      // một máy có vi phạm, một máy bị gắn cờ ML
        assertEquals(1, Roster.count(rows, Roster.Tab.LOST));
        assertEquals(1, Roster.count(rows, Roster.Tab.SUBMITTED));
        assertEquals(List.of("B21DCCN104", "B21DCCN135"),
            Roster.filter(rows, Roster.Tab.ATTENTION, "").stream().map(ExamRoom.Row::studentId).toList());
    }

    @Test
    void searchMatchesIdOrNameWithoutCaseOrAccents() {
        assertEquals(List.of("B21DCCN104"), ids(Roster.filter(rows, Roster.Tab.ALL, "chau")));
        assertEquals(List.of("B21DCCN104"), ids(Roster.filter(rows, Roster.Tab.ALL, "LÊ MINH")));
        assertEquals(List.of("B21DCCN130"), ids(Roster.filter(rows, Roster.Tab.ALL, "ccn130")));
        assertEquals(List.of("B21DCCN135"), ids(Roster.filter(rows, Roster.Tab.ATTENTION, "lan")));
        assertTrue(Roster.filter(rows, Roster.Tab.LOST, "lan").isEmpty(), "Tab và tìm kiếm phải cùng áp dụng");
    }

    private static List<String> ids(List<ExamRoom.Row> list) { return list.stream().map(ExamRoom.Row::studentId).toList(); }

    @Test
    void displayTexts() {
        assertEquals("Đang làm", Roster.statusText(Session.ST_IN_EXAM));
        assertEquals("Mất kết nối", Roster.statusText(Session.ST_DISCONNECTED));
        assertEquals("Đã nộp", Roster.statusText(Session.ST_SUBMITTED));
        assertEquals("7,5", Roster.scoreText(7.5));
        assertEquals("9,0", Roster.scoreText(9.0));
        assertEquals("3,33", Roster.scoreText(3.33));
        assertEquals("-", Roster.scoreText(null));
        assertEquals("Windows", Roster.osShort("Windows 11"));
        assertEquals("macOS", Roster.osShort("Mac OS X"));
        assertEquals("Linux", Roster.osShort("Linux"));
        assertEquals("-", Roster.osShort(""));
        assertEquals("NET chatgpt.com · 09:46", Roster.lastViolationText(rows.get(1)));
        assertEquals("-", Roster.lastViolationText(rows.get(0)));
    }

    @Test
    void detailPanelShowsOnlyNotableEvents() {
        LocalDateTime t = LocalDateTime.now();
        assertFalse(Roster.notable(new ExamLog.Event(t, "S1", "An", "LOGIN", "")));
        assertFalse(Roster.notable(new ExamLog.Event(t, "S1", "An", "PRECHECK_OK", "")));
        assertFalse(Roster.notable(new ExamLog.Event(t, "S1", "An", "EXAM_SENT", "còn 60 giây")));
        assertTrue(Roster.notable(new ExamLog.Event(t, "S1", "An", "VIOLATION", "NET: x")));
        assertTrue(Roster.notable(new ExamLog.Event(t, "S1", "An", "ANOMALY_ON", "0.90")));
        assertTrue(Roster.notable(new ExamLog.Event(t, "S1", "An", "DISCONNECT", "socket đóng")));
        assertTrue(Roster.notable(new ExamLog.Event(t, "S1", "An", "PRECHECK_FAIL", "USB")));
    }

    @Test
    void disconnectAfterSubmitIsNotNotable() {
        LocalDateTime t = LocalDateTime.now();
        List<ExamLog.Event> events = List.of(
            new ExamLog.Event(t, "S1", "An", "DISCONNECT", "mạng"), new ExamLog.Event(t, "S1", "An", "RECONNECT", ""),
            new ExamLog.Event(t, "S1", "An", "SUBMIT", "điểm 10"), new ExamLog.Event(t, "S1", "An", "DISCONNECT", "socket đóng"));
        assertEquals(List.of("DISCONNECT", "RECONNECT"), Roster.notableEvents(events).stream().map(ExamLog.Event::type).toList());
    }

    @Test
    void eventTextIsReadableVietnamese() {
        LocalDateTime t = LocalDateTime.now();
        assertEquals("NET: chatgpt.com", Roster.eventText(new ExamLog.Event(t, "S1", "An", "VIOLATION", "NET: chatgpt.com")));
        assertEquals("Mức bất thường: Rất cao (0.91).", Roster.eventText(new ExamLog.Event(t, "S1", "An", "ANOMALY_ON", "Mức bất thường: Rất cao (0.91).")));
        assertEquals("Hết bất thường", Roster.eventText(new ExamLog.Event(t, "S1", "An", "ANOMALY_OFF", "0.20")));
        assertEquals("Mất kết nối (socket đóng)", Roster.eventText(new ExamLog.Event(t, "S1", "An", "DISCONNECT", "socket đóng")));
        assertEquals("Vào lại phòng thi", Roster.eventText(new ExamLog.Event(t, "S1", "An", "RECONNECT", "")));
    }

    @Test
    void historyTypesAreInVietnamese() {
        assertEquals("Đăng nhập", Roster.typeText("LOGIN"));
        assertEquals("Vào lại", Roster.typeText("RECONNECT"));
        assertEquals("Đăng nhập lỗi", Roster.typeText("LOGIN_FAIL"));
        assertEquals("PRECHECK lỗi", Roster.typeText("PRECHECK_FAIL"));
        assertEquals("PRECHECK đạt", Roster.typeText("PRECHECK_OK"));
        assertEquals("Nhận đề", Roster.typeText("EXAM_SENT"));
        assertEquals("Vi phạm", Roster.typeText("VIOLATION"));
        assertEquals("Bất thường", Roster.typeText("ANOMALY_ON"));
        assertEquals("Hết bất thường", Roster.typeText("ANOMALY_OFF"));
        assertEquals("Mất kết nối", Roster.typeText("DISCONNECT"));
        assertEquals("Nộp bài", Roster.typeText("SUBMIT"));
        assertEquals("Kết thúc", Roster.typeText("EXAM_END"));
    }
}
