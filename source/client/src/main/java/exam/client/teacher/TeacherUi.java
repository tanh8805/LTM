// Owner: Nguoi2

package exam.client.teacher;

import com.google.gson.JsonObject;
import exam.client.TeacherClient;
import exam.common.protocol.ResponseMessage;
import java.awt.Component;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Hàm dùng chung cho các màn hình giáo viên: gọi REQUEST không chặn giao diện và hiện lỗi nếu có. */
public final class TeacherUi {

    private TeacherUi() {
    }

    /**
     * Gửi REQUEST. Khi RESPONSE ok = true thì chạy onSuccess(data) TRÊN THREAD GIAO DIỆN.
     * Khi lỗi (ok = false, mất kết nối, quá hạn) thì hiện hộp thoại báo lỗi.
     */
    public static void call(TeacherClient client, Component parent, String action, JsonObject data,
                            Consumer<JsonObject> onSuccess) {
        client.request(action, data).whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                showError(parent, "Không gọi được " + action + ": " + describe(error));
            } else if (!response.ok) {
                showError(parent, response.error);
            } else {
                onSuccess.accept(response.data != null ? response.data : new JsonObject());
            }
        }));
    }

    /** Giống call() nhưng nhận cả RESPONSE lỗi để người gọi tự xử lý. */
    public static void callRaw(TeacherClient client, String action, JsonObject data, Consumer<ResponseMessage> onResponse) {
        client.request(action, data).whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                onResponse.accept(new ResponseMessage(null, false, null, describe(error)));
            } else {
                onResponse.accept(response);
            }
        }));
    }

    public static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    public static void showInfo(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String describe(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
