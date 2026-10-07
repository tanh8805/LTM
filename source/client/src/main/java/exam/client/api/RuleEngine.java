// Owner: Nguoi1

package exam.client.api;

import exam.common.model.MonitoringRules;
import exam.common.protocol.ViolationMessage;
import java.util.List;

/**
 * Kiểm tra 5 luật giám sát (process, domain/IP, USB, network card, focus).
 *
 * Ai cài đặt: Nguoi3 (exam.client.monitor.RuleEngineImpl)
 * Ai gọi: Nguoi3 (MonitoringLoop gọi start và checkForViolations),
 *         Nguoi2 (StudentFrame gọi onFocusLost khi cửa sổ thi mất focus)
 */
public interface RuleEngine {

    /**
     * Bắt đầu giám sát: chụp danh sách process, USB, network card lúc này làm mốc,
     * resolve các domain bị chặn ra IP.
     */
    void start(MonitoringRules rules);

    /** So với mốc ban đầu, trả về các vi phạm MỚI kể từ lần gọi trước. Rỗng nếu không có. */
    List<ViolationMessage> checkForViolations();

    /** Cửa sổ thi vừa mất focus. */
    void onFocusLost();

    /** Tổng số lần mất focus từ đầu ca thi. */
    int getFocusLostCount();
}
