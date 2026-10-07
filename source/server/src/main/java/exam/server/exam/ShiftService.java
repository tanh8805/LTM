// Owner: Nguoi2

package exam.server.exam;

import exam.common.model.ExamShift;
import exam.common.model.MlMode;
import exam.common.model.MonitoringRules;
import exam.common.protocol.ExamEndMessage;
import exam.common.protocol.ExamStartMessage;
import exam.common.protocol.RulesConfigMessage;
import exam.common.protocol.TimeSyncMessage;
import exam.server.api.MessageSender;
import exam.server.api.SessionRegistry;
import exam.server.db.AttemptDao;
import exam.server.db.CandidateRecord;
import exam.server.db.ExamDao;
import exam.server.db.ShiftDao;
import exam.server.db.UserDao;
import exam.common.model.UserAccount;
import exam.server.session.ClientSession;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ca thi: tạo, bắt đầu, kết thúc, đồng hồ do Server điều khiển.
 *
 * Vòng đời: CREATED --(giáo viên bấm bắt đầu, hoặc tới giờ hẹn)--> RUNNING --(hết giờ, hoặc giáo viên kết thúc)--> ENDED.
 * Khi RUNNING hết giờ, Server tự chốt bài của mọi thí sinh chưa nộp rồi gửi EXAM_END.
 * Client không có quyền quyết định thời gian: giờ hết bài luôn là start_time + duration trong database.
 */
public class ShiftService {

    private static final int MAX_CANDIDATE_LINES = 2000;

    /** Kết quả tạo ca thi. */
    public static class CreateShiftResult {
        public int shiftId;
        public String code;
        public int candidatesAdded;
        public final List<String> errors = new ArrayList<>();
    }

    private final ShiftDao shiftDao;
    private final ExamDao examDao;
    private final UserDao userDao;
    private final AttemptDao attemptDao;
    private final AttemptService attemptService;
    private final MessageSender messageSender;
    private final SessionRegistry sessionRegistry;
    private final MlMode mlMode;
    private final MonitoringRules defaultRules;
    private final Object lock;
    private final SecureRandom seedGenerator = new SecureRandom();

    public ShiftService(ShiftDao shiftDao, ExamDao examDao, UserDao userDao, AttemptDao attemptDao,
                        AttemptService attemptService, MessageSender messageSender, SessionRegistry sessionRegistry,
                        MlMode mlMode, MonitoringRules defaultRules, Object lock) {
        this.shiftDao = shiftDao;
        this.examDao = examDao;
        this.userDao = userDao;
        this.attemptDao = attemptDao;
        this.attemptService = attemptService;
        this.messageSender = messageSender;
        this.sessionRegistry = sessionRegistry;
        this.mlMode = mlMode;
        this.defaultRules = defaultRules;
        this.lock = lock;
    }

    // ------------------------------------------------------------------
    // Tạo ca thi
    // ------------------------------------------------------------------

