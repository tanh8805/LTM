// Owner: Nguoi1

package exam.common.protocol;

import com.google.gson.JsonObject;

/**
 * RESPONSE (Server → Client): Kết quả của một REQUEST.
 */
public class ResponseMessage extends Message {
    /** Giống requestId của REQUEST */
    public String requestId;
    public boolean ok;
    /** Kết quả khi ok = true */
    public JsonObject data;
    /** Lý do lỗi khi ok = false */
    public String error;

    public ResponseMessage() {
        super(MessageType.RESPONSE);
    }

    public ResponseMessage(String requestId, boolean ok, JsonObject data, String error) {
        this();
        this.requestId = requestId;
        this.ok = ok;
        this.data = data;
        this.error = error;
    }
}
