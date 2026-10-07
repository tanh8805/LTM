// Owner: Nguoi2

package exam.server.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Điểm vào duy nhất để mở SQLite và khởi tạo dữ liệu.
 *
 * Cách làm việc với database trong project (các DAO đều theo đúng thứ tự này):
 *   mở connection -> prepare statement -> set tham số -> execute -> đọc kết quả -> đóng (try-with-resources)
 *
 * SQLite là file nên mở connection rất rẻ. Mỗi lần gọi DAO mở một connection riêng,
 * nhờ vậy nhiều virtual thread dùng database cùng lúc mà không phải chia sẻ connection.
 */
public class Database {

    private final String filePath;

    /** filePath ví dụ "data/exam.db". */
    public Database(String filePath) {
        this.filePath = filePath;
    }

    /** Mở một connection mới. Người gọi PHẢI đóng (dùng try-with-resources). */
    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + filePath);
        try (Statement statement = connection.createStatement()) {
            // SQLite mặc định KHÔNG kiểm tra khóa ngoại, phải bật cho từng connection.
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    /** Tạo thư mục chứa file db (nếu chưa có) và chạy schema.sql. */
    public void createTables() throws SQLException, IOException {
        Path parentDirectory = Path.of(filePath).toAbsolutePath().getParent();
        Files.createDirectories(parentDirectory);

        runSqlResource("schema.sql");
    }

    /** Nạp dữ liệu mẫu nếu database còn trống (chưa có giáo viên nào). Chạy lại nhiều lần vẫn an toàn. */
    public void loadSampleDataIfEmpty() throws SQLException, IOException {
        if (countRows("teachers") > 0) {
            return;
        }
        runSqlResource("sample_data.sql");
    }

    /** Đếm số dòng của bảng. Tên bảng do code truyền vào (không bao giờ lấy từ người dùng). */
    private int countRows(String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    /**
     * Đọc file .sql trong resources, tách theo dấu chấm phẩy rồi chạy từng câu lệnh.
     * Dòng comment bắt đầu bằng "--" bị bỏ qua.
     */
    private void runSqlResource(String resourceName) throws SQLException, IOException {
        String sqlText = readResourceText(resourceName);

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            for (String sqlStatement : sqlText.split(";")) {
                if (sqlStatement.isBlank()) {
                    continue;
                }
                statement.execute(sqlStatement);
            }
        }
    }

    private String readResourceText(String resourceName) throws IOException {
        try (InputStream inputStream = Database.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Không tìm thấy resource: " + resourceName);
            }
            String rawText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return removeCommentLines(rawText);
        }
    }

    private String removeCommentLines(String sqlText) {
        StringBuilder result = new StringBuilder();
        for (String line : sqlText.split("\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            result.append(line).append("\n");
        }
        return result.toString();
    }
}
