// Owner: Nguoi1

package exam.server;

import exam.common.model.MonitoringRules;
import exam.server.api.ExamService;
import exam.server.api.MessageSender;
import exam.server.api.MlGateway;
import exam.server.api.MonitorService;
import exam.server.api.RateController;
import exam.server.api.SessionRegistry;
import exam.server.db.Database;
import exam.server.db.QuestionDao;
import exam.server.db.UserDao;
import exam.common.model.Role;
import exam.server.exam.ExamServiceImpl;
import exam.server.ml.MlConfig;
import exam.server.ml.MlGatewayImpl;
import exam.server.monitor.MonitorServiceImpl;
import exam.server.net.ConnectionMessageSender;
import exam.server.net.PeriodicJobs;
import exam.server.net.TcpServer;
import exam.server.rate.RateControllerImpl;
import exam.server.session.InMemorySessionRegistry;

/**
 * Ráp toàn bộ Server: SQLite, các service, TCP, việc định kỳ.
 * ServerMain dùng lớp này để chạy thật; các test tích hợp dùng nó để khởi động Server ngay trong JVM.
 */
public class ServerApp {

    private final TcpServer tcpServer;
    private final PeriodicJobs periodicJobs;
    private final Database database;
    private final MonitorService monitorService;
    private final SessionRegistry sessionRegistry;

    private ServerApp(TcpServer tcpServer, PeriodicJobs periodicJobs, Database database,
                      MonitorService monitorService, SessionRegistry sessionRegistry) {
        this.tcpServer = tcpServer;
        this.periodicJobs = periodicJobs;
        this.database = database;
        this.monitorService = monitorService;
        this.sessionRegistry = sessionRegistry;
    }

    /** Khởi động Server. Khi trả về, cổng TCP đã mở và sẵn sàng nhận client. */
    public static ServerApp start(ServerConfig config, MlConfig mlConfig) throws Exception {
        // 1. SQLite: tạo bảng, nạp dữ liệu mẫu
        Database database = new Database(config.databasePath);
        database.createTables();
        database.loadSampleDataIfEmpty();

        UserDao userDao = new UserDao(database);
        QuestionDao questionDao = new QuestionDao(database);
        System.out.println("[Server] SQLite sẵn sàng (" + config.databasePath + "): "
                + userDao.countByRole(Role.TEACHER) + " giáo viên, "
                + userDao.countByRole(Role.STUDENT) + " sinh viên, "
                + questionDao.countAll() + " câu hỏi");
        System.out.println("[Server] ml.mode = " + mlConfig.mode);

        // 2. Luật giám sát mặc định, lấy ngưỡng từ config (không hard-code)
        MonitoringRules defaultRules = MonitoringRules.createDefault();
        defaultRules.focusLossThreshold = config.focusThreshold;
        defaultRules.metricsIntervalSeconds = config.monitorIntervalSeconds;
        defaultRules.ifTrainingSeconds = mlConfig.clientTrainingSeconds;
        defaultRules.ifThresholdMargin = mlConfig.ifThresholdMargin;
        defaultRules.ifConsecutiveRequired = mlConfig.ifConsecutive;

        // 3. Nối các thành phần. Thứ tự tạo đi từ thành phần ít phụ thuộc đến nhiều phụ thuộc.
        SessionRegistry sessionRegistry = new InMemorySessionRegistry();
        MessageSender messageSender = new ConnectionMessageSender(sessionRegistry);
        RateController rateController = new RateControllerImpl(config, messageSender);
        MlGateway mlGateway = new MlGatewayImpl(mlConfig);
        MonitorService monitorService = new MonitorServiceImpl(
                config, mlConfig, database, sessionRegistry, messageSender, rateController, mlGateway);
        ExamService examService = new ExamServiceImpl(
                database, messageSender, sessionRegistry, mlConfig.mode, defaultRules);

        // 4. Mở TCP và chạy việc định kỳ
        TcpServer tcpServer = new TcpServer(config, sessionRegistry, messageSender, examService, monitorService, rateController);
        tcpServer.start();
        PeriodicJobs periodicJobs = new PeriodicJobs(config, examService, monitorService, rateController);
        periodicJobs.start();

        return new ServerApp(tcpServer, periodicJobs, database, monitorService, sessionRegistry);
    }

    public int getPort() {
        return tcpServer.getPort();
    }

    public Database getDatabase() {
        return database;
    }

    public MonitorService getMonitorService() {
        return monitorService;
    }

    public SessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }

    /** Chặn cho tới khi Server tắt. */
    public void waitUntilStopped() throws InterruptedException {
        tcpServer.waitUntilStopped();
    }

    public void stop() {
        periodicJobs.stop();
        tcpServer.stop();
    }
}
