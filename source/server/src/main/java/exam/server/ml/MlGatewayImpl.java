// Owner: Nguoi4

package exam.server.ml;

import exam.common.ml.ChronosCodec;
import exam.common.ml.ChronosJudge;
import exam.common.ml.MlResult;
import exam.common.ml.Quantiles;
import exam.common.model.Metrics;
import exam.server.api.MlGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cổng nói chuyện với ml-service (Chronos-Bolt tiny).
 *
 * Mỗi chu kỳ MonitorService gọi scoreAllMachines():
 *   1. Với MỌI máy đủ dữ liệu, lấy tối đa 60 điểm gần nhất (6 metric của Chronos), bỏ điểm mới nhất ra làm "giá trị thật".
 *   2. Gom tất cả thành MỘT request POST /score (batch). Không gọi từng máy, từng metric.
 *   3. Gọi bất đồng bộ, timeout 3 giây. Lỗi, timeout, ml-service tắt, model chưa tải được -> trả về map rỗng,
 *      Server vẫn chạy bình thường (chỉ không có kết quả ML ở chu kỳ đó).
 *   4. So giá trị thật với khoảng q0.1 - q0.9 mà Chronos dự báo (ChronosJudge): ngoài khoảng 3 lần liên tiếp là đáng ngờ.
 */
public class MlGatewayImpl implements MlGateway {

    /** 60 điểm gần nhất của một máy (mỗi điểm là 6 số: các metric Chronos chấm). */
    private static class MachineHistory {
        final ArrayDeque<double[]> points = new ArrayDeque<>();
        /** Tổng số điểm đã nhận từ trước tới nay. */
        long totalAdded = 0;
        /** Giá trị totalAdded tại lần chấm gần nhất, để không chấm lại một điểm hai lần. */
        long scoredAtTotal = 0;
    }

    private final MlConfig config;
    private final HttpClient httpClient;
    private final ChronosJudge judge;
    private final Map<String, MachineHistory> histories = new ConcurrentHashMap<>();
    /** Chỉ cho phép một lần gọi ml-service tại một thời điểm. Lần trước chưa xong thì bỏ qua chu kỳ này. */
    private final AtomicBoolean callInFlight = new AtomicBoolean(false);
    private int consecutiveFailures = 0;

