// Owner: Nguoi1

package exam.common.protocol;

/**
 * ERROR (Server → Client): Server báo lỗi cho client (message sai định dạng, chưa đăng nhập, sai vai trò, token hỏng, ...).
 */
public class ErrorMessage extends Message {
    /** Xem bảng mã lỗi trong docs/PROTOCOL.md mục 5, ví dụ BAD_MESSAGE, NOT_LOGGED_IN, FORBIDDEN */
    public String code;
    public String message;

    public ErrorMessage() {
        super(MessageType.ERROR);
    }

    public ErrorMessage(String code, String message) {
        this();
        this.code = code;
        this.message = message;
    }
}
