// Owner: Nguoi1

package exam.e2e;

import com.google.gson.JsonObject;
import exam.client.TeacherClient;
import exam.client.net.TcpServerLink;
import exam.client.teacher.TeacherView;
import exam.common.protocol.AlertMessage;
import exam.common.protocol.ResponseMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Một "máy giáo viên" không có cửa sổ: TeacherClient + view ghi lại ALERT và lỗi. */
public class TeacherHarness implements AutoCloseable, TeacherView {

    public final List<AlertMessage> alerts = Collections.synchronizedList(new ArrayList<>());
    public final List<String> errors = Collections.synchronizedList(new ArrayList<>());
    public final List<String> logins = Collections.synchronizedList(new ArrayList<>());
    private final TeacherClient client;

    public TeacherHarness(String host, int port) {
        this.client = new TeacherClient(new TcpServerLink(300, 100), host, port);
        this.client.setView(this);
    }

    public void login(String username, String password) {
        client.login(username, password);
    }

    public boolean isLoggedIn() {
        return client.isLoggedIn();
    }

    /** Gửi REQUEST và chờ RESPONSE (tối đa 20 giây). Ném lỗi nếu không có trả lời. */
    public ResponseMessage call(String action, JsonObject data) {
        try {
            return client.request(action, data).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("REQUEST " + action + " thất bại: " + e, e);
        }
    }

    public ResponseMessage call(String action) {
        return call(action, new JsonObject());
    }

    public boolean sendNotice(String target, String text) {
        return client.sendNotice(target, text);
    }

    @Override
    public void showStatus(String text) {
    }

    @Override
    public void showLogin(boolean ok, String message) {
        logins.add((ok ? "OK:" : "FAIL:") + message);
    }

    @Override
    public void showAlert(AlertMessage alert) {
        alerts.add(alert);
    }

    @Override
    public void showError(String text) {
        errors.add(text);
    }

    /** ALERT đầu tiên của máy này có lý do chứa đoạn text, hoặc null. */
    public AlertMessage findAlert(String machineId, String reasonContains) {
        synchronized (alerts) {
            for (AlertMessage alert : alerts) {
                if (alert.machineId.equals(machineId) && alert.reason.contains(reasonContains)) {
                    return alert;
                }
            }
        }
        return null;
    }

    @Override
    public void close() {
        client.close();
    }
}
