// Logic thuần của bảng giám thị: tab lọc, tìm kiếm, chữ hiển thị và sự kiện đáng chú ý. Không dính tới Swing (Người 2).
package exam.server;

import exam.common.Format;
import java.text.Normalizer;
import java.util.List;
import java.util.Set;

final class Roster {
    enum Tab {
        ALL("Tất cả"), ATTENTION("Cần chú ý"), LOST("Mất kết nối"), SUBMITTED("Đã nộp");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private static final Set<String> NOTABLE = Set.of("PRECHECK_FAIL", "LOGIN_FAIL", "VIOLATION", "ANOMALY_ON", "ANOMALY_OFF",
        "DISCONNECT", "RECONNECT", "TERMINATED");

    private Roster() {}

    /** Có vi phạm hoặc bị ML gắn cờ: dòng được tô cam và đếm vào tab "Cần chú ý". */
    static boolean needsAttention(ExamRoom.Row r) { return r.violations() > 0 || r.anomaly() || r.status().equals(Session.ST_TERMINATED); }

    static boolean inTab(ExamRoom.Row r, Tab tab) {
        return switch (tab) {
            case ALL -> true;
            case ATTENTION -> needsAttention(r);
            case LOST -> r.status().equals(Session.ST_DISCONNECTED);
            case SUBMITTED -> r.status().equals(Session.ST_SUBMITTED);
        };
    }

    static int count(List<ExamRoom.Row> rows, Tab tab) { return (int) rows.stream().filter(r -> inTab(r, tab)).count(); }

    /** Tìm theo mã SV hoặc họ tên, không phân biệt hoa thường và dấu tiếng Việt. */
    static List<ExamRoom.Row> filter(List<ExamRoom.Row> rows, Tab tab, String query) {
        String q = plain(query);
        return rows.stream().filter(r -> inTab(r, tab) && (q.isEmpty() || plain(r.studentId()).contains(q)
            || plain(r.name()).contains(q))).toList();
    }

    private static String plain(String s) {
        return Normalizer.normalize(s.trim().toLowerCase().replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    static String statusText(String status) { return status.equals(Session.ST_IN_EXAM) ? "Đang làm" : status; }

    static String scoreText(Double score) { return score == null ? "-" : Format.score(score); }

    /** "Windows 11" thành "Windows", "Mac OS X" thành "macOS"; rỗng (client cũ không gửi) thành "-". */
    static String osShort(String os) {
        String o = os.toLowerCase();
        return o.startsWith("windows") ? "Windows" : o.startsWith("mac") ? "macOS" : o.startsWith("linux") ? "Linux" : os.isEmpty() ? "-" : os;
    }

    /** "NET: chatgpt.com" lúc 09:46 thành "NET chatgpt.com · 09:46". */
    static String lastViolationText(ExamRoom.Row r) {
        if (r.lastViolation().isEmpty()) { return "-"; }
        return r.lastViolation().replaceFirst(": ", " ") + (r.lastViolationAt().isEmpty() ? "" : " · " + r.lastViolationAt());
    }

    /** Khung chi tiết chỉ liệt kê sự kiện đáng chú ý; đăng nhập, PRECHECK đạt, gửi đề, nộp bài đã có ở cột trạng thái. */
    static boolean notable(ExamLog.Event e) { return NOTABLE.contains(e.type()); }

    /** Sự kiện đáng chú ý theo thứ tự thời gian; ngắt kết nối sau khi đã nộp bài là bình thường (client tự đóng) nên bỏ qua. */
    static List<ExamLog.Event> notableEvents(List<ExamLog.Event> events) {
        List<ExamLog.Event> result = new java.util.ArrayList<>();
        boolean submitted = false;
        for (ExamLog.Event e : events) {
            submitted |= e.type().equals("SUBMIT");
            if (notable(e) && !(submitted && e.type().equals("DISCONNECT"))) { result.add(e); }
        }
        return result;
    }

    /** Tên tiếng Việt của loại sự kiện cho cột "Loại" trong Lịch sử; events.csv vẫn giữ mã tiếng Anh. */
    static String typeText(String type) {
        return switch (type) {
            case "LOGIN" -> "Đăng nhập";
            case "RECONNECT" -> "Vào lại";
            case "LOGIN_FAIL" -> "Đăng nhập lỗi";
            case "PRECHECK_FAIL" -> "PRECHECK lỗi";
            case "PRECHECK_OK" -> "PRECHECK đạt";
            case "EXAM_SENT" -> "Nhận đề";
            case "VIOLATION" -> "Vi phạm";
            case "ANOMALY_ON" -> "Bất thường";
            case "ANOMALY_OFF" -> "Hết bất thường";
            case "DISCONNECT" -> "Mất kết nối";
            case "SUBMIT" -> "Nộp bài";
            case "EXAM_END" -> "Kết thúc";
            case "TERMINATED" -> "Đình chỉ";
            default -> type;
        };
    }

    /** Một dòng chữ cho khung chi tiết: loại sự kiện đổi sang tiếng Việt dễ đọc. */
    static String eventText(ExamLog.Event e) {
        return switch (e.type()) {
            case "VIOLATION" -> e.detail();
            case "ANOMALY_ON" -> e.detail();
            case "ANOMALY_OFF" -> "Hết bất thường";
            case "TERMINATED" -> "Đình chỉ: " + e.detail();
            case "DISCONNECT" -> "Mất kết nối (" + e.detail() + ")";
            case "RECONNECT" -> "Vào lại phòng thi";
            case "PRECHECK_FAIL" -> "PRECHECK lỗi: " + e.detail();
            case "LOGIN_FAIL" -> "Đăng nhập lỗi: " + e.detail();
            default -> e.type() + (e.detail().isEmpty() ? "" : " " + e.detail());
        };
    }
}
