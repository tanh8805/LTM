// Owner: Nguoi2

package exam.server.db;

import com.google.gson.Gson;
import exam.common.model.ExamShift;
import exam.common.model.MonitoringRules;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng exam_shifts và shift_students (ca thi và thí sinh). */
public class ShiftDao {

    private static final String SELECT_SHIFT = """
        SELECT s.id, s.code, s.exam_id, e.title, s.start_time, s.duration_seconds, s.status, s.rules_json
        FROM exam_shifts s
        JOIN exams e ON e.id = s.exam_id
        """;

    private final Database database;
    private final Gson gson = new Gson();

    public ShiftDao(Database database) {
        this.database = database;
    }

    /** Tạo ca thi và danh sách thí sinh trong MỘT transaction. Trả về id ca thi. */
    public int insertShift(String code, int examId, long startTime, int durationSeconds,
                           MonitoringRules rules, List<CandidateRecord> candidates) throws SQLException {
        String insertShiftSql = """
            INSERT INTO exam_shifts (code, exam_id, start_time, duration_seconds, status, rules_json)
            VALUES (?, ?, ?, ?, 'CREATED', ?)
            """;
        String insertCandidateSql = "INSERT INTO shift_students (shift_id, student_id, shuffle_seed) VALUES (?, ?, ?)";

        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                int shiftId;
                try (PreparedStatement statement = connection.prepareStatement(insertShiftSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, code);
                    statement.setInt(2, examId);
                    statement.setLong(3, startTime);
                    statement.setInt(4, durationSeconds);
                    statement.setString(5, gson.toJson(rules));
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        shiftId = keys.getInt(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(insertCandidateSql)) {
                    for (CandidateRecord candidate : candidates) {
                        statement.setInt(1, shiftId);
                        statement.setInt(2, candidate.studentId);
                        statement.setLong(3, candidate.shuffleSeed);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return shiftId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Trả về null nếu không có ca thi với mã này. */
    public ExamShift findByCode(String code) throws SQLException {
        String sql = SELECT_SHIFT + " WHERE s.code = ?";

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readShift(resultSet);
                }
                return null;
            }
        }
    }

    public List<ExamShift> findAll() throws SQLException {
        return findShifts(SELECT_SHIFT + " ORDER BY s.id", null);
    }

    /** Các ca đang diễn ra. */
    public List<ExamShift> findRunning() throws SQLException {
        return findShifts(SELECT_SHIFT + " WHERE s.status = ? ORDER BY s.id", ExamShift.STATUS_RUNNING);
    }

    /** Các ca đã đặt giờ hẹn (start_time > 0), chưa bắt đầu, và giờ hẹn đã tới. */
    public List<ExamShift> findDueToStart(long nowMillis) throws SQLException {
        List<ExamShift> due = new ArrayList<>();
        for (ExamShift shift : findShifts(SELECT_SHIFT + " WHERE s.status = ? ORDER BY s.id", ExamShift.STATUS_CREATED)) {
            if (shift.startTimeServer > 0 && shift.startTimeServer <= nowMillis) {
                due.add(shift);
            }
        }
        return due;
    }

    private List<ExamShift> findShifts(String sql, String statusParameter) throws SQLException {
        List<ExamShift> shifts = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (statusParameter != null) {
                statement.setString(1, statusParameter);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    shifts.add(readShift(resultSet));
                }
            }
        }
        return shifts;
    }

    /** Đổi trạng thái ca thi và giờ bắt đầu thực tế. */
    public void updateStatus(int shiftId, String status, long startTime) throws SQLException {
        String sql = "UPDATE exam_shifts SET status = ?, start_time = ? WHERE id = ?";

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, startTime);
            statement.setInt(3, shiftId);
            statement.executeUpdate();
        }
    }

    public List<CandidateRecord> findCandidates(int shiftId) throws SQLException {
        String sql = """
            SELECT st.id, st.username, st.full_name, ss.shuffle_seed
            FROM shift_students ss
            JOIN students st ON st.id = ss.student_id
            WHERE ss.shift_id = ?
            ORDER BY st.username
            """;

        List<CandidateRecord> candidates = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shiftId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    candidates.add(readCandidate(resultSet));
                }
            }
        }
        return candidates;
    }

    /** Trả về null nếu sinh viên không nằm trong danh sách thí sinh của ca. */
    public CandidateRecord findCandidate(int shiftId, int studentId) throws SQLException {
        String sql = """
            SELECT st.id, st.username, st.full_name, ss.shuffle_seed
            FROM shift_students ss
            JOIN students st ON st.id = ss.student_id
            WHERE ss.shift_id = ? AND ss.student_id = ?
            """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shiftId);
            statement.setInt(2, studentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readCandidate(resultSet);
                }
                return null;
            }
        }
    }

    private CandidateRecord readCandidate(ResultSet resultSet) throws SQLException {
        CandidateRecord candidate = new CandidateRecord();
        candidate.studentId = resultSet.getInt("id");
        candidate.username = resultSet.getString("username");
        candidate.fullName = resultSet.getString("full_name");
        candidate.shuffleSeed = resultSet.getLong("shuffle_seed");
        return candidate;
    }

    private ExamShift readShift(ResultSet resultSet) throws SQLException {
        ExamShift shift = new ExamShift();
        shift.id = resultSet.getInt("id");
        shift.code = resultSet.getString("code");
        shift.examId = resultSet.getInt("exam_id");
        shift.examTitle = resultSet.getString("title");
        shift.startTimeServer = resultSet.getLong("start_time");
        shift.durationSeconds = resultSet.getInt("duration_seconds");
        shift.status = resultSet.getString("status");

        MonitoringRules rules = gson.fromJson(resultSet.getString("rules_json"), MonitoringRules.class);
        shift.rules = rules != null ? rules : MonitoringRules.createDefault();
        return shift;
    }
}
