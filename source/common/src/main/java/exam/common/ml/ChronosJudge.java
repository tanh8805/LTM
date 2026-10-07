// Owner: Nguoi4

package exam.common.ml;

import exam.common.model.Metrics;
import java.util.HashMap;
import java.util.Map;

/**
 * Quy tắc Chronos: một máy đáng ngờ khi giá trị THẬT của một metric nằm ngoài khoảng [q0.1, q0.9]
 * (nhỏ hơn q0.1 hoặc lớn hơn q0.9) đủ N lần liên tiếp (mặc định 3).
 *
 * Chỉ xét 6 metric đầu của Metrics (KB gửi, KB nhận, số kết nối, số địa chỉ đích, CPU, RAM).
 * KHÔNG xét số tiến trình và số lần mất focus.
 *
 * Lớp này nhớ số lần liên tiếp của từng (máy, metric) nên mỗi máy chỉ được judge() một lần cho mỗi điểm dữ liệu mới.
 */
public class ChronosJudge {

    private final int consecutiveRequired;
    // khóa: machineId + "|" + tên metric
    private final Map<String, Integer> consecutiveCounts = new HashMap<>();

    public ChronosJudge(int consecutiveRequired) {
        this.consecutiveRequired = consecutiveRequired;
    }

    /**
     * @param actual   giá trị thật của metric ở bước vừa rồi: tên metric -> giá trị
     * @param forecast dự báo của Chronos cho đúng bước đó: tên metric -> phân vị
     */
    public synchronized MlResult judge(String machineId, Map<String, Double> actual, Map<String, Quantiles> forecast) {
        int worstCount = 0;
        String reason = null;

        for (int i = 0; i < Metrics.CHRONOS_DIMENSIONS; i++) {
            String metricName = Metrics.VECTOR_NAMES[i];
            Double actualValue = actual.get(metricName);
            Quantiles quantiles = forecast.get(metricName);
            if (actualValue == null || quantiles == null) {
                continue; // thiếu dữ liệu cho metric này: bỏ qua, không đổi bộ đếm
            }

            String key = machineId + "|" + metricName;
            boolean outOfRange = actualValue < quantiles.q10 || actualValue > quantiles.q90;
            int count = outOfRange ? consecutiveCounts.getOrDefault(key, 0) + 1 : 0;
            consecutiveCounts.put(key, count);

            if (count > worstCount) {
                worstCount = count;
                reason = metricName + "=" + format(actualValue) + " ngoài khoảng q0.1-q0.9 ["
                        + format(quantiles.q10) + ", " + format(quantiles.q90) + "] " + count + " lần liên tiếp";
            }
        }

        boolean suspicious = worstCount >= consecutiveRequired;
        return new MlResult(suspicious, worstCount, suspicious ? reason : null);
    }

    private String format(double value) {
        return String.format("%.2f", value);
    }
}
