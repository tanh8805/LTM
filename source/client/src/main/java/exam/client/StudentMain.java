// Owner: Nguoi1

package exam.client;

import exam.client.api.AnomalyScorer;
import exam.client.api.MetricsSource;
import exam.client.api.RuleEngine;
import exam.client.ml.IsolationForestScorer;
import exam.client.monitor.MonitoringLoop;
import exam.client.monitor.OshiMetricsSource;
import exam.client.monitor.RuleEngineImpl;
import exam.client.net.TcpServerLink;
import exam.client.student.StudentFrame;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * Ứng dụng của sinh viên.
 *
 *   java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.StudentMain [host] [port] [maSV] [matKhau] [maCaThi] [auto]
 *
 * Giá trị mặc định lấy từ config/client.properties (host, port) và form đăng nhập (SV001 / 123456 / CA001).
 * Thêm tham số cuối "auto" để tự bấm Login (dùng khi chạy thử).
 *
 * Luồng: mở cửa sổ đăng nhập -> Login -> nhận LOGIN_OK -> chờ EXAM_START -> làm bài -> SUBMIT / EXAM_END.
 * Toàn bộ logic mạng nằm trong StudentClient; lớp này chỉ ráp các phần lại.
 */
public class StudentMain {

    public static void main(String[] args) throws IOException {
        ClientConfig config = ClientConfig.load(Path.of("config/client.properties"));

        String host = args.length > 0 ? args[0] : config.serverHost;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : config.serverPort;
        String studentCode = args.length > 2 ? args[2] : "SV001";
        String password = args.length > 3 ? args[3] : "123456";
        String examCode = args.length > 4 ? args[4] : "CA001";
        boolean autoLogin = args.length > 5 && args[5].equalsIgnoreCase("auto");

        TcpServerLink serverLink = new TcpServerLink(config.reconnectDelayMs, config.reconnectMaxAttempts);
        MetricsSource metricsSource = new OshiMetricsSource();
        RuleEngine ruleEngine = new RuleEngineImpl(metricsSource);
        AnomalyScorer anomalyScorer = new IsolationForestScorer();
        MonitoringLoop monitoringLoop = new MonitoringLoop(serverLink, metricsSource, ruleEngine, anomalyScorer);
        StudentClient client = new StudentClient(serverLink, monitoringLoop, ruleEngine, host, port, config.heartbeatIntervalMs);

        // Swing yêu cầu tạo cửa sổ trên thread giao diện (Event Dispatch Thread).
        SwingUtilities.invokeLater(() -> {
            StudentFrame frame = new StudentFrame(client, studentCode, password, examCode);
            client.setView(frame);
            frame.setVisible(true);
            if (autoLogin) {
                frame.clickLoginForAutoStart();
            }
        });
    }
}
