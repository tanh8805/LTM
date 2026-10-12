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
    private final JFrame frame = new JFrame("Thi trắc nghiệm");
    private final Monitor monitor = new Monitor();
    private final Map<Integer, Integer> answers = new HashMap<>();   // id câu hỏi -> chỉ số lựa chọn (chỉ nằm trong bộ nhớ)
    private StudentScreens screens;
    private Timer countdown;

    private ServerConnection conn;
    private JsonObject rules;
    private JsonArray questions;               // null cho tới khi nhận đề lần đầu
    private String studentId = "", studentName = "";
    private int current;
    private long deadlineMillis;
    private boolean loggedInOnce, examActive, submitted;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StudentApp().show());
    }

    private void show() {
        screens = new StudentScreens(frame, this::login, this::runPrecheck, () -> goTo(current - 1),
            () -> goTo(current + 1), this::submit, this::onAnswer);
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent e) { if (otherApp(e)) { monitor.focusGained(); } }
            @Override public void windowLostFocus(WindowEvent e) { if (otherApp(e)) { monitor.focusLost(); } }
        });
        countdown = new Timer(1000, e -> tick());
        frame.setVisible(true);
    }

    /** getOppositeWindow() == null nghĩa là focus đi/đến app khác; hộp thoại của chính app thì không tính. */
    private boolean otherApp(WindowEvent e) { return examActive && e.getOppositeWindow() == null; }

    // ---------- Đăng nhập và nhận tin ----------

    private void login() {
        studentId = screens.studentId();
        studentName = screens.studentName();
        if (studentId.isEmpty() || studentName.isEmpty()) { screens.showMessage("Hãy nhập mã SV và họ tên"); return; }
        screens.setLoginEnabled(false);
        screens.setStatus("Đang kết nối...");
        String host = screens.host();
        new Thread(() -> connect(host), "login").start();   // tìm server và nối mạng không được chặn luồng giao diện
    }

    private void connect(String host) {
        InetSocketAddress address = host.isEmpty() ? ServerFinder.find(Protocol.UDP_PORT, 3000)
                                                   : new InetSocketAddress(host, Protocol.TCP_PORT);
        if (address == null) { loginFailed("Không tìm thấy server, hãy nhập IP"); return; }
        conn = new ServerConnection(address, m -> SwingUtilities.invokeLater(() -> onMessage(m)),
            () -> screens.setStatus("Mất kết nối, đang thử lại..."),
            () -> {
                screens.setStatus("Đã kết nối lại");
                sendLogin();
            });
        try {
            conn.connect();
            screens.setStatus("Đã kết nối " + address.getHostString() + ":" + address.getPort());
            sendLogin();
        } catch (IOException e) {
            loginFailed("Không kết nối được: " + e.getMessage());
        }
    }

    private void loginFailed(String message) {
        SwingUtilities.invokeLater(() -> {
            screens.setLoginEnabled(true);
            screens.setStatus(" ");
            screens.showMessage(message);
        });
    }

    private void sendLogin() {
        JsonObject m = Protocol.msg(Protocol.LOGIN);
        m.addProperty("studentId", studentId);
        m.addProperty("name", studentName);
        conn.send(m);
    }

    private void onMessage(JsonObject m) {
        switch (Protocol.type(m)) {
            case Protocol.LOGIN_OK -> onLoginOk(m);
            case Protocol.LOGIN_FAIL -> onLoginFail(Protocol.str(m, "reason"));
            case Protocol.EXAM_START -> onExamStart(m);
            case Protocol.EXAM_END -> submit();
            case Protocol.RESULT -> onResult(m);
            default -> { }
        }
    }

    private void onLoginOk(JsonObject m) {
        loggedInOnce = true;
        rules = m.getAsJsonObject("rules");
        if (questions == null) {               // đã có đề (vào lại giữa giờ): server sẽ gửi lại EXAM_START
            runPrecheck();
        }
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
                if (questions == null) {
                    if (issues.isEmpty()) {
                        screens.showWaiting();
                    } else {
                        screens.showPrecheck(issues);
                    }
                }
            });
            conn.send(m);
        }, "precheck").start();
    }

    // ---------- Làm bài ----------

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
        if (left == 0) { submit(); }
    }

    private void goTo(int index) {
        if (questions != null && index >= 0 && index < questions.size()) {
            current = index;
            showCurrentQuestion();
        }
    }

    private void showCurrentQuestion() {
        JsonObject q = questions.get(current).getAsJsonObject();
        List<String> options = new ArrayList<>();
        q.getAsJsonArray("options").forEach(o -> options.add(o.getAsString()));
        int selected = answers.getOrDefault(q.get("id").getAsInt(), -1);
        screens.showQuestion(current + 1, questions.size(), Protocol.str(q, "text"), options, selected);
    }

    private void onAnswer(int choice) {
        answers.put(questions.get(current).getAsJsonObject().get("id").getAsInt(), choice);
        monitor.recordAnswer();
    }

    private void submit() {
        if (submitted || questions == null) { return; }
        submitted = true;
        examActive = false;
        monitor.stop();
        countdown.stop();
        screens.setStatus("Đã nộp bài, chờ kết quả...");
        sendSubmit();
    }

    private void sendSubmit() {
        JsonObject a = new JsonObject();
        for (Map.Entry<Integer, Integer> e : answers.entrySet()) {
            a.addProperty(String.valueOf(e.getKey()), e.getValue());
        }
        JsonObject m = Protocol.msg(Protocol.SUBMIT);
        m.add("answers", a);
        conn.send(m);
    }

    private void onResult(JsonObject m) {
        screens.showResult(m.get("score").getAsDouble(), m.get("correct").getAsInt(), m.get("total").getAsInt());
        conn.close();
        screens.setStatus("Đã hoàn thành");
    }
}
