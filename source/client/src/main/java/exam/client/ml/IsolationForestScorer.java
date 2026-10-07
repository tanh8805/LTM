// Owner: Nguoi4

package exam.client.ml;

import exam.client.api.AnomalyScorer;
import exam.common.ml.IsolationForest;
import java.util.List;

/**
 * Lớp nối mỏng: AnomalyScorer (của client) -> IsolationForest (thuật toán dùng chung ở module common).
 *
 * Thuật toán nằm ở common để cả client và tools (Simulator, ExperimentRunner) dùng chung.
 * Phụ thuộc đi một chiều: client -> common (common KHÔNG import client).
 */
public class IsolationForestScorer implements AnomalyScorer {

    private final IsolationForest forest;

    public IsolationForestScorer() {
        this.forest = new IsolationForest();
    }

    public IsolationForestScorer(IsolationForest forest) {
        this.forest = forest;
    }

    @Override
    public void fit(List<double[]> samples) {
        forest.fit(samples);
    }

    @Override
    public double score(double[] sample) {
        return forest.score(sample);
    }
}
