// Owner: Nguoi1

package exam.server.api;

import exam.common.protocol.HeartbeatMessage;
import exam.common.protocol.MetricsDetailMessage;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import exam.common.protocol.ViolationMessage;

/**
 * Giám sát phía Server: nhận số liệu và vi phạm, tính trung vị/MAD cả phòng,
 * ghép kết quả ML, quyết định mức cảnh báo (vàng/đỏ) và gửi ALERT.
 *
 * Ai cài đặt: Nguoi3 (exam.server.monitor.MonitorServiceImpl)
 * Ai gọi: Nguoi1 (ClientHandler khi nhận HEARTBEAT, METRICS_DETAIL, VIOLATION, REQUEST của giáo viên;
 *         PeriodicJobs mỗi chu kỳ giám sát)
 */
public interface MonitorService {

    /** Nhận HEARTBEAT (summary mỗi 10 giây, kèm kết quả Isolation Forest của client). */
    void onHeartbeat(String machineId, HeartbeatMessage heartbeat);

    /** Nhận METRICS_DETAIL (chế độ HIGH hoặc BASELINE). */
    void onMetricsDetail(String machineId, MetricsDetailMessage detail);

    /** Nhận VIOLATION từ client. */
    void onViolation(String machineId, ViolationMessage violation);

    /** Máy sinh viên đăng nhập hoặc nối lại. */
    void onMachineOnline(String machineId);

    /** Máy sinh viên mất kết nối. */
    void onMachineOffline(String machineId);

    /**
     * Việc định kỳ mỗi chu kỳ giám sát: tính trung vị + MAD cả phòng, gửi ROOM_STATS,
     * gọi MlGateway chấm điểm (bất đồng bộ), gửi ALERT, yêu cầu RateController tăng tần suất máy đáng ngờ.
     */
    void runPeriodicChecks();

    /** true nếu action thuộc về giám sát (LIST_MACHINES, MACHINE_DETAIL, SET_BASELINE, EXPORT_METRICS_CSV). */
    boolean handlesAction(String action);

    /** Xử lý REQUEST giám sát của giáo viên. Chỉ gọi khi handlesAction(...) là true. */
    ResponseMessage handleTeacherRequest(RequestMessage request);
}
