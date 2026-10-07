// Owner: Nguoi1

package exam.common.model;

import java.util.List;

/**
 * Câu hỏi gửi cho Student trong EXAM_START: KHÔNG có đáp án đúng.
 *
 * Server đã trộn thứ tự câu hỏi và thứ tự đáp án riêng cho từng sinh viên.
 * Vì vậy "choice" trong message ANSWER là vị trí trong list options NÀY (0..3),
 * không phải vị trí trong câu hỏi gốc. Server tự quy đổi lại khi chấm điểm.
 */
public class QuestionView {
    public int questionId;
    public String content;
    /** 4 đáp án theo thứ tự đã trộn. */
    public List<String> options;

    public QuestionView() {
    }

    public QuestionView(int questionId, String content, List<String> options) {
        this.questionId = questionId;
        this.content = content;
        this.options = options;
    }
}
