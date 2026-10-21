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
    private final ExamRoom room;
    private final Socket socket;
    private PrintWriter out;
    private Session session;

    ClientHandler(ExamServer server, Socket socket) {
        this.server = server;
        this.room = server.room();
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = Protocol.reader(socket)) {
            out = Protocol.writer(socket);
            String line = in.readLine();
            while (line != null) {
                handleLine(line);
                line = in.readLine();
            }
        } catch (IOException e) {
            // socket đóng hoặc lỗi đọc: coi như mất kết nối, xử lý ở finally
        } finally {
            room.disconnected(session, socket);
            try {
                socket.close();
            } catch (IOException e) {
                // đã đóng rồi
            }
        }
    }

    private void handleLine(String line) {
        try {
            handleMessage(line);
        } catch (RuntimeException e) {
            // một tin xấu không được làm rớt kết nối của cả máy
            server.log("Lỗi xử lý tin nhắn: " + e);
        }
    }

    private void handleMessage(String line) {
        JsonObject m;
        try {
            m = Protocol.parse(line);
        } catch (IllegalArgumentException e) {
            String shown = line;
            if (line.length() > 60) {
                shown = line.substring(0, 60) + "...";
            }
            server.log("Bỏ qua dòng không phải JSON: " + shown);
            return;
        }

        String type = Protocol.type(m);
        // chưa đăng nhập thì chỉ nhận LOGIN, bỏ qua mọi tin khác
        if (session == null && !type.equals(Protocol.LOGIN)) {
            return;
        }
        if (session != null) {
            room.touch(session);
        }

        switch (type) {
            case Protocol.LOGIN -> handleLogin(m);
            case Protocol.PRECHECK_RESULT -> handlePrecheckResult(m);
            case Protocol.VIOLATION -> handleViolation(m);
            case Protocol.FEATURES -> handleFeatures(m);
            case Protocol.SUBMIT -> handleSubmit(m);
            default -> server.log("Bỏ qua type không biết: " + type);
        }
    }

    private void handleLogin(JsonObject m) {
        if (session != null) {
            return;
        }
        String studentId = Protocol.str(m, "studentId");
        String name = Protocol.str(m, "name");
        String password = Protocol.str(m, "password");
        String os = Protocol.str(m, "os");
        // login trả null nếu bị từ chối (đã gửi LOGIN_FAIL), khi đó máy được đăng nhập lại
        session = room.login(studentId, name, password, os, socket, out);
    }

    private void handlePrecheckResult(JsonObject m) {
        JsonElement okField = m.get("ok");
        boolean ok = okField != null && okField.isJsonPrimitive()
            && okField.getAsJsonPrimitive().isBoolean() && okField.getAsBoolean();
        room.precheckResult(session, ok, issuesToText(m.get("issues")));
    }

    private String issuesToText(JsonElement issues) {
        if (issues == null) {
            return "";
        }
        if (!issues.isJsonArray()) {
            return issues.toString();
        }
        // nối bằng "; " cho dễ đọc trong log và events.csv
        StringBuilder text = new StringBuilder();
        for (JsonElement issue : issues.getAsJsonArray()) {
            if (text.length() > 0) {
                text.append("; ");
            }
            if (issue.isJsonPrimitive()) {
                text.append(issue.getAsString());
            } else {
                text.append(issue.toString());
            }
        }
        return text.toString();
    }

    private void handleViolation(JsonObject m) {
        String kind = Protocol.str(m, "kind");
        if (kind.isEmpty()) {
            server.log("Bỏ qua VIOLATION thiếu kind");
            return;
        }
        room.violation(session, kind, Protocol.str(m, "detail"));
    }

    private void handleFeatures(JsonObject m) {
        Integer window = ExamFile.asInt(m.get("window"));
        JsonElement values = m.get("values");
        if (window == null || !isSixNumbers(values)) {
            server.log("Bỏ qua FEATURES sai định dạng từ " + session.id);
            return;
        }
        JsonObject sample = new JsonObject();
        sample.addProperty("window", window);
        sample.add("values", values);
        room.features(session, sample);
    }

    private boolean isSixNumbers(JsonElement values) {
        if (values == null || !values.isJsonArray()) {
            return false;
        }
        JsonArray array = values.getAsJsonArray();
        if (array.size() != 6) {
            return false;
        }
        for (JsonElement value : array) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return false;
            }
        }
        return true;
    }

    private void handleSubmit(JsonObject m) {
        JsonObject answers = new JsonObject();
        if (m.get("answers") instanceof JsonObject given) {
            answers = given;
        }
        room.submit(session, answers);
    }
}
