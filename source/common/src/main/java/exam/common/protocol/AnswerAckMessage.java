// Owner: Nguoi1

package exam.common.protocol;

/**
 * ANSWER_ACK (Server → Client): Server xác nhận đã lưu đáp án.
 */
public class AnswerAckMessage extends Message {
    /** seq của ANSWER được xác nhận */
    public int seq;
    public int questionId;
    public boolean saved;

    public AnswerAckMessage() {
        super(MessageType.ANSWER_ACK);
    }

    public AnswerAckMessage(int seq, int questionId, boolean saved) {
        this();
        this.seq = seq;
        this.questionId = questionId;
        this.saved = saved;
    }
}
