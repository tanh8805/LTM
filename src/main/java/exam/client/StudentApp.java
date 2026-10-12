// Giao diện sinh viên (Swing, CardLayout): đăng nhập, PRECHECK, chờ, làm bài, xem kết quả.
package exam.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import javax.swing.Timer;

public class StudentApp {
    private final JFrame frame = new JFrame("Thi trắc nghiệm");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JLabel statusBar = new JLabel(" ");
    private final JTextField idField = new JTextField(20), nameField = new JTextField(20), hostField = new JTextField(20);
    private final JButton loginBtn = new JButton("Đăng nhập");
    private final JTextArea issuesArea = new JTextArea();
    private final JLabel titleLabel = new JLabel(), timerLabel = new JLabel(), numberLabel = new JLabel();
    private final JLabel questionLabel = new JLabel(), resultLabel = new JLabel();
    private final JPanel optionsPanel = new JPanel();
    private final Monitor monitor = new Monitor();
    private final Map<Integer, Integer> answers = new HashMap<>();   // id câu hỏi -> chỉ số lựa chọn (chỉ nằm trong bộ nhớ)
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
        frame.addWindowFocusListener(new WindowFocusListener() {
            // getOppositeWindow() == null nghĩa là focus sang app khác; hộp thoại của chính app thì không tính
            @Override public void windowGainedFocus(WindowEvent e) {
                if (examActive && e.getOppositeWindow() == null) {
                    monitor.focusGained();
                }
            }
            @Override public void windowLostFocus(WindowEvent e) {
                if (examActive && e.getOppositeWindow() == null) {
                    monitor.focusLost();
                }
            }
        });
        countdown = new Timer(1000, e -> tick());
        frame.setVisible(true);
    }

    // ---------- Các màn hình ----------

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
        loginBtn.addActionListener(e -> login());
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
        again.addActionListener(e -> runPrecheck());
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
        prev.addActionListener(e -> goTo(current - 1));
        next.addActionListener(e -> goTo(current + 1));
        submit.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(frame, "Nộp bài ngay?", "Xác nhận", JOptionPane.YES_NO_OPTION)
                    == JOptionPane.YES_OPTION) {
                submit();
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

    // ---------- Đăng nhập và nhận tin ----------

    private void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusBar.setText(text));
    }

    private void login() {
        studentId = idField.getText().trim();
        studentName = nameField.getText().trim();
        if (studentId.isEmpty() || studentName.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Hãy nhập mã SV và họ tên");
            return;
        }
        loginBtn.setEnabled(false);
        setStatus("Đang kết nối...");
        String host = hostField.getText().trim();
        new Thread(() -> connect(host), "login").start();   // tìm server và nối mạng không được chặn luồng giao diện
    }

    private void connect(String host) {
        InetSocketAddress address = host.isEmpty() ? ServerFinder.find(Protocol.UDP_PORT, 3000)
                                                   : new InetSocketAddress(host, Protocol.TCP_PORT);
        if (address == null) {
            loginFailed("Không tìm thấy server, hãy nhập IP");
            return;
        }
        conn = new ServerConnection(address, m -> SwingUtilities.invokeLater(() -> onMessage(m)),
            () -> setStatus("Mất kết nối, đang thử lại..."),
            () -> {
                setStatus("Đã kết nối lại");
                sendLogin();
            });
        try {
            conn.connect();
            setStatus("Đã kết nối " + address.getHostString() + ":" + address.getPort());
            sendLogin();
        } catch (java.io.IOException e) {
            loginFailed("Không kết nối được: " + e.getMessage());
        }
    }

    private void loginFailed(String message) {
        SwingUtilities.invokeLater(() -> {
            loginBtn.setEnabled(true);
            statusBar.setText(" ");
            JOptionPane.showMessageDialog(frame, message);
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
            case Protocol.LOGIN_OK -> {
                loggedInOnce = true;
                rules = m.getAsJsonObject("rules");
                if (questions == null) {       // đã có đề (vào lại giữa giờ): server sẽ gửi lại EXAM_START
                    runPrecheck();
                }
            }
            case Protocol.LOGIN_FAIL -> onLoginFail(Protocol.str(m, "reason"));
            case Protocol.EXAM_START -> onExamStart(m);
            case Protocol.EXAM_END -> submit();
            case Protocol.RESULT -> onResult(m);
            default -> { }
        }
    }

    private void onLoginFail(String reason) {
        if (loggedInOnce) {                    // vào lại mà server chưa kịp thấy kết nối cũ đã chết: thử lại sau 5 giây
            setStatus(reason + " - thử lại sau 5 giây...");
            Timer retry = new Timer(5000, e -> sendLogin());
            retry.setRepeats(false);
            retry.start();
            return;
        }
        conn.close();
        loginFailed(reason);
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
                issuesArea.setText(String.join("\n", issues));
                if (questions == null) {
                    cards.show(root, issues.isEmpty() ? "wait" : "precheck");
                }
            });
            conn.send(m);
        }, "precheck").start();
    }

    // ---------- Làm bài ----------

    private void onExamStart(JsonObject m) {
        deadlineMillis = System.currentTimeMillis() + m.get("remainingSeconds").getAsLong() * 1000;
        if (submitted) {                       // SUBMIT đã bị bỏ lúc rớt mạng: gửi lại
            sendSubmit();
            return;
        }
        if (questions != null) {               // vào lại: giữ nguyên đáp án và câu đang xem
            return;
        }
        questions = m.getAsJsonArray("questions");
        titleLabel.setText(Protocol.str(m, "title"));
        current = 0;
        examActive = true;
        monitor.start(conn::send);
        countdown.start();
        showQuestion();
        tick();
        cards.show(root, "exam");
    }

    private void tick() {
        long left = Math.max(0, (deadlineMillis - System.currentTimeMillis()) / 1000);
        timerLabel.setText(String.format("Còn lại: %02d:%02d", left / 60, left % 60));
        if (left == 0 && !submitted) {
            submit();
        }
    }

    private void goTo(int index) {
        if (index >= 0 && index < questions.size()) {
            current = index;
            showQuestion();
        }
    }

    private void showQuestion() {
        JsonObject q = questions.get(current).getAsJsonObject();
        int id = q.get("id").getAsInt();
        numberLabel.setText("Câu " + (current + 1) + "/" + questions.size());
        questionLabel.setText("<html><body style='width:650px'>" + escape(Protocol.str(q, "text")) + "</body></html>");
        optionsPanel.removeAll();
        ButtonGroup group = new ButtonGroup();
        JsonArray options = q.getAsJsonArray("options");
        for (int i = 0; i < options.size(); i++) {
            JRadioButton radio = new JRadioButton(options.get(i).getAsString());
            radio.setSelected(Integer.valueOf(i).equals(answers.get(id)));
            int choice = i;
            radio.addActionListener(e -> {
                answers.put(id, choice);
                monitor.recordAnswer();
            });
            group.add(radio);
            optionsPanel.add(radio);
        }
        optionsPanel.revalidate();
        optionsPanel.repaint();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void submit() {
        if (submitted || questions == null) {
            return;
        }
        submitted = true;
        examActive = false;
        monitor.stop();
        countdown.stop();
        setStatus("Đã nộp bài, chờ kết quả...");
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
        String score = new DecimalFormat("0.##").format(m.get("score").getAsDouble());
        resultLabel.setText("Điểm: " + score + "/10 (đúng " + m.get("correct").getAsInt()
            + "/" + m.get("total").getAsInt() + " câu)");
        cards.show(root, "result");
        conn.close();
        setStatus("Đã hoàn thành");
    }
}
