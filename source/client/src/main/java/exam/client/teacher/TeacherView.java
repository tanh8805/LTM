// Owner: Nguoi2

package exam.client.teacher;

import exam.common.protocol.AlertMessage;

/** Màn hình giáo viên, nhìn từ phía TeacherClient. TeacherFrame (Swing) cài đặt. Được gọi từ thread đọc socket. */
public interface TeacherView {

    /** "Connected", "Disconnected", ... */
    void showStatus(String text);

    /** Kết quả đăng nhập. ok = false thì message là lý do. */
    void showLogin(boolean ok, String message);

    /** Server báo cảnh báo của một máy (ALERT). */
    void showAlert(AlertMessage alert);

    /** Lỗi từ Server không gắn với REQUEST nào (ví dụ gửi NOTICE tới máy offline). */
    void showError(String text);
}
