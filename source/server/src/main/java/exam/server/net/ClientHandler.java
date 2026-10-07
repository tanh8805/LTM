// Owner: Nguoi1

package exam.server.net;

import exam.common.model.Role;
import exam.common.model.UserAccount;
import exam.common.protocol.AnswerAckMessage;
import exam.common.protocol.AnswerMessage;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.HeartbeatAckMessage;
import exam.common.protocol.HeartbeatMessage;
import exam.common.protocol.LoginFailMessage;
import exam.common.protocol.LoginMessage;
import exam.common.protocol.LoginOkMessage;
import exam.common.protocol.Message;
import exam.common.protocol.MessageCodec;
import exam.common.protocol.MetricsDetailMessage;
import exam.common.protocol.NoticeMessage;
import exam.common.protocol.ReconnectMessage;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.SubmitMessage;
import exam.common.protocol.SubmitOkMessage;
import exam.common.protocol.ViolationMessage;
import exam.server.api.ExamService;
import exam.server.api.MessageSender;
import exam.server.api.MonitorService;
import exam.server.api.SessionRegistry;
import exam.server.session.ClientSession;
import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Phục vụ MỘT client trên MỘT virtual thread: đọc từng dòng, decode, rồi chuyển cho đúng hàm xử lý.
 *
 * Luồng của một message:
 *   connection.readLine() -> codec.decode() -> handleMessage() -> handleXxx() -> service -> (SQLite)
 *                                                                          \-> connection.send(trả lời)
 *
 * Client phải LOGIN trước. Sau LOGIN_OK, field "session" khác null.
 */
public class ClientHandler {

    /**
     * Sinh viên gửi HEARTBEAT mỗi 10 giây. Nếu 30 giây (3 chu kỳ) không nhận được gì,
     * Server coi sinh viên đã mất kết nối. Đây là cách phát hiện client chết mà không đóng socket.
     */
    private static final int STUDENT_SILENCE_TIMEOUT_MS = 30_000;

    private final ClientConnection connection;
    private final SessionRegistry sessionRegistry;
    private final MessageSender messageSender;
    private final ExamService examService;
    private final MonitorService monitorService;
    private final MessageCodec codec = new MessageCodec();

    /** null cho tới khi LOGIN thành công. */
    private ClientSession session;

    public ClientHandler(ClientConnection connection, SessionRegistry sessionRegistry, MessageSender messageSender,
                         ExamService examService, MonitorService monitorService) {
        this.connection = connection;
        this.sessionRegistry = sessionRegistry;
        this.messageSender = messageSender;
        this.examService = examService;
        this.monitorService = monitorService;
    }

    /** Vòng đời của một client: đọc cho tới khi mất kết nối, rồi dọn dẹp. */
    public void run() {
        System.out.println("[Net] Client kết nối: " + connection.getRemoteAddress());
        try {
            String line;
            while ((line = connection.readLine()) != null) {
                handleLine(line);
            }
            System.out.println("[Net] Client đóng kết nối: " + connection.getRemoteAddress());
        } catch (SocketTimeoutException e) {
            System.out.println("[Net] " + describeClient() + " im lặng quá "
                    + STUDENT_SILENCE_TIMEOUT_MS / 1000 + " giây -> coi là offline");
        } catch (IOException e) {
            System.out.println("[Net] Lỗi kết nối với " + describeClient() + ": " + e.getMessage());
        } finally {
            handleDisconnect();
        }
    }

    /** Decode một dòng JSON rồi xử lý. Lỗi của MỘT message không được làm chết cả connection. */
    private void handleLine(String line) {
        Message message;
        try {
            message = codec.decode(line);
        } catch (IllegalArgumentException e) {
            System.out.println("[Net] Message sai định dạng từ " + describeClient() + ": " + e.getMessage());
            connection.send(new ErrorMessage("BAD_MESSAGE", e.getMessage()));
            return;
        }

        try {
            handleMessage(message);
        } catch (RuntimeException e) {
            e.printStackTrace();
            connection.send(new ErrorMessage("INTERNAL_ERROR", "Server lỗi khi xử lý " + message.type));
        }
    }

