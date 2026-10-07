// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.Metrics;
import java.util.List;

/**
 * METRICS_DETAIL (Client → Server): Số liệu chi tiết (chế độ HIGH mỗi 2 giây, BASELINE mỗi 1 giây).
 */
public class MetricsDetailMessage extends Message {
    public int seq;
    /** Giờ client lúc đo (epoch milli giây) */
    public long time;
    public Metrics metrics;
    /** Tên các process đang chạy */
    public List<String> processNames;
    /** Các địa chỉ đích đang kết nối */
    public List<String> remoteAddresses;
    /** Điểm Isolation Forest của client (0..1). 0 nếu client không chạy Isolation Forest. */
    public double anomalyScore;
    /** true khi Isolation Forest đã vượt threshold đủ số lần liên tiếp. */
    public boolean anomalous;

    public MetricsDetailMessage() {
        super(MessageType.METRICS_DETAIL);
    }

    public MetricsDetailMessage(int seq, long time, Metrics metrics, List<String> processNames, List<String> remoteAddresses) {
        this();
        this.seq = seq;
        this.time = time;
        this.metrics = metrics;
        this.processNames = processNames;
        this.remoteAddresses = remoteAddresses;
    }

    public MetricsDetailMessage(int seq, long time, Metrics metrics, List<String> processNames,
                                List<String> remoteAddresses, double anomalyScore, boolean anomalous) {
        this(seq, time, metrics, processNames, remoteAddresses);
        this.anomalyScore = anomalyScore;
        this.anomalous = anomalous;
    }
}
