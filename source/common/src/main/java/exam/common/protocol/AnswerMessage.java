// Owner: Nguoi1

package exam.common.protocol;

/**
 * ANSWER (Client → Server): Gửi đáp án ngay khi sinh viên chọn.
 */
public class AnswerMessage extends Message {
    /** Số thứ tự tăng dần của ANSWER */
    public int seq;
    public int questionId;
    /** Vị trí 0..3 trong options ĐÃ TRỘN của QuestionView */
    public int choice;

    public AnswerMessage() {
        super(MessageType.ANSWER);
    }

    public AnswerMessage(int seq, int questionId, int choice) {
        this();
        this.seq = seq;
        this.questionId = questionId;
        this.choice = choice;
    }
}
