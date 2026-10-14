// Dữ liệu của một sinh viên trong phòng thi và các chuỗi trạng thái hiển thị (Người 3). Không chứa logic.
package exam.server;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Session {
    public static final String ST_LOGGED_IN = "Đã đăng nhập", ST_PRECHECK_FAIL = "PRECHECK lỗi",
        ST_READY = "Sẵn sàng", ST_IN_EXAM = "Đang thi", ST_SUBMITTED = "Đã nộp", ST_DISCONNECTED = "Mất kết nối";

    final String id, name;
    Socket socket;
    PrintWriter out;                           // null = đang mất kết nối; session vẫn giữ nguyên để vào lại được
    boolean precheckOk, precheckFailed, submitted, anomaly;
    int violations, flaggedCount;              // flaggedCount: số lần chấm ML mà máy này bị cờ bất thường
    String lastViolation = "", lastViolationAt = "", os = "";    // lastViolationAt: giờ:phút; os: tên hệ điều hành client gửi lúc đăng nhập
    final List<JsonObject> samples = new ArrayList<>();    // các FEATURES đã nhận, gửi sang ML
    Double anomalyScore, maxAnomaly;
    JsonObject result;                         // {score, correct, total}, null nếu chưa nộp
    long lastSeen;                             // mốc nhận tin gần nhất, dùng cho watchdog

    Session(String id, String name) {
        this.id = id;
        this.name = name;
    }

    boolean connected() { return out != null; }

    /** Tổng số lần rời cửa sổ thi (feature thứ hai của mỗi FEATURES) trong cả buổi. */
    int focusLost() {
        int total = 0;
        for (JsonObject sample : samples) { total += sample.getAsJsonArray("values").get(1).getAsInt(); }
        return total;
    }

    /** Trạng thái hiển thị, suy ra từ các cờ nên không bao giờ lệch với dữ liệu thật. */
    String status(boolean examStarted) {
        if (submitted) { return ST_SUBMITTED; }
        if (!connected()) { return ST_DISCONNECTED; }
        if (precheckOk) { return examStarted ? ST_IN_EXAM : ST_READY; }
        return precheckFailed ? ST_PRECHECK_FAIL : ST_LOGGED_IN;
    }

    void close() {
        try {
            if (socket != null) { socket.close(); }
        } catch (IOException e) {
            // đã đóng hoặc hỏng sẵn, không còn gì để làm
        }
    }
}
