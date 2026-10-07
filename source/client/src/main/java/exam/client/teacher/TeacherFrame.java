// Owner: Nguoi2

package exam.client.teacher;

import exam.client.TeacherClient;
import exam.client.teacher.monitor.MonitorPanel;
import exam.common.protocol.AlertMessage;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Cửa sổ của giáo viên: đăng nhập, rồi các tab Câu hỏi, Đề thi, Ca thi, Giám sát, Điểm.
 * Mọi thao tác gửi REQUEST qua TeacherClient; Swing chỉ hiển thị.
 */
public class TeacherFrame extends JFrame implements TeacherView {

    private static final String CARD_LOGIN = "login";
    private static final String CARD_MAIN = "main";

    private final TeacherClient client;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    private final JTextField usernameField = new JTextField(14);
    private final JPasswordField passwordField = new JPasswordField(14);
    private final JButton loginButton = new JButton("Login");
    private final JLabel loginMessageLabel = new JLabel(" ");

    private final JLabel statusLabel = new JLabel("Status: Connecting...");
    private final JLabel loginLabel = new JLabel("Login: -");

    private final QuestionPanel questionPanel;
    private final ExamPanel examPanel;
    private final ShiftPanel shiftPanel;
    private final MonitorPanel monitorPanel;
    private final ResultsPanel resultsPanel;

    public TeacherFrame(TeacherClient client, String defaultUsername, String defaultPassword) {
        super("Teacher - Quản lý thi");
        this.client = client;
        usernameField.setText(defaultUsername);
        passwordField.setText(defaultPassword);

        questionPanel = new QuestionPanel(client);
        examPanel = new ExamPanel(client);
        shiftPanel = new ShiftPanel(client);
        monitorPanel = new MonitorPanel(client);
        resultsPanel = new ResultsPanel(client);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Câu hỏi", questionPanel);
        tabs.addTab("Đề thi", examPanel);
        tabs.addTab("Ca thi", shiftPanel);
        tabs.addTab("Giám sát", monitorPanel);
        tabs.addTab("Điểm", resultsPanel);

        cardPanel.add(buildLoginPanel(), CARD_LOGIN);
        cardPanel.add(tabs, CARD_MAIN);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        statusPanel.add(statusLabel);
        statusPanel.add(loginLabel);

        setLayout(new BorderLayout());
        add(statusPanel, BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);

        setSize(980, 620);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private JPanel buildLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Username:"), c);
        c.gridx = 1;
        panel.add(usernameField, c);
        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Password:"), c);
        c.gridx = 1;
        panel.add(passwordField, c);
        c.gridx = 1;
        c.gridy = 2;
        panel.add(loginButton, c);
        c.gridy = 3;
        panel.add(loginMessageLabel, c);

        loginButton.addActionListener(event -> {
            loginButton.setEnabled(false);
            loginMessageLabel.setText("Đang đăng nhập...");
            client.login(usernameField.getText().trim(), new String(passwordField.getPassword()));
        });
        return panel;
    }

    /** Dùng khi chạy với tham số tự đăng nhập. */
    public void clickLoginForAutoStart() {
        SwingUtilities.invokeLater(loginButton::doClick);
    }

    // ------------------------------------------------------------------
    // TeacherView: được gọi từ thread đọc socket nên chuyển sang thread giao diện
    // ------------------------------------------------------------------

    @Override
    public void showStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + text));
    }

    @Override
    public void showLogin(boolean ok, String message) {
        SwingUtilities.invokeLater(() -> {
            loginLabel.setText(ok ? "Login: OK" : "Login: FAIL (" + message + ")");
            loginMessageLabel.setText(ok ? " " : message);
            loginButton.setEnabled(!ok);
            if (ok) {
                cards.show(cardPanel, CARD_MAIN);
                questionPanel.refresh();
                examPanel.refresh();
                shiftPanel.refresh();
                resultsPanel.refreshShifts();
                monitorPanel.start();
            }
        });
    }

    @Override
    public void showAlert(AlertMessage alert) {
        monitorPanel.showAlert(alert);
    }

    @Override
    public void showError(String text) {
        SwingUtilities.invokeLater(() -> TeacherUi.showError(this, text));
    }
}
