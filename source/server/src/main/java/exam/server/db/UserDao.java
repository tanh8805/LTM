// Owner: Nguoi2

package exam.server.db;

import exam.common.model.Role;
import exam.common.model.UserAccount;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Truy vấn bảng teachers và students (đăng nhập). */
public class UserDao {

    private final Database database;
    private final PasswordHasher passwordHasher = new PasswordHasher();

    public UserDao(Database database) {
        this.database = database;
    }

    /** Trả về người dùng nếu đúng username + password, ngược lại trả về null. */
    public UserAccount findByUsernameAndPassword(Role role, String username, String password) throws SQLException {
        // Giáo viên và sinh viên nằm ở hai bảng riêng nên chọn đúng câu SQL theo vai trò.
        String sql;
        if (role == Role.TEACHER) {
            sql = """
                SELECT id, username, full_name, password_hash
                FROM teachers
                WHERE username = ?
                """;
        } else {
            sql = """
                SELECT id, username, full_name, password_hash
                FROM students
                WHERE username = ?
                """;
        }

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                // Băm mật khẩu người dùng nhập rồi so với hash đã lưu (không bao giờ so chữ thường).
                if (!passwordHasher.verify(password, resultSet.getString("password_hash"))) {
                    return null;
                }
                return new UserAccount(
                        resultSet.getInt("id"),
                        resultSet.getString("username"),
                        resultSet.getString("full_name"),
                        role);
            }
        }
    }

    /** Tìm sinh viên theo mã sinh viên (không cần mật khẩu). Trả về null nếu không có. */
    public UserAccount findStudentByUsername(String studentCode) throws SQLException {
        String sql = """
            SELECT id, username, full_name
            FROM students
            WHERE username = ?
            """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new UserAccount(
                        resultSet.getInt("id"),
                        resultSet.getString("username"),
                        resultSet.getString("full_name"),
                        Role.STUDENT);
            }
        }
    }

    /** Thêm một tài khoản, mật khẩu được băm trước khi lưu. */
    public void insertUser(Role role, String username, String password, String fullName) throws SQLException {
        String sql;
        if (role == Role.TEACHER) {
            sql = "INSERT INTO teachers (username, password_hash, full_name) VALUES (?, ?, ?)";
        } else {
            sql = "INSERT INTO students (username, password_hash, full_name) VALUES (?, ?, ?)";
        }

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHasher.hash(password));
            statement.setString(3, fullName);
            statement.executeUpdate();
        }
    }

    /** Đếm số giáo viên hoặc số sinh viên. */
    public int countByRole(Role role) throws SQLException {
        String sql;
        if (role == Role.TEACHER) {
            sql = "SELECT COUNT(*) FROM teachers";
        } else {
            sql = "SELECT COUNT(*) FROM students";
        }

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
