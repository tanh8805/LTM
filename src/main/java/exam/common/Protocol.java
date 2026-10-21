// Hằng số và hàm tiện ích dùng chung cho giao thức: mỗi tin nhắn là một dòng JSON UTF-8.
package exam.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class Protocol {
    public static final int TCP_PORT = 5000, UDP_PORT = 9999;
    public static final String DISCOVER = "EXAM_DISCOVER", SERVER_REPLY = "EXAM_SERVER";
    public static final String TOPIC = "Hệ thống giám sát thi trắc nghiệm qua mạng LAN";

    public static final String LOGIN = "LOGIN", LOGIN_OK = "LOGIN_OK", LOGIN_FAIL = "LOGIN_FAIL",
            PRECHECK_RESULT = "PRECHECK_RESULT", EXAM_START = "EXAM_START", VIOLATION = "VIOLATION",
            FEATURES = "FEATURES", APPS = "APPS", SUBMIT = "SUBMIT", EXAM_END = "EXAM_END", RESULT = "RESULT";

    // Chu kỳ gửi FEATURES (giây): server cấp trong LOGIN_OK, chỉ nhận 10..60, sai hoặc thiếu thì dùng 30
    public static final int FEATURE_INTERVAL_DEFAULT = 30, FEATURE_INTERVAL_MIN = 10, FEATURE_INTERVAL_MAX = 60;

    public static final Gson GSON = new Gson();

    private Protocol() {}

    /** Tạo object có sẵn trường "type". */
    public static JsonObject msg(String type) {
        JsonObject m = new JsonObject();
        m.addProperty("type", type);
        return m;
    }

    /** Đọc trường featureIntervalSeconds của m (rules.json hoặc LOGIN_OK); không hợp lệ thì trả 30. */
    public static int featureInterval(JsonObject m) {
        JsonElement e = m.get("featureIntervalSeconds");
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return FEATURE_INTERVAL_DEFAULT;
        }
        double seconds = e.getAsDouble();
        if (seconds != Math.rint(seconds) || seconds < FEATURE_INTERVAL_MIN || seconds > FEATURE_INTERVAL_MAX) {
            return FEATURE_INTERVAL_DEFAULT;
        }
        return (int) seconds;
    }

    public static String type(JsonObject m) {
        return str(m, "type");
    }

    /** Lấy chuỗi đã trim; thiếu trường hoặc sai kiểu thì trả "" để người gọi khỏi kiểm tra null. */
    public static String str(JsonObject m, String key) {
        JsonElement e = m.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            return "";
        }
        return e.getAsString().trim();
    }

    /** Gson tự escape \n trong chuỗi nên mỗi tin nhắn luôn nằm trên đúng một dòng. */
    public static void send(PrintWriter out, JsonObject m) {
        String line = GSON.toJson(m) + "\n";
        synchronized (out) {
            out.print(line);
            out.flush();
        }
    }

    public static JsonObject parse(String line) {
        try {
            JsonElement e = JsonParser.parseString(line);
            if (e.isJsonObject()) {
                return e.getAsJsonObject();
            }
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Dòng không phải JSON hợp lệ", ex);
        }
        throw new IllegalArgumentException("Dòng không phải JSON object");
    }

    public static BufferedReader reader(Socket s) throws IOException {
        return new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
    }

    public static PrintWriter writer(Socket s) throws IOException {
        return new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
    }
}
