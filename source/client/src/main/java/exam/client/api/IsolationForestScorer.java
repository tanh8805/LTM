// Owner: Nguoi4

package exam.client.api;

import exam.common.ml.IsolationForest;
import java.util.List;

/**
 * Lớp nối mỏng: AnomalyScorer (của client) -> IsolationForest (thuật toán dùng chung ở module common).
 * Thuật toán nằm ở common để cả client và tools (Simulator, ExperimentRunner) dùng chung.
 *
 * File này nằm trong exam.client.api nhưng thuộc sở hữu của Nguoi4 (xem .github/CODEOWNERS).
 */
public class IsolationForestScorer implements AnomalyScorer {

    private final IsolationForest forest = new IsolationForest();

    @Override
    public void fit(List<double[]> samples) {
        forest.fit(samples);
    }

    @Override
    public double score(double[] sample) {
        return forest.score(sample);
    }
}
