// Owner: Nguoi1

package exam.common.model;

import java.util.ArrayList;
import java.util.List;

/** Cấu hình luật giám sát giáo viên đặt cho một ca thi (gửi xuống client trong RULES_CONFIG). */
public class MonitoringRules {
    /** Rule 1: process được phép thêm, ngoài danh sách chụp lúc bắt đầu. */
    public List<String> processAllowlist = new ArrayList<>();
    /** Rule 1: process bị cấm. */
    public List<String> processDenylist = new ArrayList<>();
    /** Rule 2: domain bị chặn. Client resolve ra IP lúc bắt đầu. */
    public List<String> blockedDomains = new ArrayList<>();
    /** Rule 5: mất focus quá số lần này thì cảnh báo. */
    public int focusLossThreshold = 3;

    /** Bộ luật mặc định theo SPEC. */
    public static MonitoringRules createDefault() {
        MonitoringRules rules = new MonitoringRules();
        rules.processDenylist.add("Zalo");
        rules.processDenylist.add("Telegram");
        rules.processDenylist.add("Messenger");
        rules.processDenylist.add("TeamViewer");
        rules.processDenylist.add("AnyDesk");
        rules.blockedDomains.add("chatgpt.com");
        rules.blockedDomains.add("api.openai.com");
        rules.blockedDomains.add("gemini.google.com");
        return rules;
    }
}
