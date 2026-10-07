// Owner: Nguoi2

package exam.server.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng exams và exam_questions (đề thi). */
public class ExamDao {

    /** Thông tin tóm tắt một đề thi. */
    public static class ExamRecord {
        public int id;
        public String title;
        public int durationMinutes;
        public int questionCount;
    }

    private final Database database;

    public ExamDao(Database database) {
        this.database = database;
    }

    /** Tạo đề và các câu của đề trong MỘT transaction (hoặc lưu hết, hoặc không lưu gì). Trả về id đề. */
    public int insertExam(String title, int durationMinutes, int teacherId, List<Integer> questionIds) throws SQLException {
        String insertExamSql = "INSERT INTO exams (title, duration_minutes, created_by_teacher) VALUES (?, ?, ?)";
        String insertLinkSql = "INSERT INTO exam_questions (exam_id, question_id) VALUES (?, ?)";

        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                int examId;
                try (PreparedStatement statement = connection.prepareStatement(insertExamSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, title);
                    statement.setInt(2, durationMinutes);
                    statement.setInt(3, teacherId);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        examId = keys.getInt(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(insertLinkSql)) {
                    for (int questionId : questionIds) {
                        statement.setInt(1, examId);
                        statement.setInt(2, questionId);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return examId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public List<ExamRecord> findAll() throws SQLException {
        String sql = """
            SELECT e.id, e.title, e.duration_minutes, COUNT(q.question_id) AS question_count
            FROM exams e
            LEFT JOIN exam_questions q ON q.exam_id = e.id
            GROUP BY e.id, e.title, e.duration_minutes
            ORDER BY e.id
            """;

        List<ExamRecord> exams = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ExamRecord exam = new ExamRecord();
                exam.id = resultSet.getInt("id");
                exam.title = resultSet.getString("title");
                exam.durationMinutes = resultSet.getInt("duration_minutes");
                exam.questionCount = resultSet.getInt("question_count");
                exams.add(exam);
            }
        }
        return exams;
    }

    /** Trả về null nếu không có đề này. */
    public ExamRecord findById(int examId) throws SQLException {
        for (ExamRecord exam : findAll()) {
            if (exam.id == examId) {
                return exam;
            }
        }
        return null;
    }

    /** Id các câu của đề, sắp theo id tăng dần (thứ tự ổn định để trộn lặp lại được). */
    public List<Integer> findQuestionIds(int examId) throws SQLException {
        String sql = "SELECT question_id FROM exam_questions WHERE exam_id = ? ORDER BY question_id";

        List<Integer> ids = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, examId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getInt(1));
                }
            }
        }
        return ids;
    }
}
