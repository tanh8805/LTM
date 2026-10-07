// Owner: Nguoi2

package exam.server.exam;

import exam.common.model.ExamShift;
import exam.common.model.MlMode;
import exam.common.model.MonitoringRules;
import exam.common.model.QuestionView;
import exam.common.model.Role;
import exam.common.model.UserAccount;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import exam.server.api.ExamService;
import exam.server.api.MessageSender;
import exam.server.api.SessionRegistry;
import exam.server.db.AttemptDao;
import exam.server.db.CandidateRecord;
import exam.server.db.Database;
import exam.server.db.ExamDao;
import exam.server.db.QuestionDao;
import exam.server.db.ShiftDao;
import exam.server.db.UserDao;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ thi. Lớp này chỉ ghép các service nhỏ lại và đứng sau interface ExamService:
 *
 *   QuestionBankService  - ngân hàng câu hỏi, nhập CSV
 *   ExamCreationService  - tạo đề (chọn tay / ngẫu nhiên)
 *   ShiftService         - ca thi, bắt đầu/kết thúc, đồng hồ do Server điều khiển
 *   AttemptService       - lưu đáp án, nộp bài, chấm điểm
 *   TeacherRequestHandler - đổi REQUEST của giáo viên thành lời gọi các service trên
 */
public class ExamServiceImpl implements ExamService {

    private final UserDao userDao;
    private final ShiftDao shiftDao;
    private final AttemptService attemptService;
    private final ShiftService shiftService;
    private final TeacherRequestHandler teacherRequestHandler;

    public ExamServiceImpl(Database database, MessageSender messageSender, SessionRegistry sessionRegistry,
                           MlMode mlMode, MonitoringRules defaultRules) {
        this.userDao = new UserDao(database);
        this.shiftDao = new ShiftDao(database);
        QuestionDao questionDao = new QuestionDao(database);
        ExamDao examDao = new ExamDao(database);
        AttemptDao attemptDao = new AttemptDao(database);

        // Cùng một lock cho nộp bài và chốt bài để hai việc đó không chạy chồng lên nhau.
        Object lock = new Object();
        this.attemptService = new AttemptService(shiftDao, examDao, questionDao, attemptDao, lock);
        this.shiftService = new ShiftService(shiftDao, examDao, userDao, attemptDao, attemptService,
                messageSender, sessionRegistry, mlMode, defaultRules, lock);

        QuestionBankService questionBank = new QuestionBankService(questionDao);
        ExamCreationService examCreation = new ExamCreationService(examDao, questionDao);
        this.teacherRequestHandler = new TeacherRequestHandler(questionBank, examCreation, shiftService, examDao, shiftDao);
    }

    @Override
    public UserAccount login(String username, String password, Role role) {
        try {
            return userDao.findByUsernameAndPassword(role, username, password);
        } catch (SQLException e) {
            // Bọc thành unchecked: ClientHandler sẽ trả ERROR INTERNAL_ERROR cho client.
            throw new IllegalStateException("Không đọc được tài khoản từ database", e);
        }
    }

    @Override
    public ExamShift findShiftByCode(String examCode) {
        try {
            return shiftDao.findByCode(examCode);
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc được ca thi", e);
        }
    }

    @Override
    public boolean isCandidate(int studentId, String examCode) {
        try {
            ExamShift shift = shiftDao.findByCode(examCode);
            return shift != null && shiftDao.findCandidate(shift.id, studentId) != null;
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc được danh sách thí sinh", e);
        }
    }

    @Override
    public List<QuestionView> prepareQuestionsForStudent(int studentId, String examCode) {
        try {
            ExamShift shift = shiftDao.findByCode(examCode);
            if (shift == null) {
                return new ArrayList<>();
            }
            CandidateRecord candidate = shiftDao.findCandidate(shift.id, studentId);
            if (candidate == null) {
                return new ArrayList<>();
            }
            return attemptService.buildPaper(shift, candidate).toViews();
        } catch (SQLException e) {
            throw new IllegalStateException("Không chuẩn bị được đề thi", e);
        }
    }

    @Override
    public long getEndTimeServer(String examCode) {
        ExamShift shift = findShiftByCode(examCode);
        if (shift == null || ExamShift.STATUS_CREATED.equals(shift.status)) {
            return 0L;
        }
        return shift.getEndTimeServer();
    }

    @Override
    public Map<Integer, Integer> getSavedAnswers(int studentId, String examCode) {
        return attemptService.getSavedAnswers(studentId, examCode);
    }

    @Override
    public boolean hasSubmitted(int studentId, String examCode) {
        return attemptService.hasSubmitted(studentId, examCode);
    }

    @Override
    public void onStudentOnline(int studentId, String examCode, String machineId) {
        shiftService.onStudentOnline(studentId, examCode);
    }

    @Override
    public boolean saveAnswer(int studentId, String examCode, int questionId, int choice) {
        return attemptService.saveAnswer(studentId, examCode, questionId, choice);
    }

    @Override
    public int submit(int studentId, String examCode) {
        return attemptService.submit(studentId, examCode);
    }

    @Override
    public ResponseMessage handleTeacherRequest(int teacherId, RequestMessage request) {
        return teacherRequestHandler.handle(teacherId, request);
    }

    @Override
    public String exportScoresCsv(String examCode) {
        return shiftService.exportResultsCsv(examCode);
    }

    @Override
    public void startDueShifts() {
        shiftService.startDueShifts();
    }

    @Override
    public void finishExpiredExams() {
        shiftService.finishExpiredShifts();
    }

    @Override
    public void sendTimeSync() {
        shiftService.sendTimeSync();
    }
}
