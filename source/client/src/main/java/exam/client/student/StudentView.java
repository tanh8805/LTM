// Owner: Nguoi2

package exam.client.student;

import exam.common.model.QuestionView;
import java.util.List;
import java.util.Map;

/**
 * Màn hình của sinh viên, nhìn từ phía StudentClient. StudentFrame (Swing) cài đặt interface này.
 * Tách ra để phần logic mạng (StudentClient) chạy và test được mà không cần màn hình.
 *
 * Các method được gọi từ thread đọc socket, nên bản cài đặt Swing phải tự chuyển sang thread giao diện.
 */
public interface StudentView {

    /** "Connected", "Disconnected", "Mất kết nối, đang nối lại..." ... */
    void showStatus(String text);

    /** Kết quả đăng nhập. ok = false thì message là lý do. */
    void showLogin(boolean ok, String message);

    /** Heartbeat thứ seq đã được Server xác nhận. */
    void showHeartbeat(int seq);

    /** Bắt đầu làm bài (hoặc đăng nhập lại giữa ca). savedAnswers: các đáp án Server đã lưu, questionId -> vị trí chọn. */
    void showExam(String title, List<QuestionView> questions, Map<Integer, Integer> savedAnswers);

    /** Sau reconnect: đáp án Server đang giữ, để UI hiển thị lại đúng. */
    void showRestoredAnswers(Map<Integer, Integer> answers);

    /** Server đã lưu đáp án của câu này. */
    void showAnswerSaved(int questionId);

    /** Server từ chối đáp án (đã hết giờ hoặc đã nộp). */
    void showAnswerRejected(int questionId);

    /** Server đã nhận bài nộp. */
    void showSubmitted(int answeredCount);

    /** Ca thi kết thúc (TIME_UP, TEACHER_ENDED, SUBMITTED): khóa bài. */
    void showExamEnded(String reason);

    /** Thông báo của giáo viên (NOTICE). */
    void showNotice(String text);

    /** Tóm tắt thống kê phòng (ROOM_STATS). */
    void showRoomStats(String summary);

    /** Dòng nhật ký để debug. */
    void appendLog(String text);
}
