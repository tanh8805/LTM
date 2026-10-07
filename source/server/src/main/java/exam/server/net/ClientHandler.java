// Owner: Nguoi1

package exam.server.net;

import exam.common.model.ExamShift;
import exam.common.model.Role;
import exam.common.model.UserAccount;
import exam.common.protocol.AnswerAckMessage;
import exam.common.protocol.AnswerMessage;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.ExamEndMessage;
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
import exam.common.protocol.ReconnectOkMessage;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.SubmitMessage;
import exam.common.protocol.SubmitOkMessage;
import exam.common.protocol.ViolationMessage;
import exam.server.ServerConfig;
import exam.server.api.ExamService;
import exam.server.api.MessageSender;
import exam.server.api.MonitorService;
import exam.server.api.RateController;
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
 * Client phải LOGIN (hoặc RECONNECT) trước. Sau đó field "session" khác null.
 * Mọi quyền (vai trò, ca thi, đáp án) đều được kiểm tra ở Server, không tin client.
 */
public class ClientHandler {

    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_EXAM_CODE_LENGTH = 32;
    private static final int MAX_NOTICE_LENGTH = 500;

    private final ClientConnection connection;
    private final ServerConfig config;
    private final SessionRegistry sessionRegistry;
    private final MessageSender messageSender;
    private final ExamService examService;
    private final MonitorService monitorService;
    private final RateController rateController;
    private final MessageCodec codec = new MessageCodec();

    /** null cho tới khi LOGIN hoặc RECONNECT thành công. */
    private ClientSession session;
    private int loginFailures = 0;

    public ClientHandler(ClientConnection connection, ServerConfig config, SessionRegistry sessionRegistry,
                         MessageSender messageSender, ExamService examService, MonitorService monitorService,
                         RateController rateController) {
        this.connection = connection;
        this.config = config;
        this.sessionRegistry = sessionRegistry;
        this.messageSender = messageSender;
        this.examService = examService;
        this.monitorService = monitorService;
        this.rateController = rateController;
    }

