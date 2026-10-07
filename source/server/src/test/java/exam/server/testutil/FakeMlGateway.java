// Owner: Nguoi4

package exam.server.testutil;

import exam.common.ml.MlResult;
import exam.common.model.Metrics;
import exam.server.api.MlGateway;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** MlGateway giả: test đặt trước kết quả muốn trả về. */
public class FakeMlGateway implements MlGateway {

    public final List<String> recordedMachines = Collections.synchronizedList(new ArrayList<>());
    public Map<String, MlResult> nextResults = new HashMap<>();
    public int scoreCalls = 0;

    @Override
    public void recordMetrics(String machineId, Metrics metrics) {
        recordedMachines.add(machineId);
    }

    @Override
    public CompletableFuture<Map<String, MlResult>> scoreAllMachines() {
        scoreCalls++;
        return CompletableFuture.completedFuture(nextResults);
    }
}
