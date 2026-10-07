// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.RateMode;

/**
 * SET_RATE (Server → Client): Server đổi tần suất gửi số liệu của client.
 */
public class SetRateMessage extends Message {
    /** NORMAL, HIGH hoặc BASELINE */
    public RateMode mode;
    /** Chu kỳ gửi, đơn vị milli giây */
    public int intervalMs;

    public SetRateMessage() {
        super(MessageType.SET_RATE);
    }

    public SetRateMessage(RateMode mode, int intervalMs) {
        this();
        this.mode = mode;
        this.intervalMs = intervalMs;
    }
}
