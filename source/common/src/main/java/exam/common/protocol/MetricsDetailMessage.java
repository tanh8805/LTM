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
}
