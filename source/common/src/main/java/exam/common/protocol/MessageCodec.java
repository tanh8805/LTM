// Owner: Nguoi1

package exam.common.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Map;

/**
 * Chuyển qua lại giữa object Message và một dòng JSON.
 *
 *   encode(message) -> "{...}"          (chưa có '\n', người gửi tự thêm)
 *   decode("{...}") -> object Message   (dựa vào field "type")
 *
 * Dùng chung cho Server và Client nên hai phía luôn hiểu nhau.
 * Lỗi định dạng (không phải JSON, thiếu "type", "type" lạ) -> IllegalArgumentException.
 */
public class MessageCodec {

    // Gson không giữ trạng thái nên dùng chung một instance được, kể cả nhiều thread.
    // disableHtmlEscaping: để dấu '=' '<' '>' trong chuỗi không bị đổi thành =.
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * Object -> một dòng JSON (không có xuống dòng).
     * Field "type" luôn được đặt đầu tiên cho dễ đọc khi debug, ví dụ: {"type":"LOGIN_OK","token":"..."}
     */
    public String encode(Message message) {
        JsonObject body = gson.toJsonTree(message).getAsJsonObject();

        JsonObject json = new JsonObject();
        json.addProperty("type", message.type);
        for (Map.Entry<String, JsonElement> field : body.entrySet()) {
            if (!field.getKey().equals("type")) {
                json.add(field.getKey(), field.getValue());
            }
        }
        return gson.toJson(json);
    }

    /** Một dòng JSON -> object đúng loại message. */
    public Message decode(String jsonLine) {
        JsonObject json = parseJsonObject(jsonLine);

        if (!json.has("type") || !json.get("type").isJsonPrimitive()) {
            throw new IllegalArgumentException("Message không có field \"type\": " + jsonLine);
        }
        String type = json.get("type").getAsString();

        Class<? extends Message> messageClass = findMessageClass(type);
        try {
            return gson.fromJson(json, messageClass);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Message " + type + " sai định dạng: " + jsonLine, e);
        }
    }

    private JsonObject parseJsonObject(String jsonLine) {
        try {
            return JsonParser.parseString(jsonLine).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            // JsonParseException: không phải JSON. IllegalStateException: là JSON nhưng không phải object.
            throw new IllegalArgumentException("Không phải JSON object: " + jsonLine, e);
        }
    }

    /** Bảng tra: giá trị "type" -> class Java. Thêm message mới thì thêm một dòng ở đây. */
    private Class<? extends Message> findMessageClass(String type) {
        switch (type) {
            case MessageType.LOGIN:           return LoginMessage.class;
            case MessageType.LOGIN_OK:        return LoginOkMessage.class;
            case MessageType.LOGIN_FAIL:      return LoginFailMessage.class;
            case MessageType.RECONNECT:       return ReconnectMessage.class;
            case MessageType.RECONNECT_OK:    return ReconnectOkMessage.class;
            case MessageType.HEARTBEAT:       return HeartbeatMessage.class;
            case MessageType.HEARTBEAT_ACK:   return HeartbeatAckMessage.class;
            case MessageType.REQUEST:         return RequestMessage.class;
            case MessageType.RESPONSE:        return ResponseMessage.class;
            case MessageType.EXAM_START:      return ExamStartMessage.class;
            case MessageType.TIME_SYNC:       return TimeSyncMessage.class;
            case MessageType.ANSWER:          return AnswerMessage.class;
            case MessageType.ANSWER_ACK:      return AnswerAckMessage.class;
            case MessageType.SUBMIT:          return SubmitMessage.class;
            case MessageType.SUBMIT_OK:       return SubmitOkMessage.class;
            case MessageType.EXAM_END:        return ExamEndMessage.class;
            case MessageType.RULES_CONFIG:    return RulesConfigMessage.class;
            case MessageType.METRICS_DETAIL:  return MetricsDetailMessage.class;
            case MessageType.VIOLATION:       return ViolationMessage.class;
            case MessageType.SET_RATE:        return SetRateMessage.class;
            case MessageType.ROOM_STATS:      return RoomStatsMessage.class;
            case MessageType.ALERT:           return AlertMessage.class;
            case MessageType.NOTICE:          return NoticeMessage.class;
            case MessageType.ERROR:           return ErrorMessage.class;
            default:
                throw new IllegalArgumentException("Không biết message type: " + type);
        }
    }
}