    /** Chuyển message cho hàm xử lý theo loại. */
    private void handleMessage(Message message) {
        // Hai loại này được phép khi chưa đăng nhập.
        if (message instanceof LoginMessage login) {
            handleLogin(login);
            return;
        }
        if (message instanceof ReconnectMessage reconnect) {
            handleReconnect(reconnect);
            return;
        }

        // Các message còn lại bắt buộc phải đăng nhập trước.
        if (session == null) {
            connection.send(new ErrorMessage("NOT_LOGGED_IN", "Hãy gửi LOGIN trước"));
            return;
        }

        if (message instanceof HeartbeatMessage heartbeat) {
            handleHeartbeat(heartbeat);
        } else if (message instanceof AnswerMessage answer) {
            handleAnswer(answer);
        } else if (message instanceof SubmitMessage submit) {
            handleSubmit(submit);
        } else if (message instanceof ViolationMessage violation) {
            handleViolation(violation);
        } else if (message instanceof MetricsDetailMessage detail) {
            handleMetricsDetail(detail);
        } else if (message instanceof RequestMessage request) {
            handleRequest(request);
        } else if (message instanceof NoticeMessage notice) {
            handleNotice(notice);
        } else {
            connection.send(new ErrorMessage("UNEXPECTED_MESSAGE", "Server không nhận " + message.type + " từ client"));
        }
    }

    // ------------------------------------------------------------------
    // Đăng nhập
    // ------------------------------------------------------------------

    private void handleLogin(LoginMessage login) {
        if (session != null) {
            connection.send(new ErrorMessage("ALREADY_LOGGED_IN", "Connection này đã đăng nhập rồi"));
            return;
        }
        if (login.username == null || login.password == null || login.role == null) {
            connection.send(new LoginFailMessage("Thiếu username, password hoặc role"));
            return;
        }
        if (login.role == Role.STUDENT && login.examCode == null) {
            connection.send(new LoginFailMessage("Sinh viên phải nhập mã ca thi"));
            return;
        }

        // Handler -> Service -> DAO -> SQLite
        UserAccount user = examService.login(login.username, login.password, login.role);
        if (user == null) {
            System.out.println("[Login] Thất bại: " + login.username + " (" + login.role + ")");
            connection.send(new LoginFailMessage("Sai tài khoản hoặc mật khẩu"));
            return;
        }

        session = sessionRegistry.createSession(user, login.examCode, connection);
        System.out.println("[Login] OK: " + user.username + " (" + user.role + ") từ " + connection.getRemoteAddress());

        connection.send(new LoginOkMessage(
                session.token, user.id, user.fullName, user.role, System.currentTimeMillis()));

        if (user.role == Role.STUDENT) {
            enableSilenceTimeout();
        }
        // TODO(Nguoi2): Nếu ca thi của sinh viên đang chạy, gửi EXAM_START (examService.prepareQuestionsForStudent).
        // TODO(Nguoi3): Gửi RULES_CONFIG cho sinh viên sau khi đăng nhập.
    }

    /** Chỉ áp dụng cho sinh viên (giáo viên không gửi HEARTBEAT nên không được phép timeout). */
    private void enableSilenceTimeout() {
        try {
            connection.setReadTimeout(STUDENT_SILENCE_TIMEOUT_MS);
        } catch (IOException e) {
            // TODO(Nguoi1): Add proper error handling.
            e.printStackTrace();
        }
    }

    private void handleReconnect(ReconnectMessage reconnect) {
        // TODO(Nguoi1): Tìm session bằng reconnect.token (sessionRegistry.findByToken), gắn connection mới vào session,
        //  đặt online = true, gửi RECONNECT_OK (endTimeServer + các đáp án đã lưu để client khôi phục bài).
        connection.send(new ErrorMessage("NOT_IMPLEMENTED", "RECONNECT chưa được cài đặt"));
    }

