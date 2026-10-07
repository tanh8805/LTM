// Owner: Nguoi1

package exam.common.protocol;

/**
 * SUBMIT_OK (Server → Client): Server đã nhận bài nộp.
 */
public class SubmitOkMessage extends Message {
    /** Số câu đã trả lời */
    public int answeredCount;

    public SubmitOkMessage() {
        super(MessageType.SUBMIT_OK);
    }

    public SubmitOkMessage(int answeredCount) {
        this();
        this.answeredCount = answeredCount;
    }
}
