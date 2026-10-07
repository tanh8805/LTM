// Owner: Nguoi1

package exam.common.model;

import java.util.List;

/**
 * Câu hỏi trong ngân hàng câu hỏi (có đáp án đúng).
 * Chỉ dùng trong Server và Teacher. KHÔNG gửi cho Student (Student nhận QuestionView).
 */
public class Question {
    public int id;
    public String content;
    /** Đúng 4 đáp án A, B, C, D theo thứ tự gốc. */
    public List<String> options;
    /** Vị trí đáp án đúng trong options: 0=A, 1=B, 2=C, 3=D. */
    public int correctIndex;

    public Question() {
    }

    public Question(int id, String content, List<String> options, int correctIndex) {
        this.id = id;
        this.content = content;
        this.options = options;
        this.correctIndex = correctIndex;
    }
}
