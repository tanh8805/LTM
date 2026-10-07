// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.QuestionView;
import java.util.List;
import java.util.Map;

/**
 * EXAM_START (Server → Client): Bắt đầu làm bài: gửi đề đã trộn riêng cho sinh viên này.
 */
public class ExamStartMessage extends Message {
    /** Câu hỏi và đáp án đã trộn, không có đáp án đúng */
    public List<QuestionView> questions;
    /** Giờ hết bài (epoch milli giây, đồng hồ Server) */
    public long endTimeServer;
    /** Các đáp án đã lưu (questionId -> choice) để khôi phục khi đăng nhập lại giữa ca. Có thể rỗng. */
    public Map<Integer, Integer> answers;
    /** Tên đề thi, chỉ để hiển thị. */
    public String title;

    public ExamStartMessage() {
        super(MessageType.EXAM_START);
    }

    public ExamStartMessage(List<QuestionView> questions, long endTimeServer) {
        this();
        this.questions = questions;
        this.endTimeServer = endTimeServer;
    }

    public ExamStartMessage(List<QuestionView> questions, long endTimeServer,
                            Map<Integer, Integer> answers, String title) {
        this(questions, endTimeServer);
        this.answers = answers;
        this.title = title;
    }
}
