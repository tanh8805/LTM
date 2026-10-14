// Chỉ dựng giao diện sinh viên (Swing + FlatLaf, CardLayout): thanh trên, 5 màn hình, thanh cảnh báo mất mạng. Không có logic mạng hay giám sát (Người 1).
package exam.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

class StudentScreens {
    private static final int LOST_AFTER_MS = 20_000;     // mất mạng quá lâu thì chấm trạng thái chuyển từ cam sang đỏ

    private final JFrame frame;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final TopBar topBar = new TopBar();
    private final JPanel banner = new JPanel(new BorderLayout());
    private final JLabel statusBar = Theme.label(" ", 13, false, Theme.MUTED);
    private final LoginPanel login;
    private final PrecheckPanel precheck;
    private final ExamPanel exam;
    private final ResultPanel result;
    private final JPanel header = new JPanel();
    private final Timer lostTimer = new Timer(LOST_AFTER_MS, e -> topBar.setConnection(TopBar.Conn.LOST));
    private int unanswered;

    /** Sự kiện nút bấm gọi ngược về StudentApp qua các callback; mọi phương thức show... gọi trên luồng giao diện. */
    StudentScreens(JFrame frame, Runnable onLogin, Runnable onRecheck, Runnable onPrev, Runnable onNext,
                   Runnable onSubmit, IntConsumer onAnswer, IntConsumer onGoto, Runnable onNewRound) {
        this.frame = frame;
        result = new ResultPanel(onNewRound);
        login = new LoginPanel(onLogin);
        precheck = new PrecheckPanel(onRecheck);
        exam = new ExamPanel(onGoto, onPrev, onNext, () -> confirmSubmit(onSubmit), onAnswer);
        lostTimer.setRepeats(false);
        root.add(login, "login");
        root.add(precheck, "precheck");
        root.add(PrecheckPanel.waitingPanel(), "wait");
        root.add(exam, "exam");
        root.add(result, "result");

        JLabel bannerText = Theme.label("Mất kết nối, đang thử lại...", Theme.BASE_SIZE, true, Theme.WARNING);
        bannerText.setHorizontalAlignment(SwingConstants.CENTER);
        banner.setBackground(Theme.WARNING_SOFT);
        banner.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        banner.add(bannerText);
        banner.setVisible(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(topBar);
        header.add(banner);
        header.setVisible(false);
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 24, 6, 24));
        statusBar.setOpaque(true);
        statusBar.setBackground(Theme.BACKGROUND);

        frame.add(header, BorderLayout.NORTH);
        frame.add(root, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);
        frame.setMinimumSize(new Dimension(1000, 700));
        frame.setSize(1100, 760);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        show("login");
    }

    // ---------- Đọc dữ liệu nhập và thông báo ----------

    String studentId() { return login.studentId(); }

    String studentName() { return login.studentName(); }

    String host() { return login.host(); }

    /** Đang tìm server/kết nối thì khoá nút Đăng nhập và hiện thanh tiến trình kèm chữ. */
    void setLoginBusy(boolean busy, String text) { login.setBusy(busy, text); }

    /** Lỗi đăng nhập hiện bằng chữ đỏ ngay dưới form, không dùng hộp thoại. */
    void showMessage(String text) { login.setError(text); }

    /** Được gọi từ luồng nền nên tự chuyển sang luồng giao diện. */
    void setStatus(String text) { SwingUtilities.invokeLater(() -> statusBar.setText(text)); }

    /** Cập nhật chấm kết nối và thanh cam "Mất kết nối"; gọi được từ luồng nền. */
    void setConnection(TopBar.Conn state) {
        SwingUtilities.invokeLater(() -> {
            topBar.setConnection(state);
            banner.setVisible(state == TopBar.Conn.RECONNECTING || state == TopBar.Conn.LOST);
            if (state == TopBar.Conn.RECONNECTING) { lostTimer.restart(); } else { lostTimer.stop(); }
        });
    }

    // ---------- Chuyển màn hình ----------

    private void show(String name) {
        cards.show(root, name);
        header.setVisible(!name.equals("login"));
        frame.getRootPane().setDefaultButton(name.equals("login") ? login.button() : null);
    }

    void showLogin() { show("login"); }

    void showPrecheck(List<String> issues) {
        topBar.setStudent(studentName(), studentId());
        precheck.setIssues(issues);
        show("precheck");
    }

    void showWaiting() {
        topBar.setStudent(studentName(), studentId());
        show("wait");
    }

    void showExam(String title) {
        topBar.setExamTitle(title);
        topBar.setTimerVisible(true);
        show("exam");
    }

    /** Màn thông báo thay cho kết quả: đang nộp bài, hoặc bài thi đã kết thúc. */
    void showFinished(String text) {
        result.showMessage(text);
        topBar.setTimerVisible(false);
        show("result");
    }

    /** Bài thi đã kết thúc từ trước khi máy này vào: có nút Thi lượt mới. */
    void showEnded(String text) {
        result.showEnded(text);
        topBar.setTimerVisible(false);
        show("result");
    }

    /** Quay về màn đăng nhập cho kỳ thi mới; mã SV, họ tên vẫn nằm sẵn trong form. */
    void resetForNewRound() {
        login.setBusy(false, "");
        login.setError(" ");
        setStatus(" ");
        setConnection(TopBar.Conn.NONE);
        topBar.setTimerVisible(false);
        show("login");
    }

    void showResult(double score, int correct, int total) {
        result.showResult(score, correct, total);
        topBar.setTimerVisible(false);
        show("result");
    }

    void setTimer(int seconds) { topBar.setTimer(seconds); }

    /** Hiện một câu hỏi; selected là chỉ số lựa chọn đã chọn trước đó, -1 nếu chưa chọn. */
    void showQuestion(int number, int total, String text, List<String> options, int selected) {
        exam.showQuestion(number, total, text, options, selected);
    }

    /** answered[i] = câu thứ i+1 đã có đáp án. */
    void setAnswered(boolean[] answered) {
        unanswered = 0;
        for (boolean a : answered) { if (!a) { unanswered++; } }
        exam.setAnswered(answered);
    }

    /** Hộp thoại nhận cửa sổ chính làm cha để việc mở nó không bị tính là rời cửa sổ thi. */
    private void confirmSubmit(Runnable onSubmit) {
        String[] buttons = {"Nộp bài", "Quay lại"};        // tự đặt nhãn vì Swing hiện Yes/No bằng tiếng Anh
        if (JOptionPane.showOptionDialog(frame, submitQuestion(unanswered), "Xác nhận nộp bài", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, buttons, buttons[1]) == 0) {
            onSubmit.run();
        }
    }

    static String submitQuestion(int unanswered) {
        return unanswered > 0 ? "Bạn còn " + unanswered + " câu chưa làm. Nộp bài?" : "Nộp bài ngay?";
    }
}
