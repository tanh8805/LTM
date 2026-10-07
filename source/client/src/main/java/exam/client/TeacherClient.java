// Owner: Nguoi1

package exam.client;

import com.google.gson.JsonObject;
import exam.client.api.ConnectionStatus;
import exam.client.api.ServerLink;
import exam.client.api.ServerLinkListener;
import exam.client.teacher.TeacherView;
import exam.common.model.Role;
import exam.common.protocol.AlertMessage;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.LoginFailMessage;
import exam.common.protocol.LoginMessage;
import exam.common.protocol.LoginOkMessage;
import exam.common.protocol.Message;
import exam.common.protocol.NoticeMessage;
import exam.common.protocol.ReconnectMessage;
import exam.common.protocol.ReconnectOkMessage;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Phần logic của ứng dụng giáo viên (không có giao diện, nên test được).
 *
 * request(action, data) gửi REQUEST và trả về một CompletableFuture hoàn thành khi RESPONSE có cùng requestId về.
 * Mất kết nối: TcpServerLink tự nối lại TCP, lớp này gửi RECONNECT bằng token (hoặc đăng nhập lại nếu token hết hạn).
 */
public class TeacherClient implements ServerLinkListener {

    private static final int REQUEST_TIMEOUT_SECONDS = 15;

    private final ServerLink serverLink;
    private final String host;
    private final int port;
    private volatile TeacherView view;

    private volatile String username;
    private volatile String password;
    private volatile String token;
    private volatile boolean sessionActive = false;

    /** Các REQUEST đang chờ RESPONSE: requestId -> future. */
    private final Map<String, CompletableFuture<ResponseMessage>> pendingRequests = new ConcurrentHashMap<>();

    public TeacherClient(ServerLink serverLink, String host, int port) {
        this.serverLink = serverLink;
        this.host = host;
        this.port = port;
        serverLink.addListener(this);
    }

    public void setView(TeacherView view) {
        this.view = view;
    }

    /** Kết nối (nếu chưa) và đăng nhập. */
    public void login(String username, String password) {
        this.username = username;
        this.password = password;
        Thread.startVirtualThread(this::connectAndLogin);
    }

    private void connectAndLogin() {
        if (serverLink.getStatus() != ConnectionStatus.CONNECTED) {
            try {
                serverLink.connect(host, port);
            } catch (IOException e) {
                System.out.println("[Teacher] Không kết nối được tới " + host + ":" + port + ": " + e.getMessage());
                view.showStatus("Không kết nối được tới " + host + ":" + port);
                view.showLogin(false, "Không kết nối được tới Server (" + e.getMessage() + ")");
                return;
            }
        }
        sendLogin();
    }

    private void sendLogin() {
        // Giáo viên không có mã ca thi nên examCode = null.
        serverLink.send(new LoginMessage(username, password, Role.TEACHER, null));
    }

    /** Gửi REQUEST. Future hoàn thành bình thường khi có RESPONSE (kể cả ok = false), hoặc lỗi nếu không gửi được / quá hạn. */
    public CompletableFuture<ResponseMessage> request(String action, JsonObject data) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<ResponseMessage> future = new CompletableFuture<ResponseMessage>()
                .orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        pendingRequests.put(requestId, future);
        future.whenComplete((response, error) -> pendingRequests.remove(requestId));

        boolean sent = sessionActive && serverLink.send(new RequestMessage(requestId, action, data));
        if (!sent) {
            future.completeExceptionally(new IOException("Chưa kết nối tới Server"));
        }
        return future;
    }

    /** Gửi NOTICE tới một máy (target = mã sinh viên) hoặc cả phòng (target = "ALL"). */
    public boolean sendNotice(String target, String text) {
        return sessionActive && serverLink.send(new NoticeMessage(target, text));
    }

    public boolean isLoggedIn() {
        return sessionActive;
    }

    public void close() {
        serverLink.close();
    }

    // ------------------------------------------------------------------

    @Override
    public void onStatusChanged(ConnectionStatus newStatus) {
        if (view == null) {
            return;
        }
        switch (newStatus) {
            case CONNECTED:
                view.showStatus("Connected");
                if (token != null) {
                    sessionActive = false;
                    serverLink.send(new ReconnectMessage(token, 0));
                }
                break;
            case RECONNECTING:
                sessionActive = false;
                view.showStatus("Disconnected - đang thử nối lại...");
                break;
            case DISCONNECTED:
            default:
                sessionActive = false;
                view.showStatus("Disconnected");
                break;
        }
    }

    @Override
    public void onMessage(Message message) {
        if (message instanceof LoginOkMessage loginOk) {
            token = loginOk.token;
            sessionActive = true;
            System.out.println("[Teacher] LOGIN_OK, token=" + loginOk.token);
            view.showLogin(true, "OK");
        } else if (message instanceof LoginFailMessage loginFail) {
            System.out.println("[Teacher] LOGIN_FAIL: " + loginFail.reason);
            view.showLogin(false, loginFail.reason);
        } else if (message instanceof ReconnectOkMessage) {
            sessionActive = true;
            view.showStatus("Reconnected");
        } else if (message instanceof ResponseMessage response) {
            CompletableFuture<ResponseMessage> future = pendingRequests.get(response.requestId);
            if (future != null) {
                future.complete(response);
            }
        } else if (message instanceof AlertMessage alert) {
            view.showAlert(alert);
        } else if (message instanceof ErrorMessage error) {
            handleError(error);
        } else {
            System.out.println("[Teacher] Nhận " + message.type + " (không xử lý)");
        }
    }

    private void handleError(ErrorMessage error) {
        System.out.println("[Teacher] ERROR " + error.code + ": " + error.message);
        boolean tokenRejected = "INVALID_TOKEN".equals(error.code) || "TOKEN_EXPIRED".equals(error.code);
        if (tokenRejected && username != null) {
            token = null;
            sendLogin();
            return;
        }
        view.showError(error.code + ": " + error.message);
    }
}
