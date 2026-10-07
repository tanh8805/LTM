// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.Metrics;

/**
 * HEARTBEAT (Client → Server): Tín hiệu "tôi còn sống" mỗi 10 giây, kèm summary số liệu giám sát.
 */
public class HeartbeatMessage extends Message {
    /** Số thứ tự tăng dần 1, 2, 3, ... */
    public int seq;
    /** Vector 8 chiều (chế độ NORMAL) */
    public Metrics summary;

    public HeartbeatMessage() {
        super(MessageType.HEARTBEAT);
    }

    public HeartbeatMessage(int seq, Metrics summary) {
        this();
        this.seq = seq;
        this.summary = summary;
    }
}
