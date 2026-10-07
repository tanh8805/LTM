// Owner: Nguoi1

package exam.client.api;

import exam.common.protocol.Message;
import java.io.IOException;

/**
 * Đường nối từ client tới Server: gửi message, nhận message, biết trạng thái kết nối.
 *
 * Ai cài đặt: Nguoi1 (exam.client.net.TcpServerLink)
 * Ai gọi: Nguoi1 (StudentMain, TeacherMain), Nguoi2 (màn hình thi, màn hình giáo viên),
 *         Nguoi3 (gửi VIOLATION, METRICS_DETAIL)
 */
public interface ServerLink {

    /** Kết nối TCP tới Server. Ném IOException nếu không nối được. */
    void connect(String host, int port) throws IOException;

    /** Gửi một message. Trả về false nếu chưa kết nối hoặc gửi lỗi. */
    boolean send(Message message);

    /** Đăng ký nhận message và thay đổi trạng thái. */
    void addListener(ServerLinkListener listener);

    ConnectionStatus getStatus();

    /**
     * Cắt socket hiện tại để buộc nối lại (RECONNECTING), dùng khi nghi ngờ kết nối đã chết lặng
     * (không có FIN/RST nên TCP không tự báo lỗi). Khác close(): ServerLink vẫn tiếp tục thử nối lại.
     */
    void resetConnection();

    /** Đóng kết nối hẳn, không thử nối lại. */
    void close();
}
