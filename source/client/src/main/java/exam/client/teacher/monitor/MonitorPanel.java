// Owner: Nguoi3

package exam.client.teacher.monitor;

import exam.common.protocol.AlertMessage;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Tab "Giám sát" của giáo viên.
 * HIỆN LÀ STUB: chỉ in các ALERT nhận được ra một ô văn bản.
 */
public class MonitorPanel extends JPanel {

    private final JTextArea alertArea = new JTextArea();

    public MonitorPanel() {
        super(new BorderLayout());

        // TODO(Nguoi3): Thay bằng bảng danh sách máy: mã SV, online/offline, mức cảnh báo đỏ/vàng, lý do.
        // TODO(Nguoi3): Nút "Xem chi tiết" một máy (METRICS_DETAIL gần nhất).
        // TODO(Nguoi3): Nút gửi NOTICE tới một máy hoặc tới cả phòng.
        add(new JLabel("Giám sát realtime (TODO(Nguoi3)). Các ALERT nhận được:"), BorderLayout.NORTH);
        alertArea.setEditable(false);
        add(new JScrollPane(alertArea), BorderLayout.CENTER);
    }

    /** Được gọi từ thread đọc socket, nên cập nhật giao diện qua invokeLater. */
    public void showAlert(AlertMessage alert) {
        SwingUtilities.invokeLater(() ->
                alertArea.append("[" + alert.level + "] " + alert.machineId + ": " + alert.reason + "\n"));
    }
}
