// Owner: Nguoi1

package exam.client;

import exam.client.api.ConnectionStatus;
import exam.client.api.ServerLink;
import exam.client.api.ServerLinkListener;
import exam.client.api.RuleEngine;
import exam.client.monitor.MonitoringLoop;
import exam.client.student.StudentActions;
import exam.client.student.StudentView;
import exam.common.model.Metrics;
import exam.common.model.Role;
import exam.common.protocol.AnswerAckMessage;
import exam.common.protocol.AnswerMessage;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.ExamEndMessage;
import exam.common.protocol.ExamStartMessage;
import exam.common.protocol.HeartbeatAckMessage;
import exam.common.protocol.HeartbeatMessage;
import exam.common.protocol.LoginFailMessage;
import exam.common.protocol.LoginMessage;
import exam.common.protocol.LoginOkMessage;
import exam.common.protocol.Message;
import exam.common.protocol.NoticeMessage;
import exam.common.protocol.ReconnectMessage;
import exam.common.protocol.ReconnectOkMessage;
import exam.common.protocol.RoomStatsMessage;
import exam.common.protocol.RulesConfigMessage;
import exam.common.protocol.SetRateMessage;
import exam.common.protocol.SubmitMessage;
import exam.common.protocol.SubmitOkMessage;
import exam.common.protocol.TimeSyncMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Phần logic của ứng dụng sinh viên (không có giao diện, nên test được).
 *
 * Việc làm:
 *   - đăng nhập, giữ token, gửi HEARTBEAT đều đặn (kèm số liệu giám sát)
 *   - nhận đề (EXAM_START), gửi ANSWER ngay khi chọn, SUBMIT, nhận EXAM_END
 *   - mất kết nối: TcpServerLink tự nối lại TCP, rồi lớp này gửi RECONNECT bằng token, khôi phục đáp án,
 *     gửi lại các ANSWER chưa được xác nhận. Nếu Server không nhận token (Server đã khởi động lại) thì đăng nhập lại.
 *   - đồng hồ: chỉ HIỂN THỊ thời gian còn lại tính theo đồng hồ Server. Hết giờ do Server quyết định (EXAM_END).
 */
public class StudentClient implements ServerLinkListener, StudentActions {

    private final ServerLink serverLink;
    private final MonitoringLoop monitoringLoop;
    private final RuleEngine ruleEngine;
    private final String host;
    private final int port;
    private volatile StudentView view;

    private volatile String studentCode;
    private volatile String password;
    private volatile String examCode;

    private volatile String token;
    /** true khi Server đã chấp nhận phiên (sau LOGIN_OK hoặc RECONNECT_OK); chỉ khi đó mới gửi HEARTBEAT, ANSWER. */
    private volatile boolean sessionActive = false;
    private volatile boolean closed = false;

    private volatile long serverClockOffset = 0;
    private volatile long endTimeServer = 0;
    private volatile boolean examActive = false;
    private volatile int heartbeatIntervalMs;
    private Thread heartbeatThread;
    private int heartbeatSeq = 0;

    private int answerSeq = 0;
    /** Các ANSWER chưa được Server xác nhận: seq -> message. Gửi lại sau khi nối lại. */
    private final ConcurrentSkipListMap<Integer, AnswerMessage> pendingAnswers = new ConcurrentSkipListMap<>();
    private final Map<Integer, Integer> answers = new ConcurrentHashMap<>();
    private volatile int lastAckedAnswerSeq = 0;

    public StudentClient(ServerLink serverLink, MonitoringLoop monitoringLoop, RuleEngine ruleEngine,
                         String host, int port, int heartbeatIntervalMs) {
        this.serverLink = serverLink;
        this.monitoringLoop = monitoringLoop;
        this.ruleEngine = ruleEngine;
        this.host = host;
        this.port = port;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        serverLink.addListener(this);
    }

    public void setView(StudentView view) {
        this.view = view;
    }

    // ------------------------------------------------------------------
    // Việc sinh viên yêu cầu (StudentActions)
    // ------------------------------------------------------------------

    @Override
    public void login(String studentCode, String password, String examCode) {
        this.studentCode = studentCode;
        this.password = password;
        this.examCode = examCode;
        // Kết nối có thể chậm nên chạy trên thread khác, không làm đơ giao diện.
        Thread.startVirtualThread(this::connectAndLogin);
    }

    private void connectAndLogin() {
        if (serverLink.getStatus() != ConnectionStatus.CONNECTED) {
            try {
                serverLink.connect(host, port);
            } catch (IOException e) {
                System.out.println("[Student] Không kết nối được tới " + host + ":" + port + ": " + e.getMessage());
                view.showStatus("Không kết nối được tới " + host + ":" + port);
                view.showLogin(false, "Không kết nối được tới Server (" + e.getMessage() + ")");
                return;
            }
        }
        sendLogin();
    }

