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
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

class StudentScreens {
    private static final int LOST_AFTER_MS = 20_000;     // mất mạng quá lâu thì chấm trạng thái chuyển từ cam sang đỏ

    private final JFrame frame;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final TopBar topBar;
    private final JPanel banner = new JPanel(new BorderLayout());
    private final JLabel statusBar = Theme.label(" ", 13, false, Theme.MUTED);
    private final LoginPanel login;
    private final PrecheckPanel precheck;
    private final ExamPanel exam;
    private final ResultPanel result;
    private final JPanel header = new JPanel();
    private final Timer lostTimer;
    private List<Integer> unanswered = List.of();     // số thứ tự (từ 1) các câu chưa làm
    private IntConsumer gotoQuestion;
    private int total;

    /** Sự kiện nút bấm gọi ngược về StudentApp qua các callback; mọi phương thức show... gọi trên luồng giao diện. */
    StudentScreens(JFrame frame, Runnable onLogin, Runnable onRecheck, Runnable onPrev, Runnable onNext,
                   Runnable onSubmit, IntConsumer onAnswer, IntConsumer onGoto, Runnable onExit) {
        this.frame = frame;
        this.gotoQuestion = onGoto;
        topBar = new TopBar(() -> confirmSubmit(onSubmit));
        lostTimer = new Timer(LOST_AFTER_MS, e -> topBar.setConnection(TopBar.Conn.LOST));
        result = new ResultPanel(onExit);
        login = new LoginPanel(onLogin);
        precheck = new PrecheckPanel(onRecheck);
        exam = new ExamPanel(onGoto, onPrev, onNext, onAnswer);
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
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);    // StudentApp hỏi xác nhận nếu bài chưa nộp
        show("login");
    }

    // ---------- Đọc dữ liệu nhập và thông báo ----------

    String studentId() { return login.studentId(); }

    String password() { return login.password(); }

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
        precheck.setActive(name.equals("precheck"));
        topBar.setExamMode(name.equals("exam"));
        header.setVisible(!name.equals("login"));
        frame.getRootPane().setDefaultButton(name.equals("login") ? login.button() : null);
    }

    void showLogin() { show("login"); }

    void showPrecheck(List<String> issues, String server, String name) {
        topBar.setStudent(name, studentId());
        precheck.setIssues(issues, server);
        show("precheck");
    }

    void showWaiting(String name) {
        topBar.setStudent(name, studentId());
        show("wait");
    }

    void showExam(String title) {
        topBar.setExamTitle(title);
        show("exam");
    }

    /** Màn thông báo thay cho kết quả: đang nộp bài, hoặc bài thi đã kết thúc. */
    void showFinished(String text) {
        result.showMessage(text);
        show("result");
    }

    void showResult(double score, int correct, int total, String submittedAt) {
        result.showResult(score, correct, total, submittedAt);
        show("result");
    }

    void showTerminated(String reason, int total) {
        result.showTerminated(reason, total);
        show("result");
    }

    void setTimer(int seconds) { topBar.setTimer(seconds); }

    /** Hiện một câu hỏi; selected là chỉ số lựa chọn đã chọn trước đó, -1 nếu chưa chọn. */
    void showQuestion(int number, int total, String text, List<String> options, int selected) {
        exam.showQuestion(number, total, text, options, selected);
    }

    /** answered[i] = câu thứ i+1 đã có đáp án. */
    void setAnswered(boolean[] answered) {
        total = answered.length;
        List<Integer> missing = new java.util.ArrayList<>();
        for (int i = 0; i < answered.length; i++) { if (!answered[i]) { missing.add(i + 1); } }
        unanswered = missing;
        exam.setAnswered(answered);
    }

    int answeredCount() { return total - unanswered.size(); }

    /** Hộp xác nhận nộp bài; "Xem câu chưa làm" nhảy tới câu chưa làm đầu tiên rồi quay lại làm bài. */
    private void confirmSubmit(Runnable onSubmit) {
        boolean missing = !unanswered.isEmpty();
        Sheet.Choice c = Sheet.ask(frame, "Nộp bài ngay?", submitMessage(total, unanswered),
            missing ? "Xem câu chưa làm" : "Quay lại", "Nộp bài");
        if (c == Sheet.Choice.PRIMARY) {
            onSubmit.run();
        } else if (c == Sheet.Choice.SECONDARY && missing) {
            gotoQuestion.accept(unanswered.get(0) - 1);
        }
    }

    /** Hộp xác nhận khi đóng cửa sổ lúc bài chưa nộp: true = nộp bài rồi thoát, false = tiếp tục làm bài. */
    boolean confirmExit() {
        return Sheet.ask(frame, "Thoát khi bài chưa nộp?", exitMessage(answeredCount()), "Nộp bài và thoát", "Tiếp tục làm bài")
            == Sheet.Choice.SECONDARY;
    }

    static String submitMessage(int total, List<Integer> unanswered) {
        String done = "Bạn đã làm " + (total - unanswered.size()) + "/" + total + " câu. ";
        if (unanswered.isEmpty()) { return done + "Sau khi nộp sẽ không sửa được."; }
        return done + "Còn " + unanswered.size() + " câu chưa làm: "
            + unanswered.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))
            + ". Sau khi nộp sẽ không sửa được.";
    }

    static String exitMessage(int done) {
        return "Đáp án chỉ lưu trong ứng dụng này. Nếu thoát bây giờ, " + done + " câu đã làm sẽ mất. Bạn có thể nộp bài rồi thoát.";
    }
}
