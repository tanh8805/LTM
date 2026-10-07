// Owner: Nguoi1

package exam.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import exam.client.api.ConnectionStatus;
import exam.client.teacher.TeacherView;
import exam.client.testutil.FakeServerLink;
import exam.client.testutil.Wait;
import exam.common.model.AlertLevel;
import exam.common.model.Role;
import exam.common.protocol.AlertMessage;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.LoginFailMessage;
import exam.common.protocol.LoginMessage;
import exam.common.protocol.LoginOkMessage;
import exam.common.protocol.NoticeMessage;
import exam.common.protocol.ReconnectMessage;
import exam.common.protocol.ReconnectOkMessage;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeacherClientTest {

    private static class RecordingView implements TeacherView {
        final List<String> statuses = Collections.synchronizedList(new ArrayList<>());
        final List<String> logins = Collections.synchronizedList(new ArrayList<>());
        final List<AlertMessage> alerts = Collections.synchronizedList(new ArrayList<>());
        final List<String> errors = Collections.synchronizedList(new ArrayList<>());

        public void showStatus(String text) {
            statuses.add(text);
        }

        public void showLogin(boolean ok, String message) {
            logins.add((ok ? "OK:" : "FAIL:") + message);
        }

        public void showAlert(AlertMessage alert) {
            alerts.add(alert);
        }

        public void showError(String text) {
            errors.add(text);
        }
    }

    private FakeServerLink link;
    private RecordingView view;
    private TeacherClient client;

    @BeforeEach
    void setUp() {
        link = new FakeServerLink();
        view = new RecordingView();
        client = new TeacherClient(link, "localhost", 5000);
        client.setView(view);
    }

    private void loginSuccessfully() {
        client.login("gv01", "teacher123");
        assertTrue(Wait.until(() -> !link.sentOfType(LoginMessage.class).isEmpty(), 3000));
        link.deliver(new LoginOkMessage("teacher-token", 1, "Giảng viên", Role.TEACHER, System.currentTimeMillis()));
    }

    @Test
    void loginSendsTeacherLoginWithoutExamCode() {
        client.login("gv01", "teacher123");

        assertTrue(Wait.until(() -> !link.sentOfType(LoginMessage.class).isEmpty(), 3000));
        LoginMessage login = link.sentOfType(LoginMessage.class).get(0);
        assertEquals(Role.TEACHER, login.role);
        assertEquals("gv01", login.username);
        assertEquals(null, login.examCode);
    }

    @Test
    void loginResultIsShown() {
        loginSuccessfully();
        link.deliver(new LoginFailMessage("x"));

        assertEquals(List.of("OK:OK", "FAIL:x"), view.logins);
        assertTrue(client.isLoggedIn());
    }

    @Test
    void responseCompletesTheRequestWithTheSameRequestId() throws Exception {
        loginSuccessfully();

        CompletableFuture<ResponseMessage> future = client.request("LIST_QUESTIONS", new JsonObject());

        RequestMessage sent = link.sentOfType(RequestMessage.class).get(0);
        assertEquals("LIST_QUESTIONS", sent.action);
        assertFalse(future.isDone());

        JsonObject data = new JsonObject();
        data.addProperty("count", 30);
        link.deliver(new ResponseMessage(sent.requestId, true, data, null));

        ResponseMessage response = future.get(1, TimeUnit.SECONDS);
        assertTrue(response.ok);
        assertEquals(30, response.data.get("count").getAsInt());
    }

    @Test
    void concurrentRequestsAreMatchedByRequestIdNotByOrder() throws Exception {
        loginSuccessfully();
        CompletableFuture<ResponseMessage> first = client.request("A", new JsonObject());
        CompletableFuture<ResponseMessage> second = client.request("B", new JsonObject());
        List<RequestMessage> sent = link.sentOfType(RequestMessage.class);

        // Server trả lời ngược thứ tự
        link.deliver(new ResponseMessage(sent.get(1).requestId, true, new JsonObject(), null));
        assertTrue(second.isDone());
        assertFalse(first.isDone());
        link.deliver(new ResponseMessage(sent.get(0).requestId, false, null, "lỗi"));

        assertFalse(first.get(1, TimeUnit.SECONDS).ok);
    }

    @Test
    void responseWithUnknownRequestIdIsIgnored() {
        loginSuccessfully();

        link.deliver(new ResponseMessage("khong-ai-hoi", true, new JsonObject(), null));

        assertTrue(view.errors.isEmpty());
    }

    @Test
    void requestBeforeLoginFailsImmediately() {
        CompletableFuture<ResponseMessage> future = client.request("LIST_QUESTIONS", new JsonObject());

        assertTrue(future.isCompletedExceptionally());
        assertTrue(link.sentOfType(RequestMessage.class).isEmpty());
    }

    @Test
    void requestWhileDisconnectedFails() {
        loginSuccessfully();
        link.setStatus(ConnectionStatus.RECONNECTING);

        ExecutionException error = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> client.request("LIST_QUESTIONS", new JsonObject()).get(1, TimeUnit.SECONDS));

        assertTrue(error.getCause().getMessage().contains("Chưa kết nối"));
    }

    @Test
    void alertIsForwardedToTheView() {
        loginSuccessfully();

        link.deliver(new AlertMessage("SV003", AlertLevel.RED, "Process bị cấm: Zalo"));

        assertEquals(1, view.alerts.size());
        assertEquals("SV003", view.alerts.get(0).machineId);
    }

    @Test
    void noticeIsSentAsNoticeMessage() {
        loginSuccessfully();

        assertTrue(client.sendNotice("SV001", "Chú ý"));

        NoticeMessage notice = link.sentOfType(NoticeMessage.class).get(0);
        assertEquals("SV001", notice.target);
        assertEquals("Chú ý", notice.text);
    }

    @Test
    void serverErrorWithoutRequestIsShown() {
        loginSuccessfully();

        link.deliver(new ErrorMessage("MACHINE_OFFLINE", "Máy SV009 không online"));

        assertEquals(1, view.errors.size());
        assertTrue(view.errors.get(0).contains("MACHINE_OFFLINE"));
    }

    @Test
    void reconnectSendsTokenAndKeepsSession() {
        loginSuccessfully();
        link.setStatus(ConnectionStatus.RECONNECTING);
        assertFalse(client.isLoggedIn());

        link.setStatus(ConnectionStatus.CONNECTED);
        assertEquals("teacher-token", link.sentOfType(ReconnectMessage.class).get(0).token);
        link.deliver(new ReconnectOkMessage(0, new HashMap<>(), System.currentTimeMillis()));

        assertTrue(client.isLoggedIn());
        assertEquals(1, link.sentOfType(LoginMessage.class).size());
    }

    @Test
    void expiredTokenFallsBackToLogin() {
        loginSuccessfully();
        link.setStatus(ConnectionStatus.RECONNECTING);
        link.setStatus(ConnectionStatus.CONNECTED);

        link.deliver(new ErrorMessage("TOKEN_EXPIRED", "hết hạn"));

        assertEquals(2, link.sentOfType(LoginMessage.class).size());
        assertTrue(view.errors.isEmpty(), "lỗi token được tự xử lý, không làm phiền người dùng");
    }
}
