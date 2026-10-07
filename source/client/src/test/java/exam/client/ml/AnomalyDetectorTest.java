// Owner: Nguoi4

package exam.client.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import exam.client.api.AnomalyScorer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Test quy trình: học N mẫu -> threshold = max(điểm lúc học) + 0.05 -> vượt threshold 3 lần liên tiếp mới cảnh báo. */
class AnomalyDetectorTest {

    /** Scorer giả: điểm = 0.4 nếu mẫu bình thường (sample[0] < 100), 0.9 nếu bất thường. fit() chỉ ghi lại mẫu. */
    private static class ScriptedScorer implements AnomalyScorer {
        List<double[]> fittedSamples;

        @Override
        public void fit(List<double[]> samples) {
            fittedSamples = new ArrayList<>(samples);
        }

        @Override
        public double score(double[] sample) {
            return sample[0] < 100 ? 0.4 : 0.9;
        }
    }

    private double[] normal() {
        return new double[] {10, 20, 30};
    }

    private double[] anomaly() {
        return new double[] {500, 20, 30};
    }

    @Test
    void duringTrainingNothingIsReportedAndNoScoring() {
        AnomalyDetector detector = new AnomalyDetector(new ScriptedScorer(), 5, 0.05, 3);

        for (int i = 0; i < 4; i++) {
            AnomalyDetector.Result result = detector.observe(anomaly()); // dù bất thường cũng chỉ là mẫu học
            assertEquals(AnomalyDetector.Phase.TRAINING, result.phase);
            assertFalse(result.anomalous);
        }
        assertEquals(AnomalyDetector.Phase.TRAINING, detector.getPhase());
    }

    @Test
    void trainingEndsAfterConfiguredNumberOfSamplesAndFitsOnAllOfThem() {
        ScriptedScorer scorer = new ScriptedScorer();
        AnomalyDetector detector = new AnomalyDetector(scorer, 5, 0.05, 3);

        for (int i = 0; i < 5; i++) {
            detector.observe(normal());
        }

        assertEquals(AnomalyDetector.Phase.DETECTING, detector.getPhase());
        assertEquals(5, scorer.fittedSamples.size());
    }

    @Test
    void thresholdIsMaxTrainingScorePlusMargin() {
        AnomalyDetector detector = new AnomalyDetector(new ScriptedScorer(), 4, 0.05, 3);

        for (int i = 0; i < 4; i++) {
            detector.observe(normal()); // mọi mẫu học đều điểm 0.4
        }

        assertEquals(0.45, detector.getThreshold(), 1e-9);
    }

    @Test
    void thresholdUsesTheHighestTrainingScore() {
        // Có một mẫu "xấu" lọt vào lúc học: điểm 0.9 trở thành max -> threshold 0.95 (cao hơn mọi điểm có thể)
        AnomalyDetector detector = new AnomalyDetector(new ScriptedScorer(), 4, 0.05, 3);
        detector.observe(normal());
        detector.observe(normal());
        detector.observe(anomaly());
        detector.observe(normal());

        assertEquals(0.95, detector.getThreshold(), 1e-9);
    }

    @Test
    void anomalyIsReportedOnlyAfterThreeConsecutiveExceedances() {
        AnomalyDetector detector = new AnomalyDetector(new ScriptedScorer(), 3, 0.05, 3);
        for (int i = 0; i < 3; i++) {
            detector.observe(normal());
        }

        AnomalyDetector.Result first = detector.observe(anomaly());
        AnomalyDetector.Result second = detector.observe(anomaly());
        AnomalyDetector.Result third = detector.observe(anomaly());

        assertFalse(first.anomalous);
        assertEquals(1, first.consecutive);
        assertFalse(second.anomalous);
        assertEquals(2, second.consecutive);
        assertTrue(third.anomalous);
        assertEquals(3, third.consecutive);
        assertEquals(0.9, third.score, 1e-9);
    }

    @Test
    void oneNormalSampleResetsTheConsecutiveCount() {
        AnomalyDetector detector = new AnomalyDetector(new ScriptedScorer(), 3, 0.05, 3);
        for (int i = 0; i < 3; i++) {
            detector.observe(normal());
        }

        detector.observe(anomaly());
        detector.observe(anomaly());
        detector.observe(normal()); // bị ngắt quãng
        AnomalyDetector.Result afterReset = detector.observe(anomaly());

        assertFalse(afterReset.anomalous);
        assertEquals(1, afterReset.consecutive);
    }

    @Test
    void scoreEqualToThresholdDoesNotExceedIt() {
        // "Vượt threshold" nghĩa là lớn hơn hẳn
        AnomalyScorer constantScorer = new AnomalyScorer() {
            @Override
            public void fit(List<double[]> samples) {
            }

            @Override
            public double score(double[] sample) {
                return 0.5;
            }
        };
        AnomalyDetector detector = new AnomalyDetector(constantScorer, 2, 0.0, 1);
        detector.observe(normal());
        detector.observe(normal());

        assertFalse(detector.observe(normal()).anomalous);
    }

    @Test
    void consecutiveRequirementIsConfigurable() {
        AnomalyDetector detector = new AnomalyDetector(new ScriptedScorer(), 2, 0.05, 1);
        detector.observe(normal());
        detector.observe(normal());

        assertTrue(detector.observe(anomaly()).anomalous);
    }

    @Test
    void worksEndToEndWithTheRealIsolationForest() {
        // 30 mẫu "bình thường" quanh (10,20,30,...) rồi các mẫu cực lớn
        Random random = new Random(5);
        AnomalyDetector detector = new AnomalyDetector(new IsolationForestScorer(), 30, 0.05, 3);
        for (int i = 0; i < 30; i++) {
            detector.observe(new double[] {10 + random.nextGaussian(), 20 + random.nextGaussian(), 30 + random.nextGaussian(),
                    4 + random.nextGaussian(), 20 + random.nextGaussian(), 40 + random.nextGaussian(), 100, 0});
        }
        assertEquals(AnomalyDetector.Phase.DETECTING, detector.getPhase());

        AnomalyDetector.Result normalResult = detector.observe(new double[] {10, 20, 30, 4, 20, 40, 100, 0});
        assertFalse(normalResult.anomalous);

        double[] cheating = {5000, 3000, 80, 40, 95, 90, 100, 0};
        detector.observe(cheating);
        detector.observe(cheating);
        AnomalyDetector.Result third = detector.observe(cheating);

        assertTrue(third.anomalous, "điểm " + third.score + " threshold " + third.threshold);
        assertTrue(third.score > third.threshold);
    }

    @Test
    void realIsolationForestScorerDelegatesToTheForest() {
        IsolationForestScorer scorer = new IsolationForestScorer();
        List<double[]> samples = new ArrayList<>();
        Random random = new Random(1);
        for (int i = 0; i < 50; i++) {
            samples.add(new double[] {random.nextGaussian(), random.nextGaussian()});
        }
        scorer.fit(samples);

        assertTrue(scorer.score(new double[] {50, 50}) > scorer.score(new double[] {0, 0}));
    }
}
