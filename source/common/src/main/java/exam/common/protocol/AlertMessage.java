// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.AlertLevel;

/**
 * ALERT (Server → Client): Server báo cảnh báo của một máy cho giáo viên.
 */
public class AlertMessage extends Message {
    /** Mã máy = mã sinh viên */
    public String machineId;
    /** YELLOW hoặc RED */
    public AlertLevel level;
    /** Ví dụ: kbSent gấp 5.2 lần trung vị phòng */
    public String reason;

    public AlertMessage() {
        super(MessageType.ALERT);
    }

    public AlertMessage(String machineId, AlertLevel level, String reason) {
        this();
        this.machineId = machineId;
        this.level = level;
        this.reason = reason;
    }
}
