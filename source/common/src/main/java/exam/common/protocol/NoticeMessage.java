// Owner: Nguoi1

package exam.common.protocol;

/**
 * NOTICE (hai chiều): Thông báo của giáo viên. Giáo viên gửi lên Server, Server chuyển tới sinh viên.
 */
public class NoticeMessage extends Message {
    /** machineId của một máy, hoặc "ALL" cho cả phòng */
    public String target;
    public String text;

    public NoticeMessage() {
        super(MessageType.NOTICE);
    }

    public NoticeMessage(String target, String text) {
        this();
        this.target = target;
        this.text = text;
    }
}
