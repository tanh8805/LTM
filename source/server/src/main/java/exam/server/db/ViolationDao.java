// Owner: Nguoi3

package exam.server.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng violations (vi phạm giám sát). */
public class ViolationDao {

    /** Một vi phạm đã lưu. */
    public static class ViolationRecord {
        public String violationType;
        public String evidence;
        public long time;
    }

    private final Database database;

    public ViolationDao(Database database) {
        this.database = database;
    }

    /** shiftId = 0 nếu không xác định được ca thi. */
    public void insert(int shiftId, int studentId, String violationType, String evidence, long time) throws SQLException {
        String sql = """
            INSERT INTO violations (shift_id, student_id, violation_type, evidence, time)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shiftId);
            statement.setInt(2, studentId);
            statement.setString(3, violationType);
            statement.setString(4, evidence);
            statement.setLong(5, time);
            statement.executeUpdate();
        }
    }

    /** Các vi phạm gần nhất của một sinh viên, mới nhất trước. */
    public List<ViolationRecord> findRecent(int studentId, int limit) throws SQLException {
        String sql = """
            SELECT violation_type, evidence, time
            FROM violations
            WHERE student_id = ?
            ORDER BY id DESC
            LIMIT ?
            """;

        List<ViolationRecord> records = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ViolationRecord record = new ViolationRecord();
                    record.violationType = resultSet.getString("violation_type");
                    record.evidence = resultSet.getString("evidence");
                    record.time = resultSet.getLong("time");
                    records.add(record);
                }
            }
        }
        return records;
    }

    public int countByStudent(int studentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM violations WHERE student_id = ?";

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
