// Owner: Nguoi1

package exam.common.protocol;

import com.google.gson.JsonObject;

/**
 * REQUEST (Client → Server): Yêu cầu chung (chủ yếu Teacher: quản lý câu hỏi, đề, ca thi, xem điểm). Server trả RESPONSE cùng requestId.
 */
public class RequestMessage extends Message {
    /** Client tự sinh, dùng để ghép với RESPONSE */
    public String requestId;
    /** Ví dụ: LIST_QUESTIONS */
    public String action;
    /** Tham số của action */
    public JsonObject data;

    public RequestMessage() {
        super(MessageType.REQUEST);
    }

    public RequestMessage(String requestId, String action, JsonObject data) {
        this();
        this.requestId = requestId;
        this.action = action;
        this.data = data;
    }
}
