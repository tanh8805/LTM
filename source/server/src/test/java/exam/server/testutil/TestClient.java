// Owner: Nguoi1

package exam.server.testutil;

import com.google.gson.JsonObject;
import exam.common.model.Role;
import exam.common.protocol.LoginMessage;
import exam.common.protocol.LoginOkMessage;
import exam.common.protocol.Message;
import exam.common.protocol.MessageCodec;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Client thô dùng trong test: mở socket tới Server và nói đúng giao thức (mỗi message một dòng JSON).
 * Cho phép gửi cả dòng rác để thử các trường hợp lỗi.
 */
public class TestClient implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final MessageCodec codec = new MessageCodec();

    public TestClient(int port) throws IOException {
        socket = new Socket("localhost", port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public void send(Message message) throws IOException {
        sendRaw(codec.encode(message));
    }

    public void sendRaw(String line) throws IOException {
        writer.write(line);
        writer.write('\n');
        writer.flush();
    }

    /** Đọc một message. Trả về null nếu quá hạn mà chưa có gì. Ném IOException nếu Server đã đóng kết nối. */
    public Message read(int timeoutMillis) throws IOException {
        socket.setSoTimeout(timeoutMillis);
        try {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Server đã đóng kết nối");
            }
            return codec.decode(line);
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    /** Đọc cho tới khi gặp message loại T (bỏ qua các message khác như SET_RATE, TIME_SYNC). Trả về null nếu quá hạn. */
    public <T extends Message> T readUntil(Class<T> type, int timeoutMillis) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            int remaining = (int) Math.max(50, deadline - System.currentTimeMillis());
            Message message = read(remaining);
            if (message == null) {
                return null;
            }
            if (type.isInstance(message)) {
                return type.cast(message);
            }
        }
        return null;
    }

    /** true nếu Server đóng kết nối trong thời gian cho phép. Các message còn nằm trong hàng đợi được đọc bỏ qua. */
    public boolean isClosedByServer(int timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        try {
            while (System.currentTimeMillis() < deadline) {
                socket.setSoTimeout((int) Math.max(50, deadline - System.currentTimeMillis()));
                if (reader.readLine() == null) {
                    return true;
                }
            }
            return false;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            return true; // reset hoặc đã đóng
        }
    }

    /** Đăng nhập và trả về LOGIN_OK. Ném lỗi nếu không nhận được LOGIN_OK. */
    public LoginOkMessage login(String username, String password, Role role, String examCode) throws IOException {
        send(new LoginMessage(username, password, role, examCode));
        Message reply = read(3000);
        if (reply instanceof LoginOkMessage loginOk) {
            return loginOk;
        }
        throw new IOException("Không nhận được LOGIN_OK, nhận: " + (reply == null ? "không gì cả" : reply.type));
    }

    /** Gửi REQUEST và chờ RESPONSE cùng requestId. */
    public ResponseMessage request(String action, JsonObject data) throws IOException {
        String requestId = UUID.randomUUID().toString();
        send(new RequestMessage(requestId, action, data));
        ResponseMessage response = readUntil(ResponseMessage.class, 5000);
        if (response == null || !requestId.equals(response.requestId)) {
            throw new IOException("Không nhận được RESPONSE cho " + action);
        }
        return response;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("[TestClient] Lỗi khi đóng socket: " + e.getMessage());
        }
    }
}
