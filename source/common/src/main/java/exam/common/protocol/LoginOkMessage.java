// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.Role;

/**
 * LOGIN_OK (Server → Client): Đăng nhập thành công. token dùng cho RECONNECT.
 */
public class LoginOkMessage extends Message {
    /** Mã dùng để RECONNECT khi mất kết nối */
    public String token;
    public int userId;
    public String fullName;
    public Role role;
    /** Giờ Server lúc trả lời (epoch milli giây) */
    public long serverTime;

    public LoginOkMessage() {
        super(MessageType.LOGIN_OK);
    }

    public LoginOkMessage(String token, int userId, String fullName, Role role, long serverTime) {
        this();
        this.token = token;
        this.userId = userId;
        this.fullName = fullName;
        this.role = role;
        this.serverTime = serverTime;
    }
}
