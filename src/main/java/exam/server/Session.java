package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Session {
    public static final String ST_LOGGED_IN = "Đã đăng nhập";
    public static final String ST_PRECHECK_FAIL = "PRECHECK lỗi";
    public static final String ST_READY = "Sẵn sàng";
    public static final String ST_IN_EXAM = "Đang thi";
    public static final String ST_SUBMITTED = "Đã nộp";
    public static final String ST_DISCONNECTED = "Mất kết nối";

    final String id;
    final String name;
    Socket socket;
    PrintWriter out;
    String os = "";

    boolean precheckOk;
    boolean precheckFailed;
    boolean submitted;
    JsonObject result;

    int violations;
    String lastViolation = "";
    String lastViolationAt = "";

    final List<JsonObject> samples = new ArrayList<>();
    Double anomalyScore;
    Double maxAnomaly;
    boolean anomaly;
    JsonArray reasons = new JsonArray();
    int flaggedCount;

    long lastSeen;

    Session(String id, String name) {
        this.id = id;
        this.name = name;
    }

    boolean connected() {
        // out == null là đang mất kết nối; session vẫn được giữ để máy vào lại đúng chỗ cũ
        return out != null;
    }

    int focusLost() {
        // feature thứ hai (chỉ số 1) của mỗi mẫu FEATURES là số lần rời cửa sổ thi
        int total = 0;
        for (JsonObject sample : samples) {
            total += sample.getAsJsonArray("values").get(1).getAsInt();
        }
        return total;
    }

    String status(boolean examStarted) {
        // suy ra từ các cờ nên không bao giờ lệch với dữ liệu thật
        if (submitted) {
            return ST_SUBMITTED;
        }
        if (!connected()) {
            return ST_DISCONNECTED;
        }
        if (precheckOk && examStarted) {
            return ST_IN_EXAM;
        }
        if (precheckOk) {
            return ST_READY;
        }
        if (precheckFailed) {
            return ST_PRECHECK_FAIL;
        }
        return ST_LOGGED_IN;
    }

    void close() {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            // đã đóng hoặc hỏng sẵn, không còn gì để làm
        }
    }
}
