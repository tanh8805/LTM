// Owner: Nguoi1

package exam.server.api;

import exam.common.model.ExamShift;
import exam.common.model.QuestionView;
import exam.common.model.Role;
import exam.common.model.UserAccount;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import java.util.List;

/**
 * Nghiệp vụ thi: đăng nhập, ngân hàng câu hỏi, đề, ca thi, làm bài, chấm điểm.
 *
 * Ai cài đặt: Nguoi2 (exam.server.exam.ExamServiceImpl)
 * Ai gọi: Nguoi1 (ClientHandler khi nhận LOGIN, ANSWER, SUBMIT, REQUEST; PeriodicJobs mỗi giây)
 *
 * Các method KHÔNG ném checked exception. Lỗi database nên được bọc thành IllegalStateException;
 * ClientHandler sẽ bắt và trả ERROR INTERNAL_ERROR cho client.
 */
public interface ExamService {

    /** Kiểm tra tài khoản. Trả về người dùng, hoặc null nếu sai username/password. */
    UserAccount login(String username, String password, Role role);

    /** Tìm ca thi theo mã ca (ví dụ "CA001"). Trả về null nếu không có. */
    ExamShift findShiftByCode(String examCode);

    /** Đề thi đã trộn câu hỏi và đáp án riêng cho một sinh viên (không có đáp án đúng). */
    List<QuestionView> prepareQuestionsForStudent(int studentId, String examCode);

    /** Giờ hết bài của ca thi (epoch milli giây, đồng hồ Server). */
    long getEndTimeServer(String examCode);

    /** Lưu đáp án sinh viên chọn. Trả về true nếu lưu được. */
    boolean saveAnswer(int studentId, String examCode, int questionId, int choice);

    /** Sinh viên nộp bài: chốt bài và chấm điểm. Trả về số câu đã trả lời. */
    int submit(int studentId, String examCode);

    /** Xử lý REQUEST của giáo viên (quản lý câu hỏi, đề, ca thi, xem điểm, ...). */
    ResponseMessage handleTeacherRequest(RequestMessage request);

    /** Bảng điểm của một ca thi dạng CSV (để giáo viên xuất file). */
    String exportScoresCsv(String examCode);

    /** Server tự chốt bài các ca đã hết giờ. Được gọi mỗi giây. */
    void finishExpiredExams();
}
