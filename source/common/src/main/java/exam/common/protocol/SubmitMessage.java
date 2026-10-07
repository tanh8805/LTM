// Owner: Nguoi1

package exam.common.protocol;

/**
 * SUBMIT (Client → Server): Sinh viên nộp bài.
 */
public class SubmitMessage extends Message {
    /** Số thứ tự message của sinh viên (tiếp sau ANSWER cuối) */
    public int seq;

    public SubmitMessage() {
        super(MessageType.SUBMIT);
    }

    public SubmitMessage(int seq) {
        this();
        this.seq = seq;
    }
}
