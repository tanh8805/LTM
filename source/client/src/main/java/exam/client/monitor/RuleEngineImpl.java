// Owner: Nguoi3

package exam.client.monitor;

import exam.client.api.RuleEngine;
import exam.common.model.MonitoringRules;
import exam.common.protocol.ViolationMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** STUB của RuleEngine: chưa kiểm tra luật nào, chỉ đếm số lần mất focus. */
public class RuleEngineImpl implements RuleEngine {

    // AtomicInteger vì onFocusLost() chạy trên thread giao diện còn getFocusLostCount() chạy trên thread khác.
    private final AtomicInteger focusLostCount = new AtomicInteger(0);

    @Override
    public void start(MonitoringRules rules) {
        // TODO(Nguoi3): Chụp mốc ban đầu: process, USB, network card. Resolve rules.blockedDomains ra IP.
    }

    @Override
    public List<ViolationMessage> checkForViolations() {
        // TODO(Nguoi3): Rule 1 process, Rule 2 domain/IP, Rule 3 USB, Rule 4 network card, Rule 5 focus quá ngưỡng.
        return new ArrayList<>();
    }

    @Override
    public void onFocusLost() {
        focusLostCount.incrementAndGet();
    }

    @Override
    public int getFocusLostCount() {
        return focusLostCount.get();
    }
}
