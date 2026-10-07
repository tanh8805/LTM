// Owner: Nguoi1

package exam.common.protocol;

import java.util.Map;

/**
 * RECONNECT_OK (Server → Client): Nối lại thành công, kèm dữ liệu để khôi phục bài làm.
 */
public class ReconnectOkMessage extends Message {
    /** Giờ hết bài (epoch milli giây, đồng hồ Server) */
    public long endTimeServer;
    /** questionId -> choice các câu đã trả lời */
    public Map<Integer, Integer> answers;
    public long serverTime;

    public ReconnectOkMessage() {
        super(MessageType.RECONNECT_OK);
    }

    public ReconnectOkMessage(long endTimeServer, Map<Integer, Integer> answers, long serverTime) {
        this();
        this.endTimeServer = endTimeServer;
        this.answers = answers;
        this.serverTime = serverTime;
    }
}