    private void sendLogin() {
        serverLink.send(new LoginMessage(studentCode, password, Role.STUDENT, examCode));
    }

    @Override
    public boolean selectAnswer(int questionId, int choice) {
        if (!examActive) {
            return false;
        }
        int seq;
        synchronized (pendingAnswers) {
            seq = ++answerSeq;
            AnswerMessage message = new AnswerMessage(seq, questionId, choice);
            pendingAnswers.put(seq, message);
            answers.put(questionId, choice);
            // Gửi ngay khi chọn. Nếu đang mất kết nối thì message nằm lại trong pendingAnswers và được gửi lại sau.
            if (sessionActive) {
                serverLink.send(message);
            }
        }
        return true;
    }

    @Override
    public boolean submit() {
        if (!examActive || !sessionActive) {
            return false;
        }
        int seq;
        synchronized (pendingAnswers) {
            seq = ++answerSeq;
        }
        return serverLink.send(new SubmitMessage(seq));
    }

    @Override
    public void onFocusLost() {
        ruleEngine.onFocusLost();
    }

    @Override
    public long getRemainingSeconds() {
        if (endTimeServer <= 0) {
            return -1;
        }
        long serverNow = System.currentTimeMillis() + serverClockOffset;
        return Math.max(0, (endTimeServer - serverNow) / 1000);
    }

    public void close() {
        closed = true;
        monitoringLoop.stop();
        serverLink.close();
    }

    // ------------------------------------------------------------------
    // Trạng thái kết nối
    // ------------------------------------------------------------------

    @Override
    public void onStatusChanged(ConnectionStatus newStatus) {
        if (view == null) {
            return;
        }
        switch (newStatus) {
            case CONNECTED:
                view.showStatus("Connected");
                if (token != null) {
                    // Đây là lần nối lại sau khi mất kết nối: lấy lại phiên cũ bằng token.
                    sessionActive = false;
                    serverLink.send(new ReconnectMessage(token, lastAckedAnswerSeq));
                }
                break;
            case RECONNECTING:
                sessionActive = false;
                view.showStatus("Disconnected - đang thử nối lại...");
                break;
            case DISCONNECTED:
            default:
                sessionActive = false;
                view.showStatus("Disconnected");
                break;
        }
    }

    // ------------------------------------------------------------------
    // Nhận từ Server (chạy trên thread đọc socket)
    // ------------------------------------------------------------------

    @Override
    public void onMessage(Message message) {
        if (message instanceof LoginOkMessage loginOk) {
            handleLoginOk(loginOk);
        } else if (message instanceof LoginFailMessage loginFail) {
            System.out.println("[Student] LOGIN_FAIL: " + loginFail.reason);
            view.showLogin(false, loginFail.reason);
        } else if (message instanceof ReconnectOkMessage reconnectOk) {
            handleReconnectOk(reconnectOk);
        } else if (message instanceof HeartbeatAckMessage ack) {
            serverClockOffset = ack.serverTime - System.currentTimeMillis();
            view.showHeartbeat(ack.seq);
        } else if (message instanceof ExamStartMessage start) {
            handleExamStart(start);
        } else if (message instanceof RulesConfigMessage rulesConfig) {
            handleRulesConfig(rulesConfig);
        } else if (message instanceof TimeSyncMessage timeSync) {
            serverClockOffset = timeSync.serverTime - System.currentTimeMillis();
        } else if (message instanceof AnswerAckMessage ack) {
            handleAnswerAck(ack);
        } else if (message instanceof SubmitOkMessage submitOk) {
            examActive = false;
            monitoringLoop.stop();
            view.showSubmitted(submitOk.answeredCount);
        } else if (message instanceof ExamEndMessage examEnd) {
            examActive = false;
            monitoringLoop.stop();
            view.showExamEnded(examEnd.reason);
        } else if (message instanceof SetRateMessage setRate) {
            monitoringLoop.setRate(setRate.mode, setRate.intervalMs);
            view.appendLog("SET_RATE " + setRate.mode + " " + setRate.intervalMs + " ms");
        } else if (message instanceof NoticeMessage notice) {
            view.showNotice(notice.text);
        } else if (message instanceof RoomStatsMessage roomStats) {
            view.showRoomStats(summarizeRoomStats(roomStats));
        } else if (message instanceof ErrorMessage error) {
            handleError(error);
        } else {
            view.appendLog("Nhận " + message.type + " (không xử lý)");
        }
    }

    private void handleLoginOk(LoginOkMessage loginOk) {
        token = loginOk.token;
        sessionActive = true;
        serverClockOffset = loginOk.serverTime - System.currentTimeMillis();
        System.out.println("[Student] LOGIN_OK, token=" + loginOk.token);
        view.showLogin(true, "OK");
        startHeartbeatThreadOnce();
    }