    /** Vòng đời của một client: đọc cho tới khi mất kết nối, rồi dọn dẹp. */
    public void run() {
        System.out.println("[Net] Client kết nối: " + connection.getRemoteAddress());
        try {
            String line;
            while ((line = connection.readLine()) != null) {
                handleLine(line);
            }
            System.out.println("[Net] Client đóng kết nối: " + describeClient());
        } catch (SocketTimeoutException e) {
            System.out.println("[Net] " + describeClient() + " im lặng quá "
                    + config.heartbeatTimeoutSeconds + " giây (mất heartbeat) -> coi là offline");
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
    // Đăng nhập, nối lại
    // ------------------------------------------------------------------

    private void handleLogin(LoginMessage login) {
        if (session != null) {
            connection.send(new ErrorMessage("ALREADY_LOGGED_IN", "Connection này đã đăng nhập rồi"));
            return;
        }
        String problem = findLoginFormatProblem(login);
        if (problem != null) {
            rejectLogin(problem);
            return;
        }

        // Handler -> Service -> DAO -> SQLite
        UserAccount user = examService.login(login.username, login.password, login.role);
        if (user == null) {
            System.out.println("[Login] Thất bại: " + login.username + " (" + login.role + ")");
            rejectLogin("Sai tài khoản hoặc mật khẩu");
            return;
        }

        String examCode = null;
        if (user.role == Role.STUDENT) {
            examCode = login.examCode.trim().toUpperCase();
            String examProblem = findStudentExamProblem(user, examCode);
            if (examProblem != null) {
                System.out.println("[Login] " + user.username + " bị từ chối: " + examProblem);
                rejectLogin(examProblem);
                return;
            }
        }

        session = sessionRegistry.createSession(user, examCode, connection);
        System.out.println("[Login] OK: " + user.username + " (" + user.role + ") từ " + connection.getRemoteAddress());

        connection.send(new LoginOkMessage(
                session.token, user.id, user.fullName, user.role, System.currentTimeMillis()));

        if (user.role == Role.STUDENT) {
            enableSilenceTimeout();
            announceStudentOnline();
            // Nếu ca đang chạy: gửi EXAM_START (kèm đáp án đã lưu nếu đăng nhập lại) và RULES_CONFIG.
            examService.onStudentOnline(user.id, examCode, session.machineId);
        }
    }

    private void announceStudentOnline() {
        monitorService.onMachineOnline(session.machineId);
        rateController.onMachineOnline(session.machineId);
    }

    /** Kiểm tra định dạng LOGIN (không chạm database). Trả về lý do hoặc null nếu ổn. */
    private String findLoginFormatProblem(LoginMessage login) {
        if (login.username == null || login.password == null || login.role == null) {
            return "Thiếu username, password hoặc role";
        }
        if (login.username.isBlank() || login.username.length() > MAX_USERNAME_LENGTH
                || login.password.length() > MAX_PASSWORD_LENGTH) {
            return "Username hoặc password không hợp lệ";
        }
        if (login.role == Role.STUDENT) {
            if (login.examCode == null || login.examCode.isBlank()) {
                return "Sinh viên phải nhập mã ca thi";
            }
            if (login.examCode.length() > MAX_EXAM_CODE_LENGTH) {
                return "Mã ca thi không hợp lệ";
            }
        }
        return null;
    }

    /** Kiểm tra ca thi của sinh viên. Trả về lý do từ chối hoặc null nếu được vào. */
    private String findStudentExamProblem(UserAccount user, String examCode) {
        ExamShift shift = examService.findShiftByCode(examCode);
        if (shift == null) {
            return "Mã ca thi không tồn tại";
        }
        if (ExamShift.STATUS_ENDED.equals(shift.status)) {
            return "Ca thi đã kết thúc";
        }
        if (!examService.isCandidate(user.id, examCode)) {
            return "Bạn không có trong danh sách thí sinh của ca thi này";
        }
        ClientSession existing = sessionRegistry.findByMachineId(user.username);
        if (existing != null && existing.online && existing.role == Role.STUDENT) {
            return "Tài khoản đang đăng nhập ở máy khác (nếu vừa mất mạng, hãy chờ Server phát hiện hoặc dùng RECONNECT)";
        }
        return null;
    }

    private void rejectLogin(String reason) {
        loginFailures++;
        connection.send(new LoginFailMessage(reason));
        if (loginFailures >= config.maxLoginFailures) {
            // Đoán mật khẩu liên tục: ngắt kết nối. Đọc tiếp sẽ thấy socket đã đóng và vòng lặp kết thúc.
            System.out.println("[Net] " + connection.getRemoteAddress() + " sai LOGIN " + loginFailures + " lần, ngắt kết nối");
            connection.close();
        }
    }

    /** Sinh viên gửi HEARTBEAT đều đặn; im lặng quá heartbeat.timeout thì Server coi là mất kết nối. */
    private void enableSilenceTimeout() {
        try {
            connection.setReadTimeout(config.heartbeatTimeoutSeconds * 1000);
        } catch (IOException e) {
            System.out.println("[Net] Không đặt được timeout cho " + describeClient() + ": " + e.getMessage());
        }
    }

    /**
     * Client mất mạng rồi nối lại: dùng token để lấy lại session cũ (KHÔNG tạo session mới),
     * trả về trạng thái bài thi để client tiếp tục.
     */
    private void handleReconnect(ReconnectMessage reconnect) {
        if (session != null) {
            connection.send(new ErrorMessage("ALREADY_LOGGED_IN", "Connection này đã đăng nhập rồi"));
            return;
        }
        ClientSession found = null;
        if (reconnect.token != null) {
            found = sessionRegistry.findByToken(reconnect.token);
        }
        if (found == null) {
            connection.send(new ErrorMessage("INVALID_TOKEN", "Token không hợp lệ, hãy đăng nhập lại"));
            return;
        }
        long offlineMillis = System.currentTimeMillis() - found.offlineSince;
        if (!found.online && offlineMillis > config.reconnectWindowSeconds * 1000L) {
            connection.send(new ErrorMessage("TOKEN_EXPIRED", "Token đã hết hạn, hãy đăng nhập lại"));
            return;
        }

        // Có thể Server chưa kịp phát hiện connection cũ đã chết: connection mới thay thế nó.
        ClientConnection oldConnection = found.connection;
        sessionRegistry.markOnline(found, connection);
        session = found;
        if (oldConnection != null && oldConnection != connection) {
            oldConnection.close();
        }
        System.out.println("[Reconnect] " + session.machineId + " nối lại bằng token từ " + connection.getRemoteAddress());

        if (session.role == Role.STUDENT) {
            sendReconnectOkForStudent();
        } else {
            connection.send(new ReconnectOkMessage(0L, new java.util.HashMap<>(), System.currentTimeMillis()));
        }
    }

    private void sendReconnectOkForStudent() {
        enableSilenceTimeout();

        connection.send(new ReconnectOkMessage(
                examService.getEndTimeServer(session.examCode),
                examService.getSavedAnswers(session.userId, session.examCode),
                System.currentTimeMillis()));

        announceStudentOnline();

        // Ca đã kết thúc (hoặc sinh viên đã nộp) trong lúc mất mạng thì báo ngay để client khóa bài.
        ExamShift shift = examService.findShiftByCode(session.examCode);
        if (examService.hasSubmitted(session.userId, session.examCode)) {
            connection.send(new ExamEndMessage("SUBMITTED"));
        } else if (shift != null && ExamShift.STATUS_ENDED.equals(shift.status)) {
            connection.send(new ExamEndMessage("TIME_UP"));
        }
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
            if (session.role == Role.STUDENT) {
                monitorService.onHeartbeat(session.machineId, heartbeat);
            }
        } else {
            System.out.println("[Heartbeat] " + session.machineId + " seq=" + heartbeat.seq + " (không có summary)");
        }

        connection.send(new HeartbeatAckMessage(heartbeat.seq, System.currentTimeMillis()));
    }

    private void handleMetricsDetail(MetricsDetailMessage detail) {
        if (!requireRole(Role.STUDENT)) {
            return;
        }
        if (detail.metrics == null) {
            connection.send(new ErrorMessage("BAD_MESSAGE", "METRICS_DETAIL thiếu metrics"));
            return;
        }
        monitorService.onMetricsDetail(session.machineId, detail);
    }

    private void handleViolation(ViolationMessage violation) {
        if (!requireRole(Role.STUDENT)) {
            return;
        }
        if (violation.violationType == null) {
            connection.send(new ErrorMessage("BAD_MESSAGE", "VIOLATION thiếu violationType"));
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
        // Server tự kiểm tra giờ, ca thi, đã nộp chưa; client chỉ nói "câu nào, chọn vị trí nào".
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
        if (monitorService.handlesAction(request.action)) {
            connection.send(monitorService.handleTeacherRequest(request));
        } else {
            connection.send(examService.handleTeacherRequest(session.userId, request));
        }
    }

    private void handleNotice(NoticeMessage notice) {
        if (!requireRole(Role.TEACHER)) {
            return;
        }
        if (notice.target == null || notice.text == null || notice.text.isBlank()
                || notice.text.length() > MAX_NOTICE_LENGTH) {
            connection.send(new ErrorMessage("BAD_MESSAGE", "NOTICE cần target và text (tối đa " + MAX_NOTICE_LENGTH + " ký tự)"));
            return;
        }

        if ("ALL".equals(notice.target)) {
            int sentCount = messageSender.sendToAllStudents(notice);
            System.out.println("[Notice] Gửi cả phòng: " + sentCount + " máy");
            return;
        }

        ClientSession target = sessionRegistry.findByMachineId(notice.target);
        if (target == null || target.role != Role.STUDENT || !target.online) {
            connection.send(new ErrorMessage("MACHINE_OFFLINE", "Máy " + notice.target + " không online"));
            return;
        }
        boolean sent = messageSender.sendToMachine(notice.target, notice);
        System.out.println("[Notice] Gửi " + notice.target + ": " + (sent ? "OK" : "thất bại"));
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
        // Nếu client đã RECONNECT bằng connection mới thì connection này không còn là của session nữa:
        // không được đánh dấu offline.
        if (session != null && session.connection == connection) {
            sessionRegistry.markOffline(session);
            if (session.role == Role.STUDENT) {
                monitorService.onMachineOffline(session.machineId);
            }
            System.out.println("[Net] " + session.machineId + " offline");
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
