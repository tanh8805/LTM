// Owner: Nguoi2

package exam.server.db;

import exam.common.model.Question;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng questions (ngân hàng câu hỏi). */
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
                List<String> options = List.of(
                        resultSet.getString("option_a"),
                        resultSet.getString("option_b"),
                        resultSet.getString("option_c"),
                        resultSet.getString("option_d"));
                questions.add(new Question(
                        resultSet.getInt("id"),
                        resultSet.getString("content"),
                        options,
                        resultSet.getInt("correct_option")));
            }
        }
        return questions;
    }

    // TODO(Nguoi2): Thêm insert(Question), update(Question), delete(int id).
    // TODO(Nguoi2): Thêm importFromCsv(...) để nhập câu hỏi từ file CSV (định dạng ghi trong docs/PROTOCOL.md khi chốt).
    // TODO(Nguoi2): Thêm findByIds(List<Integer>) và findRandom(int count) cho việc tạo đề.
}
