// Owner: Nguoi4

package exam.server.ml;

import exam.common.ml.MlResult;
import exam.common.model.Metrics;
import exam.server.api.MlGateway;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** STUB của MlGateway: chưa gọi ml-service, luôn trả về "không có kết quả ML". */
public class MlGatewayImpl implements MlGateway {

    // Chưa dùng, sẽ dùng khi cài đặt (URL ml-service và timeout 3 giây).
    private final MlConfig config;

    public MlGatewayImpl(MlConfig config) {
        this.config = config;
    }

    @Override
    public void recordMetrics(String machineId, Metrics metrics) {
        // TODO(Nguoi4): Giữ 60 điểm gần nhất của mỗi máy (chỉ 6 chiều Chronos: xem Metrics.CHRONOS_DIMENSIONS).
    }

    @Override
    public CompletableFuture<Map<String, MlResult>> scoreAllMachines() {
        // TODO(Nguoi4): Gom 60 điểm của mọi máy thành MỘT request POST /score tới ml-service (java.net.http.HttpClient),
        //  gọi bất đồng bộ, timeout config.serviceTimeoutMs. Lỗi/timeout -> trả map rỗng, KHÔNG ném exception.
        //  Đáng ngờ khi giá trị thật nằm ngoài q0.1-q0.9 đủ 3 lần liên tiếp.
        return CompletableFuture.completedFuture(new HashMap<>());
    }
}
