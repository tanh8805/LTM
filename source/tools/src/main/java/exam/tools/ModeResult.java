// Owner: Nguoi4

package exam.tools;

import exam.common.model.MlMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Kết quả thực nghiệm của một chế độ ml.mode trên một bộ trace. */
public class ModeResult {
    public final MlMode mode;
    public int truePositives;
    public int falsePositives;
    public int falseNegatives;
    public int trueNegatives;
    /** Số đoạn gian lận (cửa sổ liên tiếp có nhãn 1) và số đoạn được phát hiện ít nhất một lần trong đoạn đó. */
    public int totalWindows;
    public int detectedWindows;
    /** Tổng số mẫu trễ (tính từ đầu đoạn gian lận tới lần báo đầu tiên) của các đoạn đã phát hiện. */
    public int totalDelaySamples;
    /** Số mẫu label=0 thuộc đoạn bình thường / đoạn hoạt động ứng dụng bình thường (benign), và số báo nhầm trong từng loại. */
    public int normalSamples;
    public int benignSamples;
    public int falsePositivesNormal;
    public int falsePositivesBenign;
    /** Số "đợt báo nhầm": chuỗi liên tiếp các mẫu label=0 bị báo (một đợt dài chỉ tính một lần). */
    public int falseAlarmEpisodes;
    /** Kết quả theo từng loại kịch bản (CPU_LOAD, NETWORK_LOAD, ...). */
    public final Map<String, ScenarioStats> byScenario = new LinkedHashMap<>();
    public long elapsedMillis;
    /** Khác null nếu chế độ này không chạy được (ví dụ ml-service không dùng được): kết quả khác bị bỏ trống. */
    public String skippedReason;

    /** Thống kê của một loại kịch bản. */
    public static class ScenarioStats {
        public int samples;
        public int detectedSamples;
        public int windows;
        public int detectedWindows;
        public int totalDelaySamples;
    }

    public ModeResult(MlMode mode) {
        this.mode = mode;
    }

    public ScenarioStats scenario(String name) {
        return byScenario.computeIfAbsent(name, key -> new ScenarioStats());
    }

    public double precision() {
        int predicted = truePositives + falsePositives;
        return predicted == 0 ? 0 : (double) truePositives / predicted;
    }

    public double recall() {
        int actual = truePositives + falseNegatives;
        return actual == 0 ? 0 : (double) truePositives / actual;
    }

    public double f1() {
        double p = precision();
        double r = recall();
        return p + r == 0 ? 0 : 2 * p * r / (p + r);
    }

    /** Tỷ lệ báo nhầm trên các mẫu bình thường. */
    public double falsePositiveRate() {
        int negatives = falsePositives + trueNegatives;
        return negatives == 0 ? 0 : (double) falsePositives / negatives;
    }

    /** Số mẫu trễ trung bình để phát hiện một đoạn gian lận. NaN nếu chưa phát hiện đoạn nào. */
    public double averageDelaySamples() {
        return detectedWindows == 0 ? Double.NaN : (double) totalDelaySamples / detectedWindows;
    }
}
