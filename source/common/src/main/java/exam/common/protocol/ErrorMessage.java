// Owner: Nguoi1

package exam.common.protocol;

/**
 * ERROR (Server → Client): Server báo lỗi cho client (message sai định dạng, chưa đăng nhập, chưa hỗ trợ, ...).
 */
public class ErrorMessage extends Message {
    /** Ví dụ: BAD_MESSAGE, NOT_LOGGED_IN, NOT_IMPLEMENTED */
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
