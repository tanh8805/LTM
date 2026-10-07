// Owner: Nguoi1

package exam.server.api;

import exam.common.model.Metrics;
import exam.common.protocol.MetricsDetailMessage;
import exam.common.protocol.ViolationMessage;

/**
 * Giám sát phía Server: nhận số liệu và vi phạm, tính trung vị/MAD cả phòng,
 * so sánh với điểm của client, quyết định mức cảnh báo (vàng/đỏ).
 *
 * Ai cài đặt: Nguoi3 (exam.server.monitor.MonitorServiceImpl)
 * Ai gọi: Nguoi1 (ClientHandler khi nhận HEARTBEAT, METRICS_DETAIL, VIOLATION; PeriodicJobs mỗi 10 giây)
 */
public interface MonitorService {

    /** Nhận summary trong HEARTBEAT (chế độ NORMAL, mỗi 10 giây). */
    void onMetricsSummary(String machineId, Metrics summary);

    /** Nhận METRICS_DETAIL (chế độ HIGH hoặc BASELINE). */
    void onMetricsDetail(String machineId, MetricsDetailMessage detail);

    /** Nhận VIOLATION từ client. */
    void onViolation(String machineId, ViolationMessage violation);

    /** Máy mất kết nối. */
    void onMachineOffline(String machineId);

    /**
     * Việc định kỳ mỗi 10 giây: tính trung vị + MAD cả phòng, gửi ROOM_STATS,
     * gọi MlGateway chấm điểm, gửi ALERT, yêu cầu RateController tăng tần suất máy đáng ngờ.
     */
    void runPeriodicChecks();
}
