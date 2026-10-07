// Owner: Nguoi1

package exam.common.protocol;

/**
 * TIME_SYNC (Server → Client): Server gửi định kỳ để đồng hồ đếm ngược của client khớp đồng hồ Server.
 */
public class TimeSyncMessage extends Message {
    public long serverTime;
    /** Số giây còn lại đến endTimeServer */
    public long remainingSeconds;

    public TimeSyncMessage() {
        super(MessageType.TIME_SYNC);
    }

    public TimeSyncMessage(long serverTime, long remainingSeconds) {
        this();
        this.serverTime = serverTime;
        this.remainingSeconds = remainingSeconds;
    }
}
