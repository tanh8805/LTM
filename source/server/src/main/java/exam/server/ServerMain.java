// Owner: Nguoi1

package exam.server;

import exam.common.model.Role;
import exam.server.api.ExamService;
import exam.server.api.MessageSender;
import exam.server.api.MlGateway;
import exam.server.api.MonitorService;
import exam.server.api.RateController;
import exam.server.api.SessionRegistry;
import exam.server.db.Database;
import exam.server.db.QuestionDao;
import exam.server.db.UserDao;
import exam.server.exam.ExamServiceImpl;
import exam.server.ml.MlConfig;
import exam.server.ml.MlGatewayImpl;
import exam.server.monitor.MonitorServiceImpl;
import exam.server.net.ConnectionMessageSender;
import exam.server.net.PeriodicJobs;
import exam.server.net.TcpServer;
import exam.server.rate.RateControllerImpl;
import exam.server.session.InMemorySessionRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Điểm khởi động của Server. Chạy từ thư mục gốc của repo (để đường dẫn config/ và data/ đúng):
 *
 *   java -cp "source/server/target/server.jar:source/server/target/lib/*" exam.server.ServerMain
 *
 * Việc làm: mở SQLite + nạp dữ liệu mẫu, nối các thành phần lại với nhau, rồi mở cổng TCP.
 */
public class ServerMain {

    private static final String SERVER_CONFIG_FILE = "config/server.properties";
    private static final String ML_CONFIG_FILE = "config/ml.properties";

    public static void main(String[] args) throws Exception {
        Properties serverConfig = loadServerConfig();
        int port = Integer.parseInt(serverConfig.getProperty("server.port", "5000").trim());
        String databasePath = serverConfig.getProperty("db.path", "data/exam.db").trim();

        // 1. SQLite: tạo bảng, nạp dữ liệu mẫu
        Database database = new Database(databasePath);
        database.createTables();
        database.loadSampleDataIfEmpty();

        UserDao userDao = new UserDao(database);
        QuestionDao questionDao = new QuestionDao(database);
        System.out.println("[Server] SQLite sẵn sàng (" + databasePath + "): "
                + userDao.countByRole(Role.TEACHER) + " giáo viên, "
                + userDao.countByRole(Role.STUDENT) + " sinh viên, "
                + questionDao.countAll() + " câu hỏi");

        // 2. Cấu hình ML
        MlConfig mlConfig = MlConfig.load(Path.of(ML_CONFIG_FILE));
        System.out.println("[Server] ml.mode = " + mlConfig.mode);

        // 3. Nối các thành phần. Thứ tự tạo đi từ thành phần ít phụ thuộc đến nhiều phụ thuộc.
        SessionRegistry sessionRegistry = new InMemorySessionRegistry();
        MessageSender messageSender = new ConnectionMessageSender(sessionRegistry);
        RateController rateController = new RateControllerImpl(messageSender, sessionRegistry);
        MlGateway mlGateway = new MlGatewayImpl(mlConfig);
        MonitorService monitorService = new MonitorServiceImpl(sessionRegistry, messageSender, rateController, mlGateway);
        ExamService examService = new ExamServiceImpl(userDao, questionDao, messageSender, sessionRegistry);

        // 4. Việc định kỳ chạy nền, rồi mở TCP (start() chặn cho tới khi Server tắt)
        new PeriodicJobs(examService, monitorService, rateController).start();
        new TcpServer(port, sessionRegistry, messageSender, examService, monitorService).start();
    }

    /** Đọc config/server.properties. Thiếu file thì dùng mặc định (cổng 5000, data/exam.db). */
    private static Properties loadServerConfig() throws IOException {
        Properties properties = new Properties();
        Path file = Path.of(SERVER_CONFIG_FILE);

        if (Files.exists(file)) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                properties.load(inputStream);
            }
        } else {
            System.out.println("[Server] Không thấy " + SERVER_CONFIG_FILE + ", dùng cấu hình mặc định");
        }
        return properties;
    }
}
