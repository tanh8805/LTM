// Owner: Nguoi1

package exam.server.api;

import exam.common.model.ExamShift;
import exam.common.model.QuestionView;
import exam.common.model.Role;
import exam.common.model.UserAccount;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ thi: đăng nhập, ngân hàng câu hỏi, đề, ca thi, làm bài, chấm điểm, đồng hồ.
 *
 * Ai cài đặt: Nguoi2 (exam.server.exam.ExamServiceImpl)
 * Ai gọi: Nguoi1 (ClientHandler khi nhận LOGIN, RECONNECT, ANSWER, SUBMIT, REQUEST; PeriodicJobs mỗi giây)
 *
 * Các method KHÔNG ném checked exception. Lỗi database được bọc thành IllegalStateException;
 * ClientHandler sẽ bắt và trả ERROR INTERNAL_ERROR cho client.
 *
 * Nguyên tắc: Server là nơi duy nhất quyết định giờ hết bài, đáp án đúng và điểm số.
 * Client chỉ gửi lựa chọn của sinh viên (ANSWER); điểm và thời gian luôn do Server tính.
 */
public interface ExamService {

    /** Kiểm tra tài khoản. Trả về người dùng, hoặc null nếu sai username/password. */
    UserAccount login(String username, String password, Role role);

    /** Tìm ca thi theo mã ca (ví dụ "CA001"). Trả về null nếu không có. */
    ExamShift findShiftByCode(String examCode);

    /** true nếu sinh viên nằm trong danh sách thí sinh của ca thi. */
    boolean isCandidate(int studentId, String examCode);

    /** Đề thi đã trộn câu hỏi và đáp án riêng cho một sinh viên (không có đáp án đúng). */
    List<QuestionView> prepareQuestionsForStudent(int studentId, String examCode);

    /** Giờ hết bài của ca thi (epoch milli giây, đồng hồ Server). 0 nếu ca không tồn tại hoặc chưa bắt đầu. */
    long getEndTimeServer(String examCode);

    /** Các đáp án đã lưu của sinh viên: questionId -> choice. Rỗng nếu chưa trả lời. */
    Map<Integer, Integer> getSavedAnswers(int studentId, String examCode);

    /** true nếu sinh viên đã nộp bài (hoặc Server đã tự chốt bài). */
    boolean hasSubmitted(int studentId, String examCode);

    /**
     * Sinh viên vừa đăng nhập. Nếu ca đang chạy và sinh viên là thí sinh chưa nộp thì gửi EXAM_START
     * (kèm RULES_CONFIG); nếu đã nộp thì gửi EXAM_END.
     */
    void onStudentOnline(int studentId, String examCode, String machineId);

    /** Lưu đáp án sinh viên chọn. Trả về false nếu bị từ chối (hết giờ, đã nộp, câu hỏi không thuộc đề, ...). */
    boolean saveAnswer(int studentId, String examCode, int questionId, int choice);

    /** Sinh viên nộp bài: chốt bài và chấm điểm. Trả về số câu đã trả lời. Nộp lần hai không đổi kết quả. */
    int submit(int studentId, String examCode);

    /** Xử lý REQUEST của giáo viên (câu hỏi, đề, ca thi, điểm). Danh sách action: docs/PROTOCOL.md mục 6. */
    ResponseMessage handleTeacherRequest(int teacherId, RequestMessage request);

    /** Bảng điểm của một ca thi dạng CSV (để giáo viên xuất file). */
    String exportScoresCsv(String examCode);

    /** Tự bắt đầu các ca đã đặt giờ hẹn và đã tới giờ. Được gọi mỗi giây. */
    void startDueShifts();

    /** Server tự chốt bài và gửi EXAM_END cho các ca đã hết giờ. Được gọi mỗi giây. */
    void finishExpiredExams();

    /** Gửi TIME_SYNC cho sinh viên đang thi để đồng hồ đếm ngược khớp đồng hồ Server. */
    void sendTimeSync();
}
