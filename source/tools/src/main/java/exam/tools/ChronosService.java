// Owner: Nguoi4

package exam.tools;

import exam.common.ml.Quantiles;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Nguồn dự báo Chronos cho thực nghiệm. Bản thật gọi ml-service; test dùng bản giả. */
public interface ChronosService {

    /**
     * Một batch cho mọi máy: machineId -> (tên metric -> chuỗi ngữ cảnh).
     * Trả về machineId -> (tên metric -> phân vị dự báo bước kế tiếp).
     * Ném IOException nếu ml-service không dùng được.
     */
    Map<String, Map<String, Quantiles>> forecast(Map<String, Map<String, List<Double>>> seriesByMachine) throws IOException;
}
