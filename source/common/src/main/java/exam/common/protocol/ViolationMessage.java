// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.ViolationType;

/**
 * VIOLATION (Client → Server): Client báo một vi phạm luật giám sát. LƯU Ý: tên field là violationType (không phải type) vì "type" đã dùng để phân loại message.
 */
public class ViolationMessage extends Message {
    /** Loại vi phạm */
    public ViolationType violationType;
    /** Bằng chứng, ví dụ tên process hoặc IP */
    public String evidence;
    /** Giờ client phát hiện (epoch milli giây) */
    public long time;

    public ViolationMessage() {
        super(MessageType.VIOLATION);
    }

    public ViolationMessage(ViolationType violationType, String evidence, long time) {
        this();
        this.violationType = violationType;
        this.evidence = evidence;
        this.time = time;
    }
}