    /**
     * Tạo ca thi cho một đề.
     *
     * @param code           mã ca; null hoặc rỗng thì Server tự sinh (CA001, CA002, ...)
     * @param startTime      giờ hẹn tự bắt đầu (epoch milli giây), 0 nếu giáo viên bắt đầu thủ công
     * @param candidatesCsv  danh sách thí sinh: mỗi dòng một mã sinh viên (cột đầu), tiêu đề tùy chọn
     * @param rules          luật giám sát; null thì dùng luật mặc định trong config
     */
    public CreateShiftResult createShift(int examId, String code, int durationSeconds, long startTime,
                                         String candidatesCsv, MonitoringRules rules) {
        try {
            if (examDao.findById(examId) == null) {
                throw new IllegalArgumentException("Không có đề thi id=" + examId);
            }
            if (durationSeconds <= 0) {
                throw new IllegalArgumentException("Thời lượng ca thi phải lớn hơn 0");
            }
            if (startTime < 0) {
                throw new IllegalArgumentException("Giờ bắt đầu không hợp lệ");
            }

            String shiftCode = chooseCode(code);
            CreateShiftResult result = new CreateShiftResult();
            result.code = shiftCode;

            List<CandidateRecord> candidates = parseCandidates(candidatesCsv, result.errors);
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("Ca thi cần ít nhất 1 thí sinh hợp lệ. " + String.join("; ", result.errors));
            }

            MonitoringRules shiftRules = rules != null ? completeRules(rules) : defaultRules;
            result.shiftId = shiftDao.insertShift(shiftCode, examId, startTime, durationSeconds, shiftRules, candidates);
            result.candidatesAdded = candidates.size();
            System.out.println("[Exam] Tạo ca " + shiftCode + ": " + candidates.size() + " thí sinh, " + durationSeconds + " giây");
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Không tạo được ca thi", e);
        }
    }

    private String chooseCode(String requestedCode) throws SQLException {
        if (requestedCode == null || requestedCode.isBlank()) {
            for (int i = 1; i < 10000; i++) {
                String candidateCode = String.format("CA%03d", i);
                if (shiftDao.findByCode(candidateCode) == null) {
                    return candidateCode;
                }
            }
            throw new IllegalStateException("Không còn mã ca thi trống");
        }

        String code = requestedCode.trim().toUpperCase();
        if (!code.matches("[A-Z0-9_-]{3,20}")) {
            throw new IllegalArgumentException("Mã ca thi chỉ gồm chữ, số, '-' hoặc '_' (3 đến 20 ký tự)");
        }
        if (shiftDao.findByCode(code) != null) {
            throw new IllegalArgumentException("Mã ca thi " + code + " đã tồn tại");
        }
        return code;
    }

    /** Mỗi dòng: mã sinh viên (cột đầu). Dòng lỗi được ghi vào errors và bỏ qua. */
    private List<CandidateRecord> parseCandidates(String csvText, List<String> errors) throws SQLException {
        List<CandidateRecord> candidates = new ArrayList<>();
        if (csvText == null || csvText.isBlank()) {
            return candidates;
        }
        if (csvText.startsWith("﻿")) {
            csvText = csvText.substring(1);
        }

        String[] lines = csvText.split("\\r?\\n");
        if (lines.length > MAX_CANDIDATE_LINES) {
            throw new IllegalArgumentException("Danh sách thí sinh quá dài (tối đa " + MAX_CANDIDATE_LINES + " dòng)");
        }

        Set<String> seenCodes = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String studentCode;
            try {
                studentCode = Csv.parseLine(lines[i]).get(0);
            } catch (IllegalArgumentException e) {
                errors.add("dòng " + (i + 1) + ": " + e.getMessage());
                continue;
            }
            if (i == 0 && looksLikeHeader(studentCode)) {
                continue;
            }
            if (!seenCodes.add(studentCode.toUpperCase())) {
                errors.add("dòng " + (i + 1) + ": mã " + studentCode + " bị trùng");
                continue;
            }

            UserAccount student = userDao.findStudentByUsername(studentCode);
            if (student == null) {
                errors.add("dòng " + (i + 1) + ": không có sinh viên mã " + studentCode);
                continue;
            }
            CandidateRecord candidate = new CandidateRecord();
            candidate.studentId = student.id;
            candidate.username = student.username;
            candidate.fullName = student.fullName;
            candidate.shuffleSeed = seedGenerator.nextLong();
            candidates.add(candidate);
        }
        return candidates;
    }

    private boolean looksLikeHeader(String firstCell) {
        String lower = firstCell.toLowerCase();
        return lower.equals("student_code") || lower.equals("username") || lower.equals("mã sv") || lower.equals("masv");
    }

    /** Giáo viên có thể gửi luật thiếu một số trường: bổ sung từ luật mặc định. */
    private MonitoringRules completeRules(MonitoringRules rules) {
        if (rules.processAllowlist == null) {
            rules.processAllowlist = new ArrayList<>();
        }
        if (rules.processDenylist == null) {
            rules.processDenylist = new ArrayList<>(defaultRules.processDenylist);
        }
        if (rules.blockedDomains == null) {
            rules.blockedDomains = new ArrayList<>(defaultRules.blockedDomains);
        }
        if (rules.focusLossThreshold <= 0) {
            rules.focusLossThreshold = defaultRules.focusLossThreshold;
        }
        // Các tham số ML và chu kỳ đo luôn lấy từ config của Server, giáo viên không đổi được.
        rules.metricsIntervalSeconds = defaultRules.metricsIntervalSeconds;
        rules.ifTrainingSeconds = defaultRules.ifTrainingSeconds;
        rules.ifThresholdMargin = defaultRules.ifThresholdMargin;
        rules.ifConsecutiveRequired = defaultRules.ifConsecutiveRequired;
        return rules;
    }

    // ------------------------------------------------------------------
    // Bắt đầu, kết thúc
    // ------------------------------------------------------------------

    /** Bắt đầu ca ngay bây giờ: đặt RUNNING, tính giờ hết bài, gửi EXAM_START cho thí sinh đang online. */
    public void startShift(String code) {
        synchronized (lock) {
            try {
                ExamShift shift = shiftDao.findByCode(code);
                if (shift == null) {
                    throw new IllegalArgumentException("Không có ca thi " + code);
                }
                if (!ExamShift.STATUS_CREATED.equals(shift.status)) {
                    throw new IllegalArgumentException("Ca thi " + code + " đang ở trạng thái " + shift.status + ", không thể bắt đầu");
                }

                long now = System.currentTimeMillis();
                shiftDao.updateStatus(shift.id, ExamShift.STATUS_RUNNING, now);
                shift.status = ExamShift.STATUS_RUNNING;
                shift.startTimeServer = now;
                System.out.println("[Exam] Ca " + code + " bắt đầu, hết giờ lúc " + shift.getEndTimeServer());

                for (CandidateRecord candidate : shiftDao.findCandidates(shift.id)) {
                    ClientSession session = findOnlineSession(candidate, code);
                    if (session != null) {
                        sendExamStart(shift, candidate);
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Không bắt đầu được ca thi", e);
            }
        }
    }

    /**
     * Kết thúc ca: Server tự chốt bài mọi thí sinh chưa nộp, đổi trạng thái ENDED, gửi EXAM_END.
     * Gọi lại khi ca đã ENDED thì không làm gì (idempotent).
     */
    public void endShift(String code, String reason) {
        synchronized (lock) {
            try {
                ExamShift shift = shiftDao.findByCode(code);
                if (shift == null) {
                    throw new IllegalArgumentException("Không có ca thi " + code);
                }
                if (ExamShift.STATUS_ENDED.equals(shift.status)) {
                    return;
                }

                if (ExamShift.STATUS_RUNNING.equals(shift.status)) {
                    attemptService.autoSubmitAll(shift);
                }
                shiftDao.updateStatus(shift.id, ExamShift.STATUS_ENDED, shift.startTimeServer);
                System.out.println("[Exam] Ca " + code + " kết thúc (" + reason + ")");

                for (CandidateRecord candidate : shiftDao.findCandidates(shift.id)) {
                    ClientSession session = findOnlineSession(candidate, code);
                    if (session != null) {
                        session.connection.send(new ExamEndMessage(reason));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Không kết thúc được ca thi", e);
            }
        }
    }

    /** Tự bắt đầu các ca đã đặt giờ hẹn mà đã tới giờ. Được gọi mỗi giây. */
    public void startDueShifts() {
        try {
            for (ExamShift shift : shiftDao.findDueToStart(System.currentTimeMillis())) {
                startShift(shift.code);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không kiểm tra được ca tới giờ bắt đầu", e);
        }
    }

    /** Tự chốt các ca đã hết giờ. Được gọi mỗi giây. */
    public void finishExpiredShifts() {
        try {
            long now = System.currentTimeMillis();
            for (ExamShift shift : shiftDao.findRunning()) {
                if (now >= shift.getEndTimeServer()) {
                    endShift(shift.code, "TIME_UP");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không kiểm tra được ca hết giờ", e);
        }
    }

    // ------------------------------------------------------------------
    // Đồng hồ, đăng nhập giữa ca
    // ------------------------------------------------------------------

    /** Gửi TIME_SYNC cho mọi thí sinh đang online và chưa nộp trong các ca đang chạy. */
    public void sendTimeSync() {
        try {
            long now = System.currentTimeMillis();
            for (ExamShift shift : shiftDao.findRunning()) {
                long remainingSeconds = Math.max(0, (shift.getEndTimeServer() - now) / 1000);
                for (CandidateRecord candidate : shiftDao.findCandidates(shift.id)) {
                    ClientSession session = findOnlineSession(candidate, shift.code);
                    if (session != null && attemptDao.findResult(shift.id, candidate.studentId) == null) {
                        session.connection.send(new TimeSyncMessage(now, remainingSeconds));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không gửi được TIME_SYNC", e);
        }
    }

    /** Sinh viên vừa đăng nhập: nếu ca đang chạy thì gửi đề (hoặc báo đã nộp). */
    public void onStudentOnline(int studentId, String examCode) {
        synchronized (lock) {
            try {
                ExamShift shift = shiftDao.findByCode(examCode);
                if (shift == null || !ExamShift.STATUS_RUNNING.equals(shift.status)) {
                    return;
                }
                CandidateRecord candidate = shiftDao.findCandidate(shift.id, studentId);
                if (candidate == null) {
                    return;
                }
                ClientSession session = findOnlineSession(candidate, examCode);
                if (session == null) {
                    return;
                }
                if (attemptDao.findResult(shift.id, studentId) != null) {
                    session.connection.send(new ExamEndMessage("SUBMITTED"));
                } else {
                    sendExamStart(shift, candidate);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Không gửi được đề cho sinh viên", e);
            }
        }
    }

    private void sendExamStart(ExamShift shift, CandidateRecord candidate) throws SQLException {
        ExamPaper paper = attemptService.buildPaper(shift, candidate);
        Map<Integer, Integer> savedAnswers = attemptDao.findAnswers(shift.id, candidate.studentId);

        messageSender.sendToMachine(candidate.username,
                new ExamStartMessage(paper.toViews(), shift.getEndTimeServer(), savedAnswers, shift.examTitle));
        messageSender.sendToMachine(candidate.username, new RulesConfigMessage(shift.rules, mlMode));
    }

    /** Session đang online của thí sinh và đăng nhập đúng mã ca này. Trả về null nếu không có. */
    private ClientSession findOnlineSession(CandidateRecord candidate, String examCode) {
        ClientSession session = sessionRegistry.findByMachineId(candidate.username);
        if (session == null || !session.online || !examCode.equals(session.examCode)) {
            return null;
        }
        return session;
    }

    // ------------------------------------------------------------------
    // Đọc dữ liệu cho giáo viên
    // ------------------------------------------------------------------

    public List<AttemptDao.ResultRow> getResults(String code) {
        try {
            ExamShift shift = shiftDao.findByCode(code);
            if (shift == null) {
                throw new IllegalArgumentException("Không có ca thi " + code);
            }
            return attemptDao.findResultRows(shift.id);
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc được bảng điểm", e);
        }
    }

    public String exportResultsCsv(String code) {
        List<AttemptDao.ResultRow> rows = getResults(code);

        StringBuilder csv = new StringBuilder("student_code,full_name,status,score,correct,total,answered,submitted_at\n");
        for (AttemptDao.ResultRow row : rows) {
            String status = "NOT_SUBMITTED";
            if (row.submitted) {
                status = row.autoSubmitted ? "AUTO_SUBMITTED" : "SUBMITTED";
            }
            csv.append(Csv.escape(row.studentCode)).append(',')
                    .append(Csv.escape(row.fullName)).append(',')
                    .append(status).append(',')
                    .append(row.submitted ? String.valueOf(row.score) : "").append(',')
                    .append(row.submitted ? String.valueOf(row.correctCount) : "").append(',')
                    .append(row.submitted ? String.valueOf(row.totalQuestions) : "").append(',')
                    .append(row.submitted ? String.valueOf(row.answeredCount) : "").append(',')
                    .append(row.submitted ? String.valueOf(row.submittedAt) : "").append('\n');
        }
        return csv.toString();
    }
}
