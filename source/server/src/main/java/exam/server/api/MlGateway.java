// Owner: Nguoi1

package exam.server.api;

import exam.common.ml.MlResult;
import exam.common.model.Metrics;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cổng nói chuyện với ml-service (Chronos-Bolt tiny).
 *
 * Quy tắc (xem docs/SPEC.md):
 *   - Mỗi 10 giây lấy 60 điểm gần nhất của MỌI máy, gom thành MỘT batch, gọi ml-service một lần.
 *   - Gọi bất đồng bộ, timeout 3 giây.
 *   - Lỗi hoặc timeout -> bỏ qua ML (trả về map rỗng), hệ thống vẫn chạy bình thường.
 *
 * Ai cài đặt: Nguoi4 (exam.server.ml.MlGatewayImpl)
 * Ai gọi: Nguoi3 (MonitorService.runPeriodicChecks)
 */
public interface MlGateway {

    /** Ghi nhận một điểm số liệu mới của máy (giữ 60 điểm gần nhất cho mỗi máy). */
    void recordMetrics(String machineId, Metrics metrics);

    /**
     * Chấm điểm mọi máy bằng một lần gọi ml-service.
     * Kết quả: machineId -> MlResult. Khi lỗi thì future vẫn hoàn thành, với map rỗng.
     */
    CompletableFuture<Map<String, MlResult>> scoreAllMachines();
}
