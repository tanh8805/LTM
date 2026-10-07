// Owner: Nguoi4

package exam.common.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IsolationForestTest {

    /** 200 điểm "bình thường" tập trung quanh (10, 10, ..., 10) với nhiễu nhỏ. */
    private List<double[]> createNormalCluster(int count, int dimensions, long seed) {
        Random random = new Random(seed);
        List<double[]> samples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double[] sample = new double[dimensions];
            for (int d = 0; d < dimensions; d++) {
                sample[d] = 10 + random.nextGaussian();
            }
            samples.add(sample);
        }
        return samples;
    }

    private double[] farAwayPoint(int dimensions) {
        double[] point = new double[dimensions];
        for (int d = 0; d < dimensions; d++) {
            point[d] = 100;
        }
        return point;
    }

    @Test
    void outlierScoresHigherThanNormalPoint() {
        IsolationForest forest = new IsolationForest();
        forest.fit(createNormalCluster(200, 8, 1));

        double normalScore = forest.score(new double[] {10, 10, 10, 10, 10, 10, 10, 10});
        double outlierScore = forest.score(farAwayPoint(8));

        assertTrue(outlierScore > normalScore, "outlier=" + outlierScore + " normal=" + normalScore);
        assertTrue(outlierScore > 0.6, "outlier score quá thấp: " + outlierScore);
        assertTrue(normalScore < 0.5, "normal score quá cao: " + normalScore);
    }

    @Test
    void scoreIsAlwaysBetweenZeroAndOne() {
        IsolationForest forest = new IsolationForest();
        List<double[]> samples = createNormalCluster(100, 3, 2);
        forest.fit(samples);

        for (double[] sample : samples) {
            double score = forest.score(sample);
            assertTrue(score >= 0.0 && score <= 1.0, "score ngoài [0,1]: " + score);
        }
        double outlierScore = forest.score(farAwayPoint(3));
        assertTrue(outlierScore >= 0.0 && outlierScore <= 1.0);
    }

    @Test
    void sameSeedGivesSameScore() {
        List<double[]> samples = createNormalCluster(100, 4, 3);
        double[] probe = {12, 9, 15, 7};

        IsolationForest first = new IsolationForest(50, 64, 7L);
        first.fit(samples);
        IsolationForest second = new IsolationForest(50, 64, 7L);
        second.fit(samples);

        assertEquals(first.score(probe), second.score(probe), 1e-12);
    }

    @Test
    void differentSeedsCanGiveDifferentScores() {
        List<double[]> samples = createNormalCluster(100, 4, 3);
        double[] probe = {12, 9, 15, 7};

        IsolationForest first = new IsolationForest(10, 64, 1L);
        first.fit(samples);
        IsolationForest second = new IsolationForest(10, 64, 2L);
        second.fit(samples);

        assertFalse(first.score(probe) == second.score(probe));
    }

    @Test
    void fitWithTooFewSamplesStillWorks() {
        // Giống client vừa học xong 3 mẫu: vẫn phải chạy được và phân biệt được điểm xa.
        IsolationForest forest = new IsolationForest();
        List<double[]> samples = new ArrayList<>();
        samples.add(new double[] {1, 1});
        samples.add(new double[] {1.1, 1.0});
        samples.add(new double[] {0.9, 1.1});
        forest.fit(samples);

        assertTrue(forest.score(new double[] {50, 50}) > forest.score(new double[] {1, 1}));
    }

    @Test
    void constantTrainingDataStillScoresDifferentPointHigher() {
        // Máy rảnh rỗi: mọi mẫu giống hệt nhau (không cắt được chiều nào).
        IsolationForest forest = new IsolationForest();
        List<double[]> samples = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            samples.add(new double[] {0, 0, 5, 3});
        }
        forest.fit(samples);

        double sameScore = forest.score(new double[] {0, 0, 5, 3});
        double otherScore = forest.score(new double[] {500, 400, 60, 40});
        assertTrue(otherScore >= sameScore);
        assertTrue(sameScore >= 0.0 && sameScore <= 1.0);
    }

    @Test
    void fitRejectsEmptySamples() {
        IsolationForest forest = new IsolationForest();
        assertThrows(IllegalArgumentException.class, () -> forest.fit(new ArrayList<>()));
    }

    @Test
    void fitRejectsSamplesWithDifferentDimensions() {
        IsolationForest forest = new IsolationForest();
        List<double[]> samples = new ArrayList<>();
        samples.add(new double[] {1, 2});
        samples.add(new double[] {1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> forest.fit(samples));
    }

    @Test
    void scoreBeforeFitFails() {
        IsolationForest forest = new IsolationForest();
        assertFalse(forest.isFitted());
        assertThrows(IllegalStateException.class, () -> forest.score(new double[] {1, 2}));
    }

    @Test
    void scoreRejectsWrongDimension() {
        IsolationForest forest = new IsolationForest();
        forest.fit(createNormalCluster(20, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> forest.score(new double[] {1, 2}));
    }

    @Test
    void averagePathLengthMatchesFormula() {
        assertEquals(0.0, IsolationForest.averagePathLength(1), 1e-12);
        assertEquals(1.0, IsolationForest.averagePathLength(2), 1e-12);
        // c(256) ~ 10.24 theo bài báo gốc
        assertEquals(10.24, IsolationForest.averagePathLength(256), 0.05);
    }
}
