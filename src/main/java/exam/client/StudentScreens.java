// Chỉ dựng giao diện sinh viên (Swing, CardLayout): 5 màn hình và thanh trạng thái. Không có logic mạng hay giám sát (Người 1).
package exam.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

class StudentScreens {
    private final JFrame frame;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JLabel statusBar = new JLabel(" ");
    private final JTextField idField = new JTextField(20), nameField = new JTextField(20), hostField = new JTextField(20);
    private final JButton loginBtn = new JButton("Đăng nhập");
    private final JTextArea issuesArea = new JTextArea();
    private final JLabel titleLabel = new JLabel(), timerLabel = new JLabel(), numberLabel = new JLabel();
    private final JLabel questionLabel = new JLabel(), resultLabel = new JLabel();
    private final JPanel optionsPanel = new JPanel();
    private final Runnable onLogin, onRecheck, onPrev, onNext, onSubmit;
    private final IntConsumer onAnswer;

    /** Sự kiện nút bấm gọi ngược về StudentApp qua các callback; mọi phương thức show... gọi trên luồng giao diện. */
    StudentScreens(JFrame frame, Runnable onLogin, Runnable onRecheck, Runnable onPrev, Runnable onNext,
                   Runnable onSubmit, IntConsumer onAnswer) {
        this.frame = frame;
        this.onLogin = onLogin;
        this.onRecheck = onRecheck;
        this.onPrev = onPrev;
        this.onNext = onNext;
        this.onSubmit = onSubmit;
        this.onAnswer = onAnswer;
        root.add(loginPanel(), "login");
        root.add(precheckPanel(), "precheck");
        root.add(centered(new JLabel("Đã sẵn sàng. Chờ giáo viên bắt đầu...")), "wait");
        root.add(examPanel(), "exam");
        root.add(centered(resultLabel), "result");
        frame.add(root, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    // ---------- Đọc dữ liệu nhập và thông báo ----------

    String studentId() { return idField.getText().trim(); }

    String studentName() { return nameField.getText().trim(); }

    String host() { return hostField.getText().trim(); }

    void setLoginEnabled(boolean enabled) { loginBtn.setEnabled(enabled); }

    void showMessage(String text) { JOptionPane.showMessageDialog(frame, text); }

    /** Được gọi từ luồng nền nên tự chuyển sang luồng giao diện. */
    void setStatus(String text) { SwingUtilities.invokeLater(() -> statusBar.setText(text)); }

    // ---------- Chuyển màn hình ----------

    void showLogin() { cards.show(root, "login"); }

    void showPrecheck(List<String> issues) {
        issuesArea.setText(String.join("\n", issues));
        cards.show(root, "precheck");
    }

    void showWaiting() { cards.show(root, "wait"); }

    void showExam(String title) {
        titleLabel.setText(title);
        cards.show(root, "exam");
    }

    /** Màn thông báo thay cho kết quả: đang nộp bài, hoặc bài thi đã kết thúc. */
    void showFinished(String text) {
        resultLabel.setText(text);
        cards.show(root, "result");
    }

    void showResult(double score, int correct, int total) {
        resultLabel.setText("Điểm: " + new DecimalFormat("0.##").format(score) + "/10 (đúng " + correct + "/" + total + " câu)");
        cards.show(root, "result");
    }

    void setTimer(int seconds) {
        timerLabel.setText(String.format("Còn lại: %02d:%02d", seconds / 60, seconds % 60));
    }

    /** Hiện một câu hỏi; selected là chỉ số lựa chọn đã chọn trước đó, -1 nếu chưa chọn. */
    void showQuestion(int number, int total, String text, List<String> options, int selected) {
        numberLabel.setText("Câu " + number + "/" + total);
        questionLabel.setText("<html><body style='width:650px'>" + escape(text) + "</body></html>");
        optionsPanel.removeAll();
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < options.size(); i++) {
            JRadioButton radio = new JRadioButton(options.get(i));
            radio.setSelected(i == selected);
            int choice = i;
            radio.addActionListener(e -> onAnswer.accept(choice));
            group.add(radio);
            optionsPanel.add(radio);
        }
        optionsPanel.revalidate();
        optionsPanel.repaint();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------- Dựng từng màn hình ----------

    private static JPanel centered(JLabel label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 200));
        p.add(label);
        return p;
    }

    private JPanel loginPanel() {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Mã SV"));
        form.add(idField);
        form.add(new JLabel("Họ tên"));
        form.add(nameField);
        form.add(new JLabel("IP server (để trống = tự tìm)"));
        form.add(hostField);
        loginBtn.addActionListener(e -> onLogin.run());
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 120));
        JPanel box = new JPanel(new BorderLayout(0, 12));
        box.add(form, BorderLayout.CENTER);
        box.add(loginBtn, BorderLayout.SOUTH);
        p.add(box);
        return p;
    }

    private JPanel precheckPanel() {
        issuesArea.setEditable(false);
        JButton again = new JButton("Kiểm tra lại");
        again.addActionListener(e -> onRecheck.run());
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        p.add(new JLabel("Máy chưa đạt yêu cầu, hãy xử lý các vấn đề sau:"), BorderLayout.NORTH);
        p.add(issuesArea, BorderLayout.CENTER);
        p.add(again, BorderLayout.SOUTH);
        return p;
    }

    private JPanel examPanel() {
        JPanel top = new JPanel(new GridLayout(1, 3));
        top.add(titleLabel);
        top.add(numberLabel);
        top.add(timerLabel);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        JPanel middle = new JPanel(new BorderLayout(0, 12));
        middle.add(questionLabel, BorderLayout.NORTH);
        middle.add(optionsPanel, BorderLayout.CENTER);
        JButton prev = new JButton("Câu trước"), next = new JButton("Câu sau"), submit = new JButton("Nộp bài");
        prev.addActionListener(e -> onPrev.run());
        next.addActionListener(e -> onNext.run());
        submit.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(frame, "Nộp bài ngay?", "Xác nhận", JOptionPane.YES_NO_OPTION)
                    == JOptionPane.YES_OPTION) {
                onSubmit.run();
            }
        });
        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(prev);
        bottom.add(next);
        bottom.add(submit);
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        p.add(top, BorderLayout.NORTH);
        p.add(middle, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }
}
