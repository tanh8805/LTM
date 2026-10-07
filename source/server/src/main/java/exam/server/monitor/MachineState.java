// Owner: Nguoi3

package exam.server.monitor;

import exam.common.model.AlertLevel;
import exam.common.model.Metrics;
import exam.common.protocol.MetricsDetailMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trạng thái giám sát của MỘT máy sinh viên, nằm trong bộ nhớ Server.
 *
 * Nhiều thread cùng truy cập (thread của client, PeriodicJobs, thread nhận kết quả ML),
 * nên mọi method đều synchronized.
 *
 * Cảnh báo được lưu theo "khóa" để cập nhật hoặc xóa đúng loại:
 *   "V:<loại>:<bằng chứng>" : vi phạm luật (giữ lại, không tự xóa)
 *   "ROOM"                  : lệch trung vị phòng (xóa khi hết lệch)
 *   "ML"                    : ML đáng ngờ theo ml.mode (xóa khi hết đáng ngờ)
 *   "OFFLINE"               : mất kết nối (xóa khi nối lại)
 */
public class MachineState {

    /** Số cảnh báo tối đa giữ cho mỗi máy (tránh tràn bộ nhớ khi bị spam vi phạm). */
    private static final int MAX_ALERTS = 30;

    /** Một cảnh báo đang hiển thị. */
    public static class Alert {
        public final AlertLevel level;
        public final String reason;

        Alert(AlertLevel level, String reason) {
            this.level = level;
            this.reason = reason;
        }
    }

    public final String machineId;
    public final int studentId;
    public final String fullName;
    public final String examCode;

    private boolean online = true;
    private long lastSeenTime = 0;
    private Metrics lastMetrics;
    private MetricsDetailMessage lastDetail;

    private double ifScore = 0;
    private boolean ifAnomalous = false;
    private long ifUpdatedTime = 0;

    private boolean chronosSuspicious = false;
    private String chronosReason;
    private long chronosUpdatedTime = 0;

    private final Map<String, Alert> alerts = new LinkedHashMap<>();

    public MachineState(String machineId, int studentId, String fullName, String examCode) {
        this.machineId = machineId;
        this.studentId = studentId;
        this.fullName = fullName;
        this.examCode = examCode;
    }

    // ----- số liệu -----

    public synchronized void updateFromHeartbeat(Metrics summary, double anomalyScore, boolean anomalous, long now) {
        this.lastMetrics = summary;
        this.lastSeenTime = now;
        this.ifScore = anomalyScore;
        this.ifAnomalous = anomalous;
        this.ifUpdatedTime = now;
    }

    public synchronized void updateFromDetail(MetricsDetailMessage detail, long now) {
        this.lastDetail = detail;
        this.lastMetrics = detail.metrics;
        this.lastSeenTime = now;
        this.ifScore = detail.anomalyScore;
        this.ifAnomalous = detail.anomalous;
        this.ifUpdatedTime = now;
    }

    public synchronized void updateChronos(boolean suspicious, String reason, long now) {
        this.chronosSuspicious = suspicious;
        this.chronosReason = reason;
        this.chronosUpdatedTime = now;
    }

    public synchronized Metrics getLastMetrics() {
        return lastMetrics;
    }

    public synchronized MetricsDetailMessage getLastDetail() {
        return lastDetail;
    }

    public synchronized long getLastSeenTime() {
        return lastSeenTime;
    }

    public synchronized double getIfScore() {
        return ifScore;
    }

    public synchronized String getChronosReason() {
        return chronosReason;
    }

    /** Isolation Forest đang báo bất thường (và thông tin còn mới, không quá maxAgeMillis). */
    public synchronized boolean isIfActive(long now, long maxAgeMillis) {
        return ifAnomalous && now - ifUpdatedTime <= maxAgeMillis;
    }

    /** Chronos đang báo đáng ngờ (và kết quả còn hiệu lực). */
    public synchronized boolean isChronosActive(long now, long maxAgeMillis) {
        return chronosSuspicious && now - chronosUpdatedTime <= maxAgeMillis;
    }

    public synchronized boolean isOnline() {
        return online;
    }

    public synchronized void setOnline(boolean online) {
        this.online = online;
    }

    // ----- cảnh báo -----

    /** Thêm hoặc cập nhật một cảnh báo. Trả về true nếu là cảnh báo MỚI hoặc nội dung đã đổi (nên gửi ALERT). */
    public synchronized boolean setAlert(String key, AlertLevel level, String reason) {
        Alert old = alerts.get(key);
        if (old != null && old.level == level && old.reason.equals(reason)) {
            return false;
        }
        if (old == null && alerts.size() >= MAX_ALERTS) {
            String oldestKey = alerts.keySet().iterator().next();
            alerts.remove(oldestKey);
        }
        alerts.put(key, new Alert(level, reason));
        return true;
    }

    public synchronized void clearAlert(String key) {
        alerts.remove(key);
    }

    public synchronized boolean hasAlert(String key) {
        return alerts.containsKey(key);
    }

    /** Mức cao nhất trong các cảnh báo đang có: RED > YELLOW. Trả về null nếu không có cảnh báo. */
    public synchronized AlertLevel getCurrentLevel() {
        AlertLevel highest = null;
        for (Alert alert : alerts.values()) {
            if (alert.level == AlertLevel.RED) {
                return AlertLevel.RED;
            }
            highest = AlertLevel.YELLOW;
        }
        return highest;
    }

    /** Lý do của mọi cảnh báo đang có, nối bằng "; " (cảnh báo ĐỎ trước). */
    public synchronized String getCurrentReason() {
        List<String> reasons = getReasonList();
        return String.join("; ", reasons);
    }

    public synchronized List<String> getReasonList() {
        List<String> reasons = new ArrayList<>();
        for (Alert alert : alerts.values()) {
            if (alert.level == AlertLevel.RED) {
                reasons.add(alert.reason);
            }
        }
        for (Alert alert : alerts.values()) {
            if (alert.level == AlertLevel.YELLOW) {
                reasons.add(alert.reason);
            }
        }
        return reasons;
    }
}
