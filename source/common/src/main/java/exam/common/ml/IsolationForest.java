// Owner: Nguoi4

package exam.common.ml;

import java.util.List;

/**
 * Isolation Forest TỰ CÀI bằng Java (không dùng thư viện ML).
 *
 * Cách dùng ở client (xem docs/SPEC.md):
 *   1. fit(...) bằng các vector 8 chiều thu trong 5 phút đầu (cấu hình được) của chính máy đó.
 *   2. threshold = max(score của các mẫu huấn luyện) + 0.05.
 *   3. score(...) cho từng vector mới. Vượt threshold 3 lần liên tiếp thì cảnh báo.
 *
 * HIỆN LÀ STUB: chưa có thuật toán thật.
 */
public class IsolationForest {

    private boolean fitted = false;

    /** Huấn luyện từ các mẫu. Mỗi mẫu là vector 8 chiều (xem Metrics.toVector()). */
    public void fit(List<double[]> samples) {
        // TODO(Nguoi4): Build isolation trees (random feature + random split) từ samples.
        fitted = true;
    }

    /**
     * Anomaly score trong khoảng 0..1, càng gần 1 càng bất thường.
     * Stub trả về 0.0 (luôn "bình thường").
     */
    public double score(double[] sample) {
        // TODO(Nguoi4): Tính độ dài đường đi trung bình qua các cây rồi đổi thành anomaly score.
        return 0.0;
    }

    public boolean isFitted() {
        return fitted;
    }
}
