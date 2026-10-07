// Owner: Nguoi2

package exam.client.teacher;

import exam.client.teacher.monitor.MonitorPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Cửa sổ của giáo viên. HIỆN LÀ SKELETON: chỉ hiện trạng thái kết nối + đăng nhập,
 * các tab chức năng là chỗ trống ghi sẵn việc cần làm.
 */
public class TeacherFrame extends JFrame {

    private final JLabel statusLabel = new JLabel("Status: Connecting...");
    private final JLabel loginLabel = new JLabel("Login: -");
    private final MonitorPanel monitorPanel = new MonitorPanel();

    public TeacherFrame(String username) {
        super("Teacher - " + username);

        JPanel statusPanel = new JPanel(new GridLayout(2, 1));
        statusPanel.add(statusLabel);
        statusPanel.add(loginLabel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Câu hỏi", createPlaceholderTab(
                "TODO(Nguoi2): Ngân hàng câu hỏi 4 đáp án: thêm/sửa/xóa, nhập CSV."));
        tabs.addTab("Đề thi", createPlaceholderTab(
                "TODO(Nguoi2): Tạo đề: chọn câu thủ công hoặc ngẫu nhiên N câu, đặt thời gian."));
        tabs.addTab("Ca thi", createPlaceholderTab(
                "TODO(Nguoi2): Tạo ca thi: chọn đề, giờ bắt đầu, thời lượng, danh sách thí sinh (CSV),\n"
                + "cấu hình luật giám sát, bắt đầu/kết thúc ca."));
        tabs.addTab("Giám sát", monitorPanel);
        tabs.addTab("Điểm", createPlaceholderTab(
                "TODO(Nguoi2): Xem điểm của ca thi và xuất CSV."));

        setLayout(new BorderLayout());
        add(statusPanel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        setSize(640, 420);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    public MonitorPanel getMonitorPanel() {
        return monitorPanel;
    }

    // Các method show... được gọi từ thread đọc socket nên cập nhật giao diện qua invokeLater.

    public void showStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + text));
    }

    public void showLogin(String text) {
        SwingUtilities.invokeLater(() -> loginLabel.setText("Login: " + text));
    }

    private JScrollPane createPlaceholderTab(String todoText) {
        JTextArea textArea = new JTextArea(todoText);
        textArea.setEditable(false);
        return new JScrollPane(textArea);
    }
}
