// Owner: Nguoi1

package exam.client.api;

import java.util.List;

/**
 * Chấm điểm bất thường của một vector số liệu. Cài đặt thật là Isolation Forest tự viết.
 *
 * Ai cài đặt: Nguoi4 (exam.client.api.IsolationForestScorer, dùng exam.common.ml.IsolationForest)
 * Ai gọi: Nguoi3 (MonitoringLoop)
 */
public interface AnomalyScorer {

    /** Học "bình thường" từ các vector thu trong những phút đầu (mỗi vector 8 chiều). */
    void fit(List<double[]> samples);

    /** Điểm bất thường 0..1, càng cao càng bất thường. */
    double score(double[] sample);
}
