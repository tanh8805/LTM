// Một luồng cho mỗi kết nối: đọc từng dòng JSON, kiểm tra trường rồi chuyển cho ExamServer xử lý.
package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

class ClientHandler implements Runnable {
    private final ExamServer server;
    private final Socket socket;
    private ExamServer.Session session;        // null cho tới khi LOGIN thành công

    ClientHandler(ExamServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = Protocol.reader(socket)) {
            PrintWriter out = Protocol.writer(socket);
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    handle(line, out);
                } catch (RuntimeException e) {
                    // một tin xấu không được làm rớt kết nối của cả máy
                    server.log("Lỗi xử lý tin nhắn: " + e);
                }
            }
        } catch (IOException e) {
            // socket đóng hoặc lỗi đọc: coi như mất kết nối, xử lý ở finally
        } finally {
            server.disconnected(session, socket);
            try {
                socket.close();
            } catch (IOException e) {
                // đã đóng rồi
            }
        }
    }

    private void handle(String line, PrintWriter out) {
        JsonObject m;
        try {
            m = Protocol.parse(line);
        } catch (IllegalArgumentException e) {
            server.log("Bỏ qua dòng không phải JSON: " + (line.length() > 60 ? line.substring(0, 60) + "..." : line));
            return;
        }
        String type = Protocol.type(m);
        if (type.equals(Protocol.LOGIN)) {
            if (session == null) {
                session = server.login(Protocol.str(m, "studentId"), Protocol.str(m, "name"), socket, out);
            }
            return;
        }
        if (session == null) {
            return;                            // chưa đăng nhập thì bỏ qua mọi tin khác
        }
        server.touch(session);
        switch (type) {
            case Protocol.PRECHECK_RESULT -> server.precheckResult(session, isTrue(m.get("ok")),
                m.get("issues") == null ? "" : m.get("issues").toString());
            case Protocol.VIOLATION -> violation(m);
            case Protocol.FEATURES -> features(m);
            case Protocol.SUBMIT -> server.submit(session,
                m.get("answers") instanceof JsonObject a ? a : new JsonObject());
            default -> server.log("Bỏ qua type không biết: " + type);
        }
    }

    private static boolean isTrue(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private void violation(JsonObject m) {
        String kind = Protocol.str(m, "kind");
        if (kind.isEmpty()) {
            server.log("Bỏ qua VIOLATION thiếu kind");
            return;
        }
        server.violation(session, kind, Protocol.str(m, "detail"));
    }

    /** Chỉ nhận FEATURES có window nguyên và đúng 6 số; sai định dạng thì bỏ qua. */
    private void features(JsonObject m) {
        Integer window = ExamServer.asInt(m.get("window"));
        boolean valid = window != null && m.get("values") instanceof JsonArray arr && arr.size() == 6;
        if (valid) {
            for (JsonElement e : m.getAsJsonArray("values")) {
                valid &= e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
            }
        }
        if (!valid) {
            server.log("Bỏ qua FEATURES sai định dạng từ " + session.id);
            return;
        }
        JsonObject sample = new JsonObject();
        sample.addProperty("window", window);
        sample.add("values", m.getAsJsonArray("values"));
        server.features(session, sample);
    }
}
