// Owner: Nguoi4

package exam.common.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Isolation Forest TỰ CÀI bằng Java (không dùng thư viện ML).
 *
 * Cách dùng ở client (xem docs/SPEC.md):
 *   1. fit(...) bằng các vector 8 chiều thu trong 5 phút đầu (cấu hình được) của chính máy đó.
 *   2. threshold = max(score của các mẫu huấn luyện) + 0.05.
 *   3. score(...) cho từng vector mới. Vượt threshold 3 lần liên tiếp thì cảnh báo.
 *
 * Điểm bất thường (theo bài báo Liu et al., 2008):
 *   score(x) = 2 ^ ( - E[h(x)] / c(n) )
 *   - E[h(x)] : độ dài đường đi trung bình của x qua mọi cây
 *   - c(n)    : độ dài đường đi trung bình của một cây nhị phân tìm kiếm không thành công trên n mẫu
 * score gần 1 là bất thường, quanh 0.5 là bình thường, gần 0 là rất "đông đúc".
 */
public class IsolationForest {

    private static final double EULER_MASCHERONI = 0.5772156649;

    private final int treeCount;
    private final int subsampleSize;
    private final long seed;

    private final List<IsolationTree> trees = new ArrayList<>();
    private int featureCount = -1;
    private int usedSubsampleSize = 0;

    /** 100 cây, mỗi cây học trên tối đa 256 mẫu, seed cố định để kết quả tái lập được. */
    public IsolationForest() {
        this(100, 256, 42L);
    }

    public IsolationForest(int treeCount, int subsampleSize, long seed) {
        if (treeCount < 1 || subsampleSize < 2) {
            throw new IllegalArgumentException("treeCount phải >= 1 và subsampleSize phải >= 2");
        }
        this.treeCount = treeCount;
        this.subsampleSize = subsampleSize;
        this.seed = seed;
    }

    /** Huấn luyện từ các mẫu. Mỗi mẫu là vector có cùng số chiều (ví dụ 8, xem Metrics.toVector()). */
    public void fit(List<double[]> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất 1 mẫu để huấn luyện");
        }
        int dimensions = samples.get(0).length;
        for (double[] sample : samples) {
            if (sample.length != dimensions) {
                throw new IllegalArgumentException("Các mẫu có số chiều khác nhau");
            }
        }

        Random random = new Random(seed);
        int sizePerTree = Math.min(subsampleSize, samples.size());
        // Độ cao tối đa của cây: ceil(log2(số mẫu mỗi cây)), vì cây sâu hơn mức đó không cần thiết.
        int heightLimit = (int) Math.ceil(Math.log(Math.max(sizePerTree, 2)) / Math.log(2));

        trees.clear();
        for (int i = 0; i < treeCount; i++) {
            List<double[]> subsample = pickRandomSubsample(samples, sizePerTree, random);
            trees.add(new IsolationTree(subsample, heightLimit, random));
        }
        featureCount = dimensions;
        usedSubsampleSize = sizePerTree;
    }

    /** Lấy ngẫu nhiên (không lặp) sizePerTree mẫu. */
    private List<double[]> pickRandomSubsample(List<double[]> samples, int sizePerTree, Random random) {
        List<double[]> shuffled = new ArrayList<>(samples);
        Collections.shuffle(shuffled, random);
        return new ArrayList<>(shuffled.subList(0, sizePerTree));
    }

    /** Anomaly score trong khoảng 0..1, càng gần 1 càng bất thường. */
    public double score(double[] sample) {
        if (!isFitted()) {
            throw new IllegalStateException("Phải gọi fit() trước khi score()");
        }
        if (sample.length != featureCount) {
            throw new IllegalArgumentException(
                    "Mẫu có " + sample.length + " chiều, mô hình học với " + featureCount + " chiều");
        }

        double totalPathLength = 0;
        for (IsolationTree tree : trees) {
            totalPathLength += tree.pathLength(sample);
        }
        double averagePathLength = totalPathLength / trees.size();

        double normalizer = averagePathLength(usedSubsampleSize);
        if (normalizer == 0) {
            return 0.5; // chỉ học trên 1 mẫu nên không có cơ sở so sánh
        }
        return Math.pow(2.0, -averagePathLength / normalizer);
    }

    public boolean isFitted() {
        return !trees.isEmpty();
    }

    /** c(n): độ dài đường đi trung bình của tìm kiếm không thành công trong cây nhị phân tìm kiếm n phần tử. */
    static double averagePathLength(int n) {
        if (n <= 1) {
            return 0.0;
        }
        if (n == 2) {
            return 1.0;
        }
        double harmonicNumber = Math.log(n - 1) + EULER_MASCHERONI;
        return 2.0 * harmonicNumber - 2.0 * (n - 1) / n;
    }
}