    private void handleReconnectOk(ReconnectOkMessage reconnectOk) {
        serverClockOffset = reconnectOk.serverTime - System.currentTimeMillis();
        if (reconnectOk.endTimeServer > 0) {
            endTimeServer = reconnectOk.endTimeServer;
        }
        if (reconnectOk.answers != null) {
            answers.putAll(reconnectOk.answers);
            view.showRestoredAnswers(new HashMap<>(answers));
        }
        sessionActive = true;
        resendPendingAnswers();
        System.out.println("[Student] RECONNECT_OK, đã khôi phục phiên");
        view.showStatus("Reconnected");
    }

    private void handleExamStart(ExamStartMessage start) {
        endTimeServer = start.endTimeServer;
        examActive = true;
        answers.clear();
        if (start.answers != null) {
            answers.putAll(start.answers);
        }
        view.showExam(start.title, start.questions, new HashMap<>(answers));
        resendPendingAnswers();
    }

    /** Server gửi luật giám sát: bắt đầu đo và kiểm tra luật. Chạy trên thread riêng vì resolve DNS có thể chậm. */
    private void handleRulesConfig(RulesConfigMessage rulesConfig) {
        heartbeatIntervalMs = rulesConfig.rules.metricsIntervalSeconds * 1000;
        Thread.startVirtualThread(() -> monitoringLoop.start(rulesConfig.rules, rulesConfig.mlMode, heartbeatIntervalMs));
        view.appendLog("RULES_CONFIG: mlMode=" + rulesConfig.mlMode + ", chu kỳ " + rulesConfig.rules.metricsIntervalSeconds + " giây");
    }

    private void handleAnswerAck(AnswerAckMessage ack) {
        pendingAnswers.remove(ack.seq);
        if (ack.seq > lastAckedAnswerSeq) {
            lastAckedAnswerSeq = ack.seq;
        }
        if (ack.saved) {
            view.showAnswerSaved(ack.questionId);
        } else {
            view.showAnswerRejected(ack.questionId);
        }
    }

    private void handleError(ErrorMessage error) {
        System.out.println("[Student] ERROR " + error.code + ": " + error.message);
        view.appendLog("ERROR " + error.code + ": " + error.message);

        boolean tokenRejected = "INVALID_TOKEN".equals(error.code) || "TOKEN_EXPIRED".equals(error.code);
        if (tokenRejected && studentCode != null) {
            // Server không nhận token (ví dụ Server đã khởi động lại): đăng nhập lại bằng mật khẩu.
            token = null;
            view.appendLog("Token không còn hiệu lực, đăng nhập lại");
            sendLogin();
        }
    }

    /** Gửi lại mọi ANSWER chưa được xác nhận, đúng thứ tự seq. */
    private void resendPendingAnswers() {
        synchronized (pendingAnswers) {
            for (AnswerMessage pending : pendingAnswers.values()) {
                serverLink.send(pending);
            }
        }
    }

    private String summarizeRoomStats(RoomStatsMessage stats) {
        return String.format("Trung vị phòng: gửi %.1f KB/s, nhận %.1f KB/s, CPU %.1f%%, RAM %.1f%%",
                stats.medians.getOrDefault("kbSent", 0.0), stats.medians.getOrDefault("kbReceived", 0.0),
                stats.medians.getOrDefault("cpuPercent", 0.0), stats.medians.getOrDefault("ramPercent", 0.0));
    }

    // ------------------------------------------------------------------
    // HEARTBEAT mỗi chu kỳ (đồng thời là nhịp đo số liệu giám sát)
    // ------------------------------------------------------------------

    private synchronized void startHeartbeatThreadOnce() {
        if (heartbeatThread != null) {
            return;
        }
        heartbeatThread = Thread.startVirtualThread(this::runHeartbeatLoop);
    }

    private void runHeartbeatLoop() {
        while (!closed) {
            if (sessionActive && serverLink.getStatus() == ConnectionStatus.CONNECTED) {
                MonitoringLoop.Sample sample = monitoringLoop.takeSample();
                Metrics summary = sample.metrics;
                serverLink.send(new HeartbeatMessage(++heartbeatSeq, summary, sample.anomalyScore, sample.anomalous));
            }
            if (!sleepOneHeartbeatInterval()) {
                return;
            }
        }
    }

    /** Ngủ một chu kỳ heartbeat, chia nhỏ để đổi chu kỳ (RULES_CONFIG) hoặc đóng ứng dụng có hiệu lực nhanh. */
    private boolean sleepOneHeartbeatInterval() {
        long waited = 0;
        while (waited < heartbeatIntervalMs && !closed) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Có ai đó yêu cầu dừng: giữ lại cờ interrupt rồi thoát.
                Thread.currentThread().interrupt();
                return false;
            }
            waited += 50;
        }
        return !closed;
    }
}