    // ------------------------------------------------------------------
    // Heartbeat và giám sát
    // ------------------------------------------------------------------

    private void handleHeartbeat(HeartbeatMessage heartbeat) {
        session.lastHeartbeatSeq = heartbeat.seq;
        session.lastHeartbeatTime = System.currentTimeMillis();

        if (heartbeat.summary != null) {
            System.out.println("[Heartbeat] " + session.machineId + " seq=" + heartbeat.seq
                    + " cpu=" + heartbeat.summary.cpuPercent + "% ram=" + heartbeat.summary.ramPercent + "%"
                    + " focusLost=" + heartbeat.summary.focusLostCount);
            monitorService.onMetricsSummary(session.machineId, heartbeat.summary);
        } else {
            System.out.println("[Heartbeat] " + session.machineId + " seq=" + heartbeat.seq + " (không có summary)");
        }

        connection.send(new HeartbeatAckMessage(heartbeat.seq, System.currentTimeMillis()));
    }

    private void handleMetricsDetail(MetricsDetailMessage detail) {
        if (!requireRole(Role.STUDENT)) {
            return;
        }
        monitorService.onMetricsDetail(session.machineId, detail);
    }

    private void handleViolation(ViolationMessage violation) {
        if (!requireRole(Role.STUDENT)) {
            return;
        }
        System.out.println("[Violation] " + session.machineId + " " + violation.violationType + ": " + violation.evidence);
        monitorService.onViolation(session.machineId, violation);
    }

    // ------------------------------------------------------------------
    // Làm bài
    // ------------------------------------------------------------------

    private void handleAnswer(AnswerMessage answer) {
        if (!requireRole(Role.STUDENT)) {
            return;
        }
        boolean saved = examService.saveAnswer(session.userId, session.examCode, answer.questionId, answer.choice);
        connection.send(new AnswerAckMessage(answer.seq, answer.questionId, saved));
    }

    private void handleSubmit(SubmitMessage submit) {
        if (!requireRole(Role.STUDENT)) {
            return;
        }
        int answeredCount = examService.submit(session.userId, session.examCode);
        connection.send(new SubmitOkMessage(answeredCount));
    }

    // ------------------------------------------------------------------
    // Giáo viên
    // ------------------------------------------------------------------

    private void handleRequest(RequestMessage request) {
        if (!requireRole(Role.TEACHER)) {
            return;
        }
        connection.send(examService.handleTeacherRequest(request));
    }

    private void handleNotice(NoticeMessage notice) {
        if (!requireRole(Role.TEACHER)) {
            return;
        }
        if ("ALL".equals(notice.target)) {
            int sentCount = messageSender.sendToAllStudents(notice);
            System.out.println("[Notice] Gửi cả phòng: " + sentCount + " máy");
        } else {
            boolean sent = messageSender.sendToMachine(notice.target, notice);
            System.out.println("[Notice] Gửi " + notice.target + ": " + (sent ? "OK" : "máy offline"));
        }
    }

    // ------------------------------------------------------------------
    // Hàm phụ
    // ------------------------------------------------------------------

    /** true nếu client đang đăng nhập đúng vai trò; ngược lại trả ERROR FORBIDDEN và trả về false. */
    private boolean requireRole(Role requiredRole) {
        if (session.role == requiredRole) {
            return true;
        }
        connection.send(new ErrorMessage("FORBIDDEN", "Chỉ " + requiredRole + " mới được gửi message này"));
        return false;
    }

    private void handleDisconnect() {
        if (session != null) {
            sessionRegistry.markOffline(session);
            monitorService.onMachineOffline(session.machineId);
            System.out.println("[Net] " + session.machineId + " offline");
            // TODO(Nguoi1): Gửi ALERT cho giáo viên khi sinh viên đang thi bị offline.
        }
        connection.close();
    }

    private String describeClient() {
        if (session != null) {
            return session.machineId;
        }
        return connection.getRemoteAddress();
    }
}
