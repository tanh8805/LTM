// Owner: Nguoi1

package exam.common.protocol;

/**
 * HEARTBEAT_ACK (Server → Client): Server xác nhận đã nhận HEARTBEAT.
 */
public class HeartbeatAckMessage extends Message {
    /** seq của HEARTBEAT được xác nhận */
    public int seq;
    public long serverTime;

    public HeartbeatAckMessage() {
        super(MessageType.HEARTBEAT_ACK);
    }

    public HeartbeatAckMessage(int seq, long serverTime) {
        this();
        this.seq = seq;
        this.serverTime = serverTime;
    }
}
