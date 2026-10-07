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
    /** Điểm Isolation Forest của client (0..1). 0 nếu client không chạy Isolation Forest. */
    public double anomalyScore;
    /** true khi Isolation Forest đã vượt threshold đủ số lần liên tiếp. */
    public boolean anomalous;

    public HeartbeatMessage() {
        super(MessageType.HEARTBEAT);
    }

    public HeartbeatMessage(int seq, Metrics summary) {
        this();
        this.seq = seq;
        this.summary = summary;
    }

    public HeartbeatMessage(int seq, Metrics summary, double anomalyScore, boolean anomalous) {
        this(seq, summary);
        this.anomalyScore = anomalyScore;
        this.anomalous = anomalous;
    }
}
