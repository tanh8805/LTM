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

    public UserDao(Database database) {
        this.database = database;
    }

    /** Trả về người dùng nếu đúng username + password, ngược lại trả về null. */
    public UserAccount findByUsernameAndPassword(Role role, String username, String password) throws SQLException {
        // Giáo viên và sinh viên nằm ở hai bảng riêng nên chọn đúng câu SQL theo vai trò.
        String sql;
        if (role == Role.TEACHER) {
            sql = """
                SELECT id, username, full_name
                FROM teachers
                WHERE username = ? AND password = ?
                """;
        } else {
            sql = """
                SELECT id, username, full_name
                FROM students
                WHERE username = ? AND password = ?
                """;
        }

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, password);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
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

    // TODO(Nguoi2): Thêm findStudentsOfShift(shiftId) và importStudentsFromCsv(...) cho danh sách thí sinh CSV.
}
