// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.MlMode;
import exam.common.model.MonitoringRules;

/**
 * RULES_CONFIG (Server → Client): Server gửi luật giám sát cho client khi bắt đầu ca thi.
 */
public class RulesConfigMessage extends Message {
    public MonitoringRules rules;
    /** Client biết có phải chạy Isolation Forest không */
    public MlMode mlMode;

    public RulesConfigMessage() {
        super(MessageType.RULES_CONFIG);
    }

    public RulesConfigMessage(MonitoringRules rules, MlMode mlMode) {
        this();
        this.rules = rules;
        this.mlMode = mlMode;
    }
}
