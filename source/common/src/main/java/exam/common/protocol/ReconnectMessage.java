// Owner: Nguoi1

package exam.common.protocol;

/**
 * RECONNECT (Client → Server): Nối lại sau khi mất kết nối, bằng token đã nhận lúc LOGIN_OK.
 */
public class ReconnectMessage extends Message {
    public String token;
    /** seq của ANSWER cuối cùng đã được ANSWER_ACK */
    public int lastAnswerSeq;

    public ReconnectMessage() {
        super(MessageType.RECONNECT);
    }

    public ReconnectMessage(String token, int lastAnswerSeq) {
        this();
        this.token = token;
        this.lastAnswerSeq = lastAnswerSeq;
    }
}
