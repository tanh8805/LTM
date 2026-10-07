// Owner: Nguoi3

package exam.client.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import exam.client.api.AnomalyScorer;
import exam.client.api.ConnectionStatus;
import exam.client.ml.IsolationForestScorer;
import exam.client.testutil.FakeMetricsSource;
import exam.client.testutil.FakeServerLink;
import exam.client.testutil.Wait;
import exam.common.model.Metrics;
import exam.common.model.MlMode;
import exam.common.model.MonitoringRules;
import exam.common.model.RateMode;
import exam.common.model.ViolationType;
import exam.common.protocol.MetricsDetailMessage;
import exam.common.protocol.ViolationMessage;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitoringLoopTest {

    /** Scorer giả: điểm 0.4 nếu kbSent < 100, ngược lại 0.9. */
    private static class SimpleScorer implements AnomalyScorer {
        @Override
        public void fit(List<double[]> samples) {
        }

        @Override
        public double score(double[] sample) {
            return sample[0] < 100 ? 0.4 : 0.9;
        }
    }

    private FakeServerLink link;
    private FakeMetricsSource source;
    private RuleEngineImpl ruleEngine;
    private MonitoringLoop loop;
    private MonitoringRules rules;

    @BeforeEach
    void setUp() throws Exception {
        link = new FakeServerLink();
        link.connect("localhost", 0); // trạng thái CONNECTED để send() hoạt động
        source = new FakeMetricsSource();
        ruleEngine = new RuleEngineImpl(source, domain -> List.of());
        loop = new MonitoringLoop(link, source, ruleEngine, new SimpleScorer());

        rules = MonitoringRules.createDefault();
        rules.ifTrainingSeconds = 30; // 30 giây / chu kỳ 10 giây = học 3 mẫu
        rules.ifConsecutiveRequired = 3;
        rules.ifThresholdMargin = 0.05;
    }

    @AfterEach
    void tearDown() {
        loop.stop();
    }

    private void setKbSent(double value) {
        source.nextMetrics = new Metrics(value, 5, 4, 3, 20, 40, 100, 0);
    }

    @Test
    void sampleBeforeStartJustMeasuresWithoutRulesOrMl() {
        source.processes.add("Zalo");

        MonitoringLoop.Sample sample = loop.takeSample();

        assertEquals(20.0, sample.metrics.cpuPercent);
        assertFalse(sample.anomalous);
        assertTrue(link.sentOfType(ViolationMessage.class).isEmpty(), "chưa bắt đầu giám sát thì chưa kiểm luật");
    }

    @Test
    void sampleChecksRulesAndSendsViolationsToServer() {
        loop.start(rules, MlMode.NONE, 10_000);
        source.usbDevices.add("USB Flash [x]");

        loop.takeSample();

        List<ViolationMessage> violations = link.sentOfType(ViolationMessage.class);
        assertEquals(1, violations.size());
        assertEquals(ViolationType.USB_DEVICE, violations.get(0).violationType);
    }

    @Test
    void sampleCarriesFocusLostCount() {
        loop.start(rules, MlMode.NONE, 10_000);
        ruleEngine.onFocusLost();
        ruleEngine.onFocusLost();

        MonitoringLoop.Sample sample = loop.takeSample();

        assertEquals(2, sample.metrics.focusLostCount);
    }

    @Test
    void isolationForestRunsOnlyWhenModeUsesIt() {
        loop.start(rules, MlMode.CHRONOS, 10_000); // chỉ Chronos: client không chạy IF
        setKbSent(5000);

        for (int i = 0; i < 10; i++) {
            assertFalse(loop.takeSample().anomalous);
        }
    }

    @Test
    void isolationForestLearnsThenFlagsThreeConsecutiveAnomalies() {
        loop.start(rules, MlMode.IF, 10_000);
        setKbSent(10);
        for (int i = 0; i < 3; i++) {
            assertFalse(loop.takeSample().anomalous); // 3 mẫu học
        }
        assertFalse(loop.takeSample().anomalous); // 1 mẫu bình thường

        setKbSent(5000);
        assertFalse(loop.takeSample().anomalous);
        assertFalse(loop.takeSample().anomalous);
        MonitoringLoop.Sample third = loop.takeSample();

        assertTrue(third.anomalous);
        assertEquals(0.9, third.anomalyScore, 1e-9);

        setKbSent(10);
        assertFalse(loop.takeSample().anomalous, "về bình thường thì hết cảnh báo");
    }

    @Test
    void trainingLengthIsConvertedFromSecondsUsingHeartbeatInterval() {
        rules.ifTrainingSeconds = 300; // 5 phút
        loop.start(rules, MlMode.IF, 10_000); // 10 giây/mẫu -> 30 mẫu học
        setKbSent(10);
        for (int i = 0; i < 29; i++) {
            loop.takeSample();
        }
        setKbSent(5000);

        // Mới học 29/30 mẫu: mẫu thứ 30 (dù bất thường) vẫn là mẫu học, nên chưa thể cảnh báo
        for (int i = 0; i < 3; i++) {
            assertFalse(loop.takeSample().anomalous);
        }
    }

    @Test
    void baselineModeDisablesIsolationForest() {
        loop.start(rules, MlMode.IF, 10_000);
        setKbSent(10);
        for (int i = 0; i < 4; i++) {
            loop.takeSample();
        }
        loop.setRate(RateMode.BASELINE, 60_000);
        setKbSent(5000);

        for (int i = 0; i < 5; i++) {
            assertFalse(loop.takeSample().anomalous, "BASELINE: không ML");
        }
    }

    @Test
    void highModeSendsDetailMessagesAtTheGivenInterval() {
        loop.start(rules, MlMode.NONE, 10_000);
        source.processes.addAll(List.of("java", "chrome"));
        source.remoteAddresses.add("8.8.8.8");

        loop.setRate(RateMode.HIGH, 100);

        assertTrue(Wait.until(() -> link.sentOfType(MetricsDetailMessage.class).size() >= 3, 3000));
        MetricsDetailMessage detail = link.sentOfType(MetricsDetailMessage.class).get(0);
        assertEquals(List.of("java", "chrome"), detail.processNames);
        assertEquals(List.of("8.8.8.8"), detail.remoteAddresses);
        assertEquals(20.0, detail.metrics.cpuPercent);
        assertTrue(detail.time > 0);
    }

    @Test
    void detailSequenceNumbersIncrease() {
        loop.setRate(RateMode.BASELINE, 50);
        assertTrue(Wait.until(() -> link.sentOfType(MetricsDetailMessage.class).size() >= 3, 3000));

        List<MetricsDetailMessage> details = link.sentOfType(MetricsDetailMessage.class);
        assertTrue(details.get(1).seq > details.get(0).seq);
        assertTrue(details.get(2).seq > details.get(1).seq);
    }

    @Test
    void returningToNormalStopsDetailMessages() throws Exception {
        loop.setRate(RateMode.HIGH, 50);
        assertTrue(Wait.until(() -> link.sentOfType(MetricsDetailMessage.class).size() >= 2, 3000));

        loop.setRate(RateMode.NORMAL, 10_000);
        Thread.sleep(200); // cho thread detail dừng hẳn
        int countAfterStop = link.sentOfType(MetricsDetailMessage.class).size();
        Thread.sleep(400);

        assertEquals(countAfterStop, link.sentOfType(MetricsDetailMessage.class).size());
        assertEquals(RateMode.NORMAL, loop.getRateMode());
    }

    @Test
    void detailModeAlsoReportsViolationsQuickly() {
        loop.start(rules, MlMode.NONE, 10_000);
        loop.setRate(RateMode.HIGH, 50);
        source.networkCards.add("tun0");

        assertTrue(Wait.until(() -> !link.sentOfType(ViolationMessage.class).isEmpty(), 3000),
                "ở chế độ HIGH vi phạm phải được báo trong vài trăm ms chứ không chờ 10 giây");
    }

    @Test
    void measurementFailureDoesNotCrashTheLoop() {
        FakeMetricsSource brokenSource = new FakeMetricsSource() {
            @Override
            public Metrics collectMetrics() {
                throw new IllegalStateException("OSHI lỗi (giả lập)");
            }
        };
        MonitoringLoop brokenLoop = new MonitoringLoop(link, brokenSource,
                new RuleEngineImpl(brokenSource, domain -> List.of()), new IsolationForestScorer());

        MonitoringLoop.Sample sample = brokenLoop.takeSample();

        assertEquals(0.0, sample.metrics.cpuPercent);
        assertEquals(ConnectionStatus.CONNECTED, link.getStatus());
    }
}
