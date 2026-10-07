// Owner: Nguoi3

package exam.client.monitor;

import exam.client.api.AnomalyScorer;
import exam.client.api.MetricsSource;
import exam.client.api.RuleEngine;
import exam.client.api.ServerLink;
import exam.client.ml.AnomalyDetector;
import exam.common.ml.MlDecision;
import exam.common.model.Metrics;
import exam.common.model.MlMode;
import exam.common.model.MonitoringRules;
import exam.common.model.RateMode;
import exam.common.protocol.MetricsDetailMessage;
import exam.common.protocol.ViolationMessage;

/**
 * Giám sát chạy nền trên máy sinh viên.
 *
 * Hai nhịp:
 *   1. Nhịp HEARTBEAT (mỗi 10 giây, do StudentClient điều khiển): gọi takeSample() -> đo số liệu, kiểm tra 5 luật
 *      (gửi VIOLATION nếu có), chấm Isolation Forest -> trả về summary để gắn vào HEARTBEAT.
 *   2. Nhịp DETAIL (chỉ khi Server đặt HIGH hoặc BASELINE bằng SET_RATE): một virtual thread riêng cứ mỗi
 *      intervalMs (2 giây / 1 giây) đo số liệu và gửi METRICS_DETAIL. Về NORMAL thì thread này dừng.
 *
 * Isolation Forest chỉ chạy ở nhịp HEARTBEAT (để số mẫu học và số lần "liên tiếp" luôn tính theo cùng một chu kỳ),
 * và không chạy khi BASELINE ("không ML").
 */
public class MonitoringLoop {

    /** Kết quả một lần đo ở nhịp HEARTBEAT. */
    public static class Sample {
        public final Metrics metrics;
        public final double anomalyScore;
        public final boolean anomalous;

        Sample(Metrics metrics, double anomalyScore, boolean anomalous) {
            this.metrics = metrics;
            this.anomalyScore = anomalyScore;
            this.anomalous = anomalous;
        }
    }

    private final ServerLink serverLink;
    private final MetricsSource metricsSource;
    private final RuleEngine ruleEngine;
    private final AnomalyScorer anomalyScorer;

    private volatile boolean rulesStarted = false;
    private volatile RateMode rateMode = RateMode.NORMAL;
    private volatile Thread detailThread;
    private AnomalyDetector detector;
    private double latestScore = 0;
    private boolean latestAnomalous = false;
    private int detailSeq = 0;

    public MonitoringLoop(ServerLink serverLink, MetricsSource metricsSource,
                          RuleEngine ruleEngine, AnomalyScorer anomalyScorer) {
        this.serverLink = serverLink;
        this.metricsSource = metricsSource;
        this.ruleEngine = ruleEngine;
        this.anomalyScorer = anomalyScorer;
    }

    /**
     * Bắt đầu giám sát theo luật của ca thi (khi nhận RULES_CONFIG).
     *
     * @param heartbeatIntervalMs chu kỳ HEARTBEAT, dùng để đổi "thời gian học Isolation Forest" ra số mẫu
     */
    public synchronized void start(MonitoringRules rules, MlMode mlMode, int heartbeatIntervalMs) {
        ruleEngine.start(rules);

        if (MlDecision.usesIsolationForest(mlMode)) {
            int trainingSamples = (int) Math.max(1, rules.ifTrainingSeconds * 1000L / heartbeatIntervalMs);
            detector = new AnomalyDetector(anomalyScorer, trainingSamples, rules.ifThresholdMargin, rules.ifConsecutiveRequired);
            System.out.println("[Monitor] Isolation Forest sẽ học " + trainingSamples + " mẫu đầu tiên");
        } else {
            detector = null;
        }
        latestScore = 0;
        latestAnomalous = false;
        rulesStarted = true;
    }

    /** Nhịp HEARTBEAT: đo, kiểm tra luật, chấm điểm bất thường. */
    public synchronized Sample takeSample() {
        Metrics metrics = measure();

        if (rulesStarted && detector != null && rateMode != RateMode.BASELINE) {
            AnomalyDetector.Result result = detector.observe(metrics.toVector());
            latestScore = result.score;
            latestAnomalous = result.anomalous;
        } else if (rateMode == RateMode.BASELINE) {
            latestScore = 0;
            latestAnomalous = false;
        }
        return new Sample(metrics, latestScore, latestAnomalous);
    }

    /** Đo số liệu và (nếu đang giám sát) kiểm tra 5 luật; vi phạm được gửi lên Server ngay. */
    private Metrics measure() {
        Metrics metrics;
        try {
            metrics = metricsSource.collectMetrics();
        } catch (RuntimeException e) {
            // OSHI lỗi: gửi số liệu rỗng thay vì làm chết vòng giám sát (Server vẫn nhận được heartbeat).
            System.out.println("[Monitor] Không đo được số liệu: " + e);
            metrics = new Metrics();
        }
        metrics.focusLostCount = ruleEngine.getFocusLostCount();

        if (rulesStarted) {
            for (ViolationMessage violation : ruleEngine.checkForViolations()) {
                serverLink.send(violation);
            }
        }
        return metrics;
    }

    /** Server gửi SET_RATE. NORMAL: dừng thread detail. HIGH hoặc BASELINE: gửi METRICS_DETAIL mỗi intervalMs. */
    public synchronized void setRate(RateMode mode, int intervalMs) {
        rateMode = mode;
        stopDetailThread();
        if (mode == RateMode.NORMAL) {
            System.out.println("[Monitor] Chế độ NORMAL: chỉ gửi summary trong HEARTBEAT");
            return;
        }
        System.out.println("[Monitor] Chế độ " + mode + ": gửi METRICS_DETAIL mỗi " + intervalMs + " ms");
        Thread thread = new Thread(() -> runDetailLoop(intervalMs));
        thread.setDaemon(true);
        detailThread = thread;
        thread.start();
    }

    private void stopDetailThread() {
        Thread thread = detailThread;
        if (thread != null) {
            thread.interrupt();
            detailThread = null;
        }
    }

    private void runDetailLoop(int intervalMs) {
        Thread self = Thread.currentThread();
        while (!self.isInterrupted() && detailThread == self) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                // setRate() hoặc stop() yêu cầu dừng.
                return;
            }
            sendDetail();
        }
    }

    private synchronized void sendDetail() {
        Metrics metrics = measure();
        MetricsDetailMessage detail = new MetricsDetailMessage(
                ++detailSeq, System.currentTimeMillis(), metrics,
                safeProcessNames(), safeRemoteAddresses(), latestScore, latestAnomalous);
        serverLink.send(detail);
    }

    private java.util.List<String> safeProcessNames() {
        try {
            return metricsSource.listProcessNames();
        } catch (RuntimeException e) {
            System.out.println("[Monitor] Không đọc được danh sách process: " + e);
            return new java.util.ArrayList<>();
        }
    }

    private java.util.List<String> safeRemoteAddresses() {
        try {
            return metricsSource.listRemoteAddresses();
        } catch (RuntimeException e) {
            System.out.println("[Monitor] Không đọc được danh sách kết nối: " + e);
            return new java.util.ArrayList<>();
        }
    }

    /** Dừng giám sát (hết ca thi). */
    public synchronized void stop() {
        rulesStarted = false;
        stopDetailThread();
        rateMode = RateMode.NORMAL;
        detector = null;
    }

    public RateMode getRateMode() {
        return rateMode;
    }
}
