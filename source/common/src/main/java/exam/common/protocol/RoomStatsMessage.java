// Owner: Nguoi1

package exam.common.protocol;

import java.util.Map;

/**
 * ROOM_STATS (Server → Client): Trung vị và MAD của từng metric trên cả phòng, để client tự so sánh.
 */
public class RoomStatsMessage extends Message {
    public long time;
    /** tên metric -> trung vị cả phòng */
    public Map<String, Double> medians;
    /** tên metric -> MAD cả phòng */
    public Map<String, Double> mads;

    public RoomStatsMessage() {
        super(MessageType.ROOM_STATS);
    }

    public RoomStatsMessage(long time, Map<String, Double> medians, Map<String, Double> mads) {
        this();
        this.time = time;
        this.medians = medians;
        this.mads = mads;
    }
}
