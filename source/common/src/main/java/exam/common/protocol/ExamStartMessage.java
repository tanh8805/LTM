// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.QuestionView;
import java.util.List;

/**
 * EXAM_START (Server → Client): Bắt đầu làm bài: gửi đề đã trộn riêng cho sinh viên này.
 */
public class ExamStartMessage extends Message {
    /** Câu hỏi và đáp án đã trộn, không có đáp án đúng */
    public List<QuestionView> questions;
    /** Giờ hết bài (epoch milli giây, đồng hồ Server) */
    public long endTimeServer;

    public ExamStartMessage() {
        super(MessageType.EXAM_START);
    }

    public ExamStartMessage(List<QuestionView> questions, long endTimeServer) {
        this();
        this.questions = questions;
        this.endTimeServer = endTimeServer;
    }
}
