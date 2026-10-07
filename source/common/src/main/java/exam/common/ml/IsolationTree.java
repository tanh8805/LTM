// Owner: Nguoi4

package exam.common.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Một cây trong Isolation Forest.
 *
 * Ý tưởng: chọn ngẫu nhiên một chiều và một giá trị cắt, chia mẫu làm hai nhánh, lặp lại.
 * Điểm bất thường nằm xa số đông nên bị tách riêng sau rất ít lần cắt (đường đi ngắn).
 */
class IsolationTree {

    private static class Node {
        // Lá: splitFeature = -1, size = số mẫu còn lại trong lá.
        int splitFeature = -1;
        double splitValue;
        Node left;
        Node right;
        int size;
    }

    private final Node root;

    IsolationTree(List<double[]> samples, int heightLimit, Random random) {
        this.root = buildNode(samples, 0, heightLimit, random);
    }

    private Node buildNode(List<double[]> samples, int depth, int heightLimit, Random random) {
        Node node = new Node();
        node.size = samples.size();

        // Dừng khi chỉ còn 1 mẫu hoặc cây đã đủ sâu.
        if (samples.size() <= 1 || depth >= heightLimit) {
            return node;
        }

        int feature = pickRandomSplittableFeature(samples, random);
        if (feature == -1) {
            return node; // mọi chiều đều không đổi, không cắt được nữa
        }

        double min = minOfFeature(samples, feature);
        double max = maxOfFeature(samples, feature);
        double splitValue = min + random.nextDouble() * (max - min);

        List<double[]> leftSamples = new ArrayList<>();
        List<double[]> rightSamples = new ArrayList<>();
        for (double[] sample : samples) {
            if (sample[feature] < splitValue) {
                leftSamples.add(sample);
            } else {
                rightSamples.add(sample);
            }
        }
        if (leftSamples.isEmpty() || rightSamples.isEmpty()) {
            return node; // hiếm gặp do làm tròn số thực: coi như lá
        }

        node.splitFeature = feature;
        node.splitValue = splitValue;
        node.left = buildNode(leftSamples, depth + 1, heightLimit, random);
        node.right = buildNode(rightSamples, depth + 1, heightLimit, random);
        return node;
    }

    /** Chọn ngẫu nhiên một chiều có min khác max. Trả về -1 nếu không còn chiều nào cắt được. */
    private int pickRandomSplittableFeature(List<double[]> samples, Random random) {
        int featureCount = samples.get(0).length;
        List<Integer> candidates = new ArrayList<>();
        for (int feature = 0; feature < featureCount; feature++) {
            if (minOfFeature(samples, feature) < maxOfFeature(samples, feature)) {
                candidates.add(feature);
            }
        }
        if (candidates.isEmpty()) {
            return -1;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private double minOfFeature(List<double[]> samples, int feature) {
        double min = Double.POSITIVE_INFINITY;
        for (double[] sample : samples) {
            if (sample[feature] < min) {
                min = sample[feature];
            }
        }
        return min;
    }

    private double maxOfFeature(List<double[]> samples, int feature) {
        double max = Double.NEGATIVE_INFINITY;
        for (double[] sample : samples) {
            if (sample[feature] > max) {
                max = sample[feature];
            }
        }
        return max;
    }

    /**
     * Độ dài đường đi từ gốc tới lá chứa mẫu này.
     * Nếu lá còn nhiều hơn 1 mẫu (chưa tách hết) thì cộng thêm độ dài đường đi trung bình dự kiến của chúng.
     */
    double pathLength(double[] sample) {
        Node node = root;
        int depth = 0;
        while (node.splitFeature != -1) {
            if (sample[node.splitFeature] < node.splitValue) {
                node = node.left;
            } else {
                node = node.right;
            }
            depth++;
        }
        return depth + IsolationForest.averagePathLength(node.size);
    }
}
