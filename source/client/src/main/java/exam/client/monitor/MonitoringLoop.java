// Owner: Nguoi3

package exam.client.monitor;

import exam.client.api.AnomalyScorer;
import exam.client.api.MetricsSource;
import exam.client.api.RuleEngine;
import exam.client.api.ServerLink;
import exam.common.model.RateMode;

/**
 * Giám sát chạy nền trên máy sinh viên.
 *
 * Mỗi chu kỳ (mặc định 10 giây): đo số liệu -> kiểm tra luật (VIOLATION) -> chấm Isolation Forest -> gửi lên Server.
 * HIỆN LÀ STUB: start() chưa làm gì.
 */
public class MonitoringLoop {

    // Các field này chưa dùng, sẽ dùng khi cài đặt các TODO bên dưới.
    private final ServerLink serverLink;
    private final MetricsSource metricsSource;
    private final RuleEngine ruleEngine;
    private final AnomalyScorer anomalyScorer;

    public MonitoringLoop(ServerLink serverLink, MetricsSource metricsSource,
                          RuleEngine ruleEngine, AnomalyScorer anomalyScorer) {
        this.serverLink = serverLink;
        this.metricsSource = metricsSource;
        this.ruleEngine = ruleEngine;
        this.anomalyScorer = anomalyScorer;
    }

    public void start() {
        // TODO(Nguoi3): Chạy vòng lặp nền (virtual thread): metricsSource.collectMetrics(), ruleEngine.checkForViolations()
        //  -> serverLink.send(VIOLATION). Phối hợp Nguoi4: 5 phút đầu anomalyScorer.fit(...), sau đó so threshold
        //  max(training score) + 0.05, vượt 3 lần liên tiếp thì báo.
        System.out.println("[Monitor] MonitoringLoop (stub) - chưa giám sát gì");
    }

    /** Server gửi SET_RATE: đổi chu kỳ gửi số liệu. */
    public void setRate(RateMode mode, int intervalMs) {
        // TODO(Nguoi3): NORMAL = summary trong HEARTBEAT mỗi 10s, HIGH = METRICS_DETAIL mỗi 2s, BASELINE = mỗi 1s không ML.
    }

    public void stop() {
        // TODO(Nguoi3): Dừng vòng lặp nền.
    }
}
