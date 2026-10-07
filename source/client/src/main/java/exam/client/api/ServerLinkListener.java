// Owner: Nguoi1

package exam.client.api;

import exam.common.protocol.Message;

/**
 * Nhận sự kiện từ ServerLink.
 *
 * LƯU Ý: các method này được gọi từ thread đọc socket, KHÔNG phải thread giao diện của Swing.
 * Muốn cập nhật giao diện phải qua SwingUtilities.invokeLater(...) (các Frame của project đã làm sẵn).
 */
public interface ServerLinkListener {

    /** Có message mới từ Server. */
    void onMessage(Message message);

    /** Trạng thái kết nối thay đổi. */
    void onStatusChanged(ConnectionStatus newStatus);
}
