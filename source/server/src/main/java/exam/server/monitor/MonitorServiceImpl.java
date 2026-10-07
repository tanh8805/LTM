// Owner: Nguoi3

package exam.server.monitor;

import exam.common.model.Metrics;
import exam.common.protocol.MetricsDetailMessage;
import exam.common.protocol.ViolationMessage;
import exam.server.api.MessageSender;
import exam.server.api.MlGateway;
import exam.server.api.MonitorService;
import exam.server.api.RateController;
import exam.server.api.SessionRegistry;

/** STUB của MonitorService: nhận dữ liệu nhưng chưa xử lý gì. */
public class MonitorServiceImpl implements MonitorService {

    // Các field này chưa dùng, sẽ dùng khi cài đặt các TODO bên dưới.
    private final SessionRegistry sessionRegistry;
    private final MessageSender messageSender;
    private final RateController rateController;
    private final MlGateway mlGateway;

    public MonitorServiceImpl(SessionRegistry sessionRegistry, MessageSender messageSender,
                              RateController rateController, MlGateway mlGateway) {
        this.sessionRegistry = sessionRegistry;
        this.messageSender = messageSender;
        this.rateController = rateController;
        this.mlGateway = mlGateway;
    }

    @Override
    public void onMetricsSummary(String machineId, Metrics summary) {
        // TODO(Nguoi3): Lưu summary mới nhất của máy, chuyển cho mlGateway.recordMetrics(machineId, summary).
    }

    @Override
    public void onMetricsDetail(String machineId, MetricsDetailMessage detail) {
        // TODO(Nguoi3): Lưu detail để giáo viên xem chi tiết một máy, chuyển detail.metrics cho mlGateway.
    }

    @Override
    public void onViolation(String machineId, ViolationMessage violation) {
        // TODO(Nguoi3): Ghi bảng violations, quyết định mức cảnh báo (AlertLevel), gửi ALERT cho giáo viên,
        //  gọi rateController.raiseToHigh(machineId).
    }

    @Override
    public void onMachineOffline(String machineId) {
        // TODO(Nguoi3): Đánh dấu máy offline trong danh sách máy của giáo viên.
    }

    @Override
    public void runPeriodicChecks() {
        // TODO(Nguoi3): Tính median và MAD từng metric của cả phòng -> gửi ROOM_STATS cho sinh viên,
        //  so sánh từng máy với phòng -> WARNING "gấp X lần trung vị phòng".
        // TODO(Nguoi3): Gọi mlGateway.scoreAllMachines() (bất đồng bộ), ghép kết quả theo ml.mode, gửi ALERT.
    }
}
