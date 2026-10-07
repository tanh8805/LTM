// Owner: Nguoi1

package exam.common.protocol;

import exam.common.model.Role;

/**
 * LOGIN (Client → Server): Đăng nhập. Sinh viên gửi mã SV + mật khẩu + mã ca thi; giáo viên gửi username + mật khẩu.
 */
public class LoginMessage extends Message {
    /** Giáo viên: username. Sinh viên: mã sinh viên */
    public String username;
    public String password;
    /** TEACHER hoặc STUDENT */
    public Role role;
    /** Mã ca thi (chỉ sinh viên, giáo viên để null) */
    public String examCode;

    public LoginMessage() {
        super(MessageType.LOGIN);
    }

    public LoginMessage(String username, String password, Role role, String examCode) {
        this();
        this.username = username;
        this.password = password;
        this.role = role;
        this.examCode = examCode;
    }
}
