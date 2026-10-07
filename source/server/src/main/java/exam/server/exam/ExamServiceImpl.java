// Owner: Nguoi2

package exam.server.exam;

import exam.common.model.ExamShift;
import exam.common.model.QuestionView;
import exam.common.model.Role;
import exam.common.model.UserAccount;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import exam.server.api.ExamService;
import exam.server.api.MessageSender;
import exam.server.api.SessionRegistry;
import exam.server.db.QuestionDao;
import exam.server.db.UserDao;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Nghiệp vụ thi. HIỆN CHỈ login() chạy thật (đọc SQLite); các method còn lại là STUB trả giá trị mặc định.
 */
public class ExamServiceImpl implements ExamService {

    private final UserDao userDao;
    // Các field dưới đây chưa dùng, sẽ dùng khi cài đặt các TODO bên dưới.
    private final QuestionDao questionDao;
    private final MessageSender messageSender;
    private final SessionRegistry sessionRegistry;

    public ExamServiceImpl(UserDao userDao, QuestionDao questionDao,
                           MessageSender messageSender, SessionRegistry sessionRegistry) {
        this.userDao = userDao;
        this.questionDao = questionDao;
        this.messageSender = messageSender;
        this.sessionRegistry = sessionRegistry;
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
        // TODO(Nguoi2): Tìm ca thi trong bảng exam_shifts theo code. Tạo ExamShiftDao.
        return null;
    }

    @Override
    public List<QuestionView> prepareQuestionsForStudent(int studentId, String examCode) {
        // TODO(Nguoi2): Lấy câu hỏi của đề, trộn thứ tự câu và thứ tự đáp án bằng shuffle_seed riêng của sinh viên
        //  (lưu trong shift_students để reconnect ra cùng thứ tự). Chỉ trả QuestionView, không lộ đáp án đúng.
        return new ArrayList<>();
    }

    @Override
    public long getEndTimeServer(String examCode) {
        // TODO(Nguoi2): endTimeServer = start_time + duration_minutes của ca thi.
        return 0L;
    }

    @Override
    public boolean saveAnswer(int studentId, String examCode, int questionId, int choice) {
        // TODO(Nguoi2): Kiểm tra ca đang chạy và chưa hết giờ, rồi ghi vào bảng answers (ghi đè nếu chọn lại).
        return false;
    }

    @Override
    public int submit(int studentId, String examCode) {
        // TODO(Nguoi2): Chấm điểm (quy đổi choice đã trộn về đáp án gốc), ghi bảng results, trả số câu đã trả lời.
        return 0;
    }

    @Override
    public ResponseMessage handleTeacherRequest(RequestMessage request) {
        // TODO(Nguoi2): Xử lý các action của giáo viên: câu hỏi (thêm/sửa/xóa/nhập CSV), tạo đề, tạo ca thi,
        //  bắt đầu/kết thúc ca, xem điểm. Danh sách action ghi trong docs/PROTOCOL.md khi chốt.
        return new ResponseMessage(request.requestId, false, null, "NOT_IMPLEMENTED: " + request.action);
    }

    @Override
    public String exportScoresCsv(String examCode) {
        // TODO(Nguoi2): Ghép bảng điểm từ bảng results: mỗi dòng "mã SV,họ tên,điểm".
        return "student_code,full_name,score\n";
    }

    @Override
    public void finishExpiredExams() {
        // TODO(Nguoi2): Tìm ca RUNNING đã hết giờ -> chấm bài của những sinh viên chưa nộp,
        //  đổi status ENDED, gửi EXAM_END cho sinh viên (messageSender.sendToMachine).
    }
}