    public MlGatewayImpl(MlConfig config) {
        this.config = config;
        this.judge = new ChronosJudge(config.chronosConsecutive);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(config.serviceTimeoutMs))
                .build();
    }

    @Override
    public void recordMetrics(String machineId, Metrics metrics) {
        double[] chronosValues = new double[Metrics.CHRONOS_DIMENSIONS];
        System.arraycopy(metrics.toVector(), 0, chronosValues, 0, Metrics.CHRONOS_DIMENSIONS);
        for (int i = 0; i < chronosValues.length; i++) {
            // JSON không biểu diễn được NaN/Infinity: số liệu hỏng được thay bằng 0 thay vì làm hỏng cả batch.
            if (!Double.isFinite(chronosValues[i])) {
                chronosValues[i] = 0;
            }
        }

        MachineHistory history = histories.computeIfAbsent(machineId, key -> new MachineHistory());
        synchronized (history) {
            history.points.addLast(chronosValues);
            history.totalAdded++;
            // Giữ cửa sổ + 1 điểm: điểm mới nhất là "giá trị thật", chỉ các điểm trước đó mới gửi làm ngữ cảnh.
            while (history.points.size() > config.chronosWindow + 1) {
                history.points.removeFirst();
            }
        }
    }

    @Override
    public CompletableFuture<Map<String, MlResult>> scoreAllMachines() {
        if (!callInFlight.compareAndSet(false, true)) {
            System.out.println("[ML] Lần gọi ml-service trước chưa xong, bỏ qua chu kỳ này");
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        // machineId -> (metric -> ngữ cảnh) và machineId -> (metric -> giá trị thật)
        Map<String, Map<String, List<Double>>> contextByMachine = new LinkedHashMap<>();
        Map<String, Map<String, Double>> actualByMachine = new HashMap<>();
        Map<String, Long> totalsByMachine = new HashMap<>();
        collectBatch(contextByMachine, actualByMachine, totalsByMachine);

        if (contextByMachine.isEmpty()) {
            callInFlight.set(false);
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.serviceUrl + "/score"))
                    .timeout(Duration.ofMillis(config.serviceTimeoutMs))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(ChronosCodec.buildRequest(contextByMachine)))
                    .build();
        } catch (RuntimeException e) {
            // Địa chỉ ml.service.url sai hoặc không dựng được JSON: bỏ qua ML nhưng KHÔNG để cờ callInFlight bị kẹt.
            callInFlight.set(false);
            logFailure(e);
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        // sendAsync không chặn thread gọi. orTimeout bảo đảm tổng thời gian không vượt quá timeout (kể cả lúc đọc body).
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(config.serviceTimeoutMs, TimeUnit.MILLISECONDS)
                .thenApply(response -> judgeResponse(response, actualByMachine, totalsByMachine))
                .exceptionally(error -> {
                    logFailure(error);
                    return new HashMap<String, MlResult>();
                })
                .whenComplete((results, error) -> callInFlight.set(false));
    }

    /** Lấy dữ liệu của các máy có điểm mới và đủ số điểm tối thiểu. */
    private void collectBatch(Map<String, Map<String, List<Double>>> contextByMachine,
                              Map<String, Map<String, Double>> actualByMachine,
                              Map<String, Long> totalsByMachine) {
        for (Map.Entry<String, MachineHistory> entry : histories.entrySet()) {
            MachineHistory history = entry.getValue();
            synchronized (history) {
                int size = history.points.size();
                boolean hasNewPoint = history.totalAdded > history.scoredAtTotal;
                if (!hasNewPoint || size - 1 < config.chronosMinPoints) {
                    continue;
                }

                List<double[]> points = new ArrayList<>(history.points);
                double[] latest = points.get(size - 1);

                Map<String, List<Double>> context = new LinkedHashMap<>();
                Map<String, Double> actual = new HashMap<>();
                for (int metric = 0; metric < Metrics.CHRONOS_DIMENSIONS; metric++) {
                    List<Double> values = new ArrayList<>();
                    for (int i = 0; i < size - 1; i++) {
                        values.add(points.get(i)[metric]);
                    }
                    context.put(Metrics.VECTOR_NAMES[metric], values);
                    actual.put(Metrics.VECTOR_NAMES[metric], latest[metric]);
                }
                contextByMachine.put(entry.getKey(), context);
                actualByMachine.put(entry.getKey(), actual);
                totalsByMachine.put(entry.getKey(), history.totalAdded);
            }
        }
    }

    private Map<String, MlResult> judgeResponse(HttpResponse<String> response,
                                                Map<String, Map<String, Double>> actualByMachine,
                                                Map<String, Long> totalsByMachine) {
        if (response.statusCode() != 200) {
            String shortBody = response.body().length() > 200 ? response.body().substring(0, 200) : response.body();
            throw new IllegalStateException("ml-service trả HTTP " + response.statusCode() + ": " + shortBody);
        }

        Map<String, Map<String, Quantiles>> forecastByMachine = ChronosCodec.parseResponse(response.body());
        Map<String, MlResult> results = new HashMap<>();
        for (Map.Entry<String, Map<String, Quantiles>> entry : forecastByMachine.entrySet()) {
            String machineId = entry.getKey();
            Map<String, Double> actual = actualByMachine.get(machineId);
            if (actual == null) {
                continue; // ml-service trả về máy không có trong request: bỏ qua
            }
            results.put(machineId, judge.judge(machineId, actual, entry.getValue()));

            MachineHistory history = histories.get(machineId);
            synchronized (history) {
                history.scoredAtTotal = totalsByMachine.get(machineId);
            }
        }

        if (consecutiveFailures > 0) {
            System.out.println("[ML] ml-service hoạt động trở lại");
        }
        consecutiveFailures = 0;
        return results;
    }

    /** Ghi log lần lỗi đầu tiên và mỗi 30 lần sau đó (tránh tràn log khi ml-service tắt lâu). */
    private void logFailure(Throwable error) {
        consecutiveFailures++;
        if (consecutiveFailures == 1 || consecutiveFailures % 30 == 0) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            System.out.println("[ML] Bỏ qua ML (lần lỗi liên tiếp thứ " + consecutiveFailures + "): "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }
}
