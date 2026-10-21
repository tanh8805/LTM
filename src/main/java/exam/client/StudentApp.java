// Logic ứng dụng sinh viên: kết nối server, xử lý tin nhắn, đồng hồ, đáp án, nộp bài, gọi Monitor (Người 1).
package exam.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class StudentApp {
    private final JFrame frame = new JFrame(Protocol.TOPIC);
    private final Monitor monitor = new Monitor();
    private final Map<Integer, Integer> answers = new HashMap<>();   // id câu hỏi -> chỉ số lựa chọn (chỉ nằm trong bộ nhớ)
    private StudentScreens screens;
    private Timer countdown;

    private ServerConnection conn;
    private JsonObject rules;
    private JsonArray questions;               // null cho tới khi nhận đề lần đầu
    private String studentId = "", studentName = "", password = "";
    private int current;
    private long deadlineMillis;
    private boolean loggedInOnce, examActive, submitted, finished;   // finished: đã hiện màn kết thúc/kết quả
    private boolean exitAfterResult;           // "Nộp bài và thoát": thoát ngay khi nhận RESULT
    private String serverText = "", submittedAt = "";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();                       // FlatLaf phải được cài trước khi tạo cửa sổ
            new StudentApp().show();
        });
    }

    private void show() {
        screens = new StudentScreens(frame, this::login, this::runPrecheck, () -> goTo(current - 1),
            () -> goTo(current + 1), this::submit, this::onAnswer, this::goTo, this::exitNow);
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent e) { if (otherApp(e)) { monitor.focusGained(); } }
            @Override public void windowLostFocus(WindowEvent e) { if (otherApp(e)) { monitor.focusLost(); } }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { requestExit(); }
        });
        countdown = new Timer(1000, e -> tick());
        frame.setVisible(true);
    }

    /** getOppositeWindow() == null nghĩa là focus đi/đến app khác; hộp thoại của chính app thì không tính. */
    private boolean otherApp(WindowEvent e) { return examActive && e.getOppositeWindow() == null; }

    private void login() {
        studentId = screens.studentId();
        password = screens.password();
        if (studentId.isEmpty() || password.isEmpty()) { screens.showMessage("Hãy nhập mã SV và mật khẩu"); return; }
        String host = screens.host();
        screens.setLoginBusy(true, host.isEmpty() ? "Đang tìm server..." : "Đang kết nối...");
        new Thread(() -> connect(host), "login").start();   // tìm server và nối mạng không được chặn luồng giao diện
    }

    private void connect(String host) {
        InetSocketAddress address = host.isEmpty() ? ServerFinder.find(Protocol.UDP_PORT, 3000)
                                                   : new InetSocketAddress(host, Protocol.TCP_PORT);
        if (address == null) { loginFailed("Không tìm thấy server, hãy nhập IP"); return; }
        conn = new ServerConnection(address, m -> SwingUtilities.invokeLater(() -> onMessage(m)),
            () -> { screens.setStatus("Mất kết nối, đang thử lại..."); screens.setConnection(TopBar.Conn.RECONNECTING); },
            () -> { screens.setStatus("Đã kết nối lại"); screens.setConnection(TopBar.Conn.CONNECTED); sendLogin(); });
        try {
            conn.connect();
            serverText = address.getHostString() + ":" + address.getPort();
            screens.setStatus("Đã kết nối " + address.getHostString() + ":" + address.getPort());
            screens.setConnection(TopBar.Conn.CONNECTED);
            sendLogin();
        } catch (IOException e) {
            loginFailed("Không kết nối được: " + e.getMessage());
        }
    }

    private void loginFailed(String message) {
        SwingUtilities.invokeLater(() -> {
            screens.setLoginBusy(false, "");
            screens.setStatus(" ");
            screens.showMessage(message);
        });
    }

    private void sendLogin() {
        JsonObject m = Protocol.msg(Protocol.LOGIN);
        m.addProperty("studentId", studentId);
        m.addProperty("password", password);
        m.addProperty("os", System.getProperty("os.name", ""));
        conn.send(m);
    }

    private void onMessage(JsonObject m) {
        switch (Protocol.type(m)) {
            case Protocol.LOGIN_OK -> onLoginOk(m);
            case Protocol.LOGIN_FAIL -> onLoginFail(Protocol.str(m, "reason"));
            case Protocol.EXAM_START -> onExamStart(m);
            case Protocol.EXAM_END -> onExamEnd();
            case Protocol.RESULT -> onResult(m);
            default -> { }
        }
    }

    private void onLoginOk(JsonObject m) {
        loggedInOnce = true;
        rules = m.getAsJsonObject("rules");
        studentName = Protocol.str(m, "name");       // họ tên lấy từ danh sách của server
        monitor.useIntervalFrom(m);
        if (questions == null) { runPrecheck(); }      // đã có đề (vào lại giữa giờ): server sẽ gửi lại EXAM_START
    }

    private void onLoginFail(String reason) {
        if (!loggedInOnce) {
            conn.close();
            loginFailed(reason);
            return;
        }
        screens.setStatus(reason + " - thử lại sau 5 giây...");    // server chưa kịp thấy kết nối cũ đã chết
        Timer retry = new Timer(5000, e -> sendLogin());
        retry.setRepeats(false);
        retry.start();
    }

    private void runPrecheck() {
        new Thread(() -> {
            List<String> issues = monitor.precheck(rules);
            JsonObject m = Protocol.msg(Protocol.PRECHECK_RESULT);
            m.addProperty("ok", issues.isEmpty());
            JsonArray arr = new JsonArray();
            issues.forEach(arr::add);
            m.add("issues", arr);
            // Đưa việc cập nhật giao diện vào hàng đợi TRƯỚC khi gửi kết quả: nếu giáo viên đã bắt đầu thi thì
            // EXAM_START về ngay sau đó và phải được xử lý sau, không bị màn "Chờ" đè lên màn làm bài.
            SwingUtilities.invokeLater(() -> {
                if (questions == null && !finished) {
                    if (issues.isEmpty()) { screens.showWaiting(studentName); } else { screens.showPrecheck(issues, serverText, studentName); }
                }
            });
            if (!finished) { conn.send(m); }        // đã có RESULT (đăng nhập lại sau khi nộp) thì không cần báo PRECHECK nữa
        }, "precheck").start();
    }

    private void onExamStart(JsonObject m) {
        deadlineMillis = System.currentTimeMillis() + m.get("remainingSeconds").getAsLong() * 1000;
        if (submitted) { sendSubmit(); return; }    // SUBMIT đã bị bỏ lúc rớt mạng: gửi lại
        if (questions != null) { return; }          // vào lại: giữ nguyên đáp án và câu đang xem
        questions = m.getAsJsonArray("questions");
        current = 0;
        examActive = true;
        monitor.start(conn::send);
        countdown.start();
        showCurrentQuestion();
        tick();
        screens.showExam(Protocol.str(m, "title"));
    }

    private void tick() {
        int left = (int) Math.max(0, (deadlineMillis - System.currentTimeMillis()) / 1000);
        screens.setTimer(left);
        if (left == 0) { finish("Đã hết giờ làm bài, đang nộp..."); }
    }

    private void goTo(int index) {
        if (questions == null || index < 0 || index >= questions.size()) { return; }
        current = index;
        showCurrentQuestion();
    }

    private void showCurrentQuestion() {
        JsonObject q = questions.get(current).getAsJsonObject();
        List<String> options = new ArrayList<>();
        q.getAsJsonArray("options").forEach(o -> options.add(o.getAsString()));
        int selected = answers.getOrDefault(q.get("id").getAsInt(), -1);
        screens.showQuestion(current + 1, questions.size(), Protocol.str(q, "text"), options, selected);
        showAnswered();
    }

    /** Báo cho giao diện biết câu nào đã có đáp án (tô màu lưới số câu, đếm số câu chưa làm). */
    private void showAnswered() {
        boolean[] done = new boolean[questions.size()];
        for (int i = 0; i < done.length; i++) {
            done[i] = answers.containsKey(questions.get(i).getAsJsonObject().get("id").getAsInt());
        }
        screens.setAnswered(done);
    }

    private void onAnswer(int choice) {
        answers.put(questions.get(current).getAsJsonObject().get("id").getAsInt(), choice);
        monitor.recordAnswer();
        showAnswered();
    }

    private void submit() { finish("Đang nộp bài..."); }

    /** Giáo viên kết thúc hoặc hết giờ: dừng đồng hồ và nộp ngay những gì đã làm. */
    private void onExamEnd() {
        if (questions == null) {               // chưa có đề (vào sau khi kết thúc): không có gì để nộp
            finished = true;
            screens.showFinished("Bài thi đã kết thúc");
            screens.setConnection(TopBar.Conn.NONE);
            conn.close();
        } else if (submitted) {
            sendSubmit();                      // SUBMIT lần trước có thể đã mất lúc rớt mạng; server nộp lần hai không chấm lại
        } else {
            finish("Đã hết giờ làm bài, đang nộp...");
        }
    }

    private void finish(String message) {
        if (submitted || questions == null) { return; }
        submitted = true;
        finished = true;
        examActive = false;
        submittedAt = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        monitor.stop();
        countdown.stop();
        screens.showFinished(message);
        screens.setStatus("Đã nộp bài, chờ kết quả...");
        sendSubmit();
    }

    private void sendSubmit() {
        JsonObject a = new JsonObject();
        answers.forEach((id, choice) -> a.addProperty(String.valueOf(id), choice));
        JsonObject m = Protocol.msg(Protocol.SUBMIT);
        m.add("answers", a);
        conn.send(m);
    }

    /** RESULT đến lúc nào cũng chuyển sang màn kết quả, kể cả khi máy đang ở màn chờ/PRECHECK (đăng nhập lại sau khi đã nộp). */
    private void onResult(JsonObject m) {
        finished = true;
        examActive = false;
        monitor.stop();
        countdown.stop();
        if (submittedAt.isEmpty()) { submittedAt = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")); }
        screens.showResult(m.get("score").getAsDouble(), m.get("correct").getAsInt(), m.get("total").getAsInt(), submittedAt);
        conn.close();
        screens.setConnection(TopBar.Conn.NONE);
        screens.setStatus("Đã hoàn thành");
        if (exitAfterResult) { exitNow(); }
    }

    /** Đóng cửa sổ: đang thi mà chưa nộp thì hỏi (nộp rồi thoát hoặc tiếp tục làm), còn lại thoát luôn. */
    private void requestExit() {
        if (!examActive || submitted) { exitNow(); return; }
        if (screens.confirmExit()) {
            exitAfterResult = true;
            submit();
            Timer fallback = new Timer(8000, e -> exitNow());     // không có RESULT (mất mạng) thì vẫn thoát được
            fallback.setRepeats(false);
            fallback.start();
        }
    }

    private void exitNow() {
        if (conn != null) { conn.close(); }
        monitor.stop();
        System.exit(0);
    }
}
