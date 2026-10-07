// Owner: Nguoi4

package exam.client.ml;

import exam.client.api.AnomalyScorer;
import java.util.ArrayList;
import java.util.List;

/**
 * Quy trình phát hiện bất thường trên máy sinh viên (đúng như docs/SPEC.md mục 8):
 *
 *   1. HỌC: thu `trainingSamples` vector đầu tiên (mặc định 5 phút) của chính máy này rồi fit mô hình.
 *   2. Threshold = max(điểm của các mẫu lúc học) + margin (mặc định 0.05).
 *   3. PHÁT HIỆN: mỗi vector mới được chấm điểm. Vượt threshold thì tăng bộ đếm liên tiếp,
 *      không vượt thì đặt lại về 0. Bộ đếm đạt `consecutiveRequired` (mặc định 3) thì cảnh báo.
 *
 * Lớp này đếm theo SỐ MẪU, không theo đồng hồ, nên test được mà không cần chờ.
 */
public class AnomalyDetector {

    public enum Phase {
        TRAINING,
        DETECTING
    }

    /** Kết quả của một lần observe(). */
    public static class Result {
        public final Phase phase;
        /** Điểm của mẫu vừa chấm (0 khi đang học). */
        public final double score;
        /** Threshold hiện tại (0 khi đang học). */
        public final double threshold;
        /** Số lần liên tiếp đã vượt threshold. */
        public final int consecutive;
        /** true khi đã vượt threshold đủ số lần liên tiếp. */
        public final boolean anomalous;

        Result(Phase phase, double score, double threshold, int consecutive, boolean anomalous) {
            this.phase = phase;
            this.score = score;
            this.threshold = threshold;
            this.consecutive = consecutive;
            this.anomalous = anomalous;
        }
    }

    private final AnomalyScorer scorer;
    private final int trainingSamples;
    private final double thresholdMargin;
    private final int consecutiveRequired;

    private final List<double[]> collectedSamples = new ArrayList<>();
    private Phase phase = Phase.TRAINING;
    private double threshold = 0;
    private int consecutiveCount = 0;

    public AnomalyDetector(AnomalyScorer scorer, int trainingSamples, double thresholdMargin, int consecutiveRequired) {
        this.scorer = scorer;
        this.trainingSamples = Math.max(1, trainingSamples);
        this.thresholdMargin = thresholdMargin;
        this.consecutiveRequired = Math.max(1, consecutiveRequired);
    }

    /** Đưa vào một vector số liệu mới (theo thứ tự thời gian). */
    public synchronized Result observe(double[] sample) {
        if (phase == Phase.TRAINING) {
            collectedSamples.add(sample);
            if (collectedSamples.size() >= trainingSamples) {
                finishTraining();
            }
            return new Result(Phase.TRAINING, 0, 0, 0, false);
        }

        double score = scorer.score(sample);
        if (score > threshold) {
            consecutiveCount++;
        } else {
            consecutiveCount = 0;
        }
        return new Result(Phase.DETECTING, score, threshold, consecutiveCount, consecutiveCount >= consecutiveRequired);
    }

    private void finishTraining() {
        scorer.fit(collectedSamples);

        double maxTrainingScore = 0;
        for (double[] sample : collectedSamples) {
            double score = scorer.score(sample);
            if (score > maxTrainingScore) {
                maxTrainingScore = score;
            }
        }
        threshold = maxTrainingScore + thresholdMargin;
        phase = Phase.DETECTING;
        System.out.println("[IF] Học xong " + collectedSamples.size() + " mẫu, threshold = " + String.format("%.3f", threshold));
    }

    public synchronized Phase getPhase() {
        return phase;
    }

    public synchronized double getThreshold() {
        return threshold;
    }
}
