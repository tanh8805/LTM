// Owner: Nguoi1

package exam.common.model;

/** Người dùng sau khi đăng nhập thành công. Không chứa password. */
public class UserAccount {
    public int id;
    /** Giáo viên: tên đăng nhập. Sinh viên: mã sinh viên (ví dụ SV001). */
    public String username;
    public String fullName;
    public Role role;

    public UserAccount() {
    }

    public UserAccount(int id, String username, String fullName, Role role) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }
}
