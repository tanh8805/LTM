// Owner: Nguoi2

package exam.server.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Truy vấn bảng answers (đáp án đã chọn) và results (kết quả sau khi nộp). */
public class AttemptDao {

    /** Một dòng bảng điểm: mỗi thí sinh của ca một dòng, kể cả người chưa nộp. */
    public static class ResultRow {
        public int studentId;
        public String studentCode;
        public String fullName;
        /** false nếu chưa có kết quả (chưa nộp và Server chưa chốt). */
        public boolean submitted;
        public boolean autoSubmitted;
        public double score;
        public int correctCount;
        public int totalQuestions;
        public int answeredCount;
        public long submittedAt;
    }

    private final Database database;

    public AttemptDao(Database database) {
        this.database = database;
    }

    /** Lưu đáp án. Chọn lại cùng câu thì ghi đè đáp án cũ. */
    public void saveAnswer(int shiftId, int studentId, int questionId, int choice, long answeredAt) throws SQLException {
        String sql = """
            INSERT INTO answers (shift_id, student_id, question_id, choice, answered_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (shift_id, student_id, question_id)
            DO UPDATE SET choice = excluded.choice, answered_at = excluded.answered_at
            """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shiftId);
            statement.setInt(2, studentId);
            statement.setInt(3, questionId);
            statement.setInt(4, choice);
            statement.setLong(5, answeredAt);
            statement.executeUpdate();
        }
    }

    /** Các đáp án đã lưu: questionId -> choice (vị trí trong thứ tự đã trộn). */
    public Map<Integer, Integer> findAnswers(int shiftId, int studentId) throws SQLException {
        String sql = "SELECT question_id, choice FROM answers WHERE shift_id = ? AND student_id = ?";

        Map<Integer, Integer> answers = new HashMap<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shiftId);
            statement.setInt(2, studentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    answers.put(resultSet.getInt("question_id"), resultSet.getInt("choice"));
                }
            }
        }
        return answers;
    }

    /**
     * Lưu kết quả (nộp bài). Mỗi sinh viên chỉ có một kết quả cho mỗi ca.
     * Trả về false nếu sinh viên đã có kết quả từ trước (không ghi đè).
     */
    public boolean insertResult(int shiftId, int studentId, double score, int correctCount, int totalQuestions,
                                int answeredCount, boolean autoSubmitted, long submittedAt) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO results
                (shift_id, student_id, score, correct_count, total_questions, answered_count, auto_submitted, submitted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shiftId);
            statement.setInt(2, studentId);
            statement.setDouble(3, score);
            statement.setInt(4, correctCount);
            statement.setInt(5, totalQuestions);
            statement.setInt(6, answeredCount);
            statement.setInt(7, autoSubmitted ? 1 : 0);
            statement.setLong(8, submittedAt);
            return statement.executeUpdate() > 0;
        }
    }

    /** Trả về null nếu sinh viên chưa nộp. */
    public ResultRow findResult(int shiftId, int studentId) throws SQLException {
        for (ResultRow row : findResultRows(shiftId)) {
            if (row.studentId == studentId) {
                return row.submitted ? row : null;
            }
        }
        return null;
    }

    /** Bảng điểm của ca: mọi thí sinh, sắp theo mã sinh viên. */
    public List<ResultRow> findResultRows(int shiftId) throws SQLException {
        String sql = """
            SELECT st.id, st.username, st.full_name,
                   r.score, r.correct_count, r.total_questions, r.answered_count, r.auto_submitted, r.submitted_at
            FROM shift_students ss
            JOIN students st ON st.id = ss.student_id
            LEFT JOIN results r ON r.shift_id = ss.shift_id AND r.student_id = ss.student_id
            WHERE ss.shift_id = ?
            ORDER BY st.username
            """;

        List<ResultRow> rows = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shiftId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ResultRow row = new ResultRow();
                    row.studentId = resultSet.getInt("id");
                    row.studentCode = resultSet.getString("username");
                    row.fullName = resultSet.getString("full_name");
                    // LEFT JOIN: sinh viên chưa nộp thì submitted_at là NULL (getLong trả 0 và wasNull = true).
                    row.submittedAt = resultSet.getLong("submitted_at");
                    row.submitted = !resultSet.wasNull();
                    if (row.submitted) {
                        row.score = resultSet.getDouble("score");
                        row.correctCount = resultSet.getInt("correct_count");
                        row.totalQuestions = resultSet.getInt("total_questions");
                        row.answeredCount = resultSet.getInt("answered_count");
                        row.autoSubmitted = resultSet.getInt("auto_submitted") == 1;
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
