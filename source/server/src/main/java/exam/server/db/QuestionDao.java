// Owner: Nguoi2

package exam.server.db;

import exam.common.model.Question;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng questions (ngân hàng câu hỏi): thêm, đọc, sửa, xóa. */
public class QuestionDao {

    private final Database database;

    public QuestionDao(Database database) {
        this.database = database;
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM questions";

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    public List<Question> findAll() throws SQLException {
        String sql = """
            SELECT id, content, option_a, option_b, option_c, option_d, correct_option
            FROM questions
            ORDER BY id
            """;

        List<Question> questions = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                questions.add(readQuestion(resultSet));
            }
        }
        return questions;
    }

    /** Trả về null nếu không có câu hỏi này. */
    public Question findById(int id) throws SQLException {
        String sql = """
            SELECT id, content, option_a, option_b, option_c, option_d, correct_option
            FROM questions
            WHERE id = ?
            """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readQuestion(resultSet);
                }
                return null;
            }
        }
    }

    /** Các câu hỏi theo danh sách id, đúng thứ tự id được đưa vào. Id không tồn tại thì bị bỏ qua. */
    public List<Question> findByIds(List<Integer> ids) throws SQLException {
        List<Question> questions = new ArrayList<>();
        for (int id : ids) {
            Question question = findById(id);
            if (question != null) {
                questions.add(question);
            }
        }
        return questions;
    }

    public List<Integer> findAllIds() throws SQLException {
        String sql = "SELECT id FROM questions ORDER BY id";

        List<Integer> ids = new ArrayList<>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ids.add(resultSet.getInt(1));
            }
        }
        return ids;
    }

    /** true nếu đã có câu hỏi cùng nội dung (không phân biệt hoa thường, bỏ khoảng trắng hai đầu). */
    public boolean existsByContent(String content) throws SQLException {
        String sql = "SELECT COUNT(*) FROM questions WHERE LOWER(TRIM(content)) = LOWER(TRIM(?))";

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, content);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    /** Thêm câu hỏi mới, trả về id vừa tạo. */
    public int insert(Question question) throws SQLException {
        String sql = """
            INSERT INTO questions (content, option_a, option_b, option_c, option_d, correct_option)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setQuestionParameters(statement, question);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Sửa câu hỏi theo id. Trả về false nếu không có câu hỏi đó. */
    public boolean update(Question question) throws SQLException {
        String sql = """
            UPDATE questions
            SET content = ?, option_a = ?, option_b = ?, option_c = ?, option_d = ?, correct_option = ?
            WHERE id = ?
            """;

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setQuestionParameters(statement, question);
            statement.setInt(7, question.id);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Xóa câu hỏi theo id. Trả về false nếu không có câu hỏi đó.
     * Ném SQLException nếu câu hỏi đang nằm trong một đề thi (khóa ngoại).
     */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM questions WHERE id = ?";

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    /** true nếu câu hỏi đang được dùng trong ít nhất một đề thi. */
    public boolean isUsedInExam(int id) throws SQLException {
        String sql = "SELECT COUNT(*) FROM exam_questions WHERE question_id = ?";

        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private void setQuestionParameters(PreparedStatement statement, Question question) throws SQLException {
        statement.setString(1, question.content);
        statement.setString(2, question.options.get(0));
        statement.setString(3, question.options.get(1));
        statement.setString(4, question.options.get(2));
        statement.setString(5, question.options.get(3));
        statement.setInt(6, question.correctIndex);
    }

    private Question readQuestion(ResultSet resultSet) throws SQLException {
        List<String> options = List.of(
                resultSet.getString("option_a"),
                resultSet.getString("option_b"),
                resultSet.getString("option_c"),
                resultSet.getString("option_d"));
        return new Question(
                resultSet.getInt("id"),
                resultSet.getString("content"),
                options,
                resultSet.getInt("correct_option"));
    }
}
