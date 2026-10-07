// Owner: Nguoi1

package exam.common.protocol;

/** Giá trị của field "type" cho từng message. Chi tiết từng message xem docs/PROTOCOL.md. */
public final class MessageType {

    private MessageType() {
    }

    // Đăng nhập, kết nối
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_OK = "LOGIN_OK";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String RECONNECT = "RECONNECT";
    public static final String RECONNECT_OK = "RECONNECT_OK";
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String HEARTBEAT_ACK = "HEARTBEAT_ACK";

    // Request/response chung (Teacher gọi Server)
    public static final String REQUEST = "REQUEST";
    public static final String RESPONSE = "RESPONSE";

    // Làm bài thi
    public static final String EXAM_START = "EXAM_START";
    public static final String TIME_SYNC = "TIME_SYNC";
    public static final String ANSWER = "ANSWER";
    public static final String ANSWER_ACK = "ANSWER_ACK";
    public static final String SUBMIT = "SUBMIT";
    public static final String SUBMIT_OK = "SUBMIT_OK";
    public static final String EXAM_END = "EXAM_END";

    // Giám sát
    public static final String RULES_CONFIG = "RULES_CONFIG";
    public static final String METRICS_DETAIL = "METRICS_DETAIL";
    public static final String VIOLATION = "VIOLATION";
    public static final String SET_RATE = "SET_RATE";
    public static final String ROOM_STATS = "ROOM_STATS";
    public static final String ALERT = "ALERT";

    // Khác
    public static final String NOTICE = "NOTICE";
    public static final String ERROR = "ERROR";
}
