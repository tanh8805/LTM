// Owner: Nguoi1

package exam.common.protocol;

/**
 * LOGIN_FAIL (Server → Client): Đăng nhập thất bại.
 */
public class LoginFailMessage extends Message {
    /** Ví dụ: sai mật khẩu */
    public String reason;

    public LoginFailMessage() {
        super(MessageType.LOGIN_FAIL);
    }

    public LoginFailMessage(String reason) {
        this();
        this.reason = reason;
    }
}
