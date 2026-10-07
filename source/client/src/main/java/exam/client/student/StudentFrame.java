// Owner: Nguoi2

package exam.client.student;

import exam.client.api.RuleEngine;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Cửa sổ làm bài của sinh viên. HIỆN LÀ SKELETON:
 *
 *   Status: Connected
 *   Login: OK
 *   Heartbeat: seq=3
 *   + ô nhật ký message nhận được
 */
public class StudentFrame extends JFrame {

    private final JLabel statusLabel = new JLabel("Status: Connecting...");
    private final JLabel loginLabel = new JLabel("Login: -");
    private final JLabel heartbeatLabel = new JLabel("Heartbeat: -");
    private final JTextArea logArea = new JTextArea();

    public StudentFrame(String studentCode, RuleEngine ruleEngine) {
        super("Student - " + studentCode);

        JPanel statusPanel = new JPanel(new GridLayout(3, 1));
        statusPanel.add(statusLabel);
        statusPanel.add(loginLabel);
        statusPanel.add(heartbeatLabel);

        // TODO(Nguoi2): Màn hình làm bài: hiện từng câu hỏi + 4 đáp án, chuyển câu, chọn đáp án
        //  (gửi ANSWER ngay khi chọn), đánh dấu câu, đồng hồ đếm ngược theo giờ Server (TIME_SYNC), nút Nộp bài (SUBMIT).
        // TODO(Nguoi2): Khi nhận EXAM_START hiện đề; EXAM_END thì khóa bài; RECONNECT_OK thì khôi phục các đáp án đã chọn.
        logArea.setEditable(false);

        setLayout(new BorderLayout());
        add(statusPanel, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Rule 5 (Focus): mỗi lần cửa sổ thi mất focus, báo cho RuleEngine đếm.
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent event) {
            }

            @Override
            public void windowLostFocus(WindowEvent event) {
                ruleEngine.onFocusLost();
            }
        });

        setSize(480, 320);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    // Các method show... được gọi từ thread đọc socket nên cập nhật giao diện qua invokeLater.

    public void showStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + text));
    }

    public void showLogin(String text) {
        SwingUtilities.invokeLater(() -> loginLabel.setText("Login: " + text));
    }

    public void showHeartbeat(int seq) {
        SwingUtilities.invokeLater(() -> heartbeatLabel.setText("Heartbeat: seq=" + seq));
    }

    public void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text + "\n"));
    }
}
