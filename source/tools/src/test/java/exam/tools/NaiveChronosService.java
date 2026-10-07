// Owner: Nguoi4

package exam.tools;

import exam.common.ml.Quantiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ChronosService giả dùng trong test: dự báo = trung vị ± 1.2816 x 1.4826 x MAD của ngữ cảnh (giống backend "naive"
 * của ml-service). Không phải Chronos thật, chỉ để kiểm tra đường đi dữ liệu và cách đánh giá.
 */
class NaiveChronosService implements ChronosService {

    int calls = 0;
    int lastBatchMachineCount = 0;

    @Override
    public Map<String, Map<String, Quantiles>> forecast(Map<String, Map<String, List<Double>>> seriesByMachine) {
        calls++;
        lastBatchMachineCount = seriesByMachine.size();
        Map<String, Map<String, Quantiles>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<Double>>> machine : seriesByMachine.entrySet()) {
            Map<String, Quantiles> forecast = new HashMap<>();
            for (Map.Entry<String, List<Double>> metric : machine.getValue().entrySet()) {
                double median = median(metric.getValue());
                List<Double> deviations = new ArrayList<>();
                for (double value : metric.getValue()) {
                    deviations.add(Math.abs(value - median));
                }
                double spread = 1.2816 * 1.4826 * median(deviations);
                forecast.put(metric.getKey(), new Quantiles(median - spread, median, median + spread));
            }
            result.put(machine.getKey(), forecast);
        }
        return result;
    }

    private double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
