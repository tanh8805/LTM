// Owner: Nguoi1

package exam.client;

import exam.client.api.AnomalyScorer;
import exam.client.api.ConnectionStatus;
import exam.client.api.IsolationForestScorer;
import exam.client.api.MetricsSource;
import exam.client.api.RuleEngine;
import exam.client.api.ServerLink;
import exam.client.api.ServerLinkListener;
import exam.client.monitor.MonitoringLoop;
import exam.client.monitor.OshiMetricsSource;
import exam.client.monitor.RuleEngineImpl;
import exam.client.net.TcpServerLink;
import exam.client.student.StudentFrame;
import exam.common.model.Metrics;
import exam.common.model.Role;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.HeartbeatAckMessage;
import exam.common.protocol.HeartbeatMessage;
import exam.common.protocol.LoginFailMessage;
import exam.common.protocol.LoginMessage;
import exam.common.protocol.LoginOkMessage;
import exam.common.protocol.Message;
import java.io.IOException;
import javax.swing.SwingUtilities;

/**
 * Ứng dụng của sinh viên.
 *
 *   java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.StudentMain [host] [port] [maSV] [matKhau] [maCaThi]
 *
 * Mặc định: localhost 5000 SV001 123456 CA001
 *
 * Luồng: mở cửa sổ -> kết nối Server -> gửi LOGIN -> nhận LOGIN_OK -> gửi HEARTBEAT mỗi 10 giây.
 */
public class StudentMain implements ServerLinkListener {

    private static final int HEARTBEAT_INTERVAL_MS = 10_000;

    private final String host;
    private final int port;
    private final String studentCode;
    private final String password;
    private final String examCode;

    private final ServerLink serverLink = new TcpServerLink();
    private final MetricsSource metricsSource = new OshiMetricsSource();
    private final RuleEngine ruleEngine = new RuleEngineImpl();
    private final AnomalyScorer anomalyScorer = new IsolationForestScorer();
    private final MonitoringLoop monitoringLoop =
            new MonitoringLoop(serverLink, metricsSource, ruleEngine, anomalyScorer);

    private StudentFrame frame;
    private int heartbeatSeq = 0;

    public StudentMain(String host, int port, String studentCode, String password, String examCode) {
        this.host = host;
        this.port = port;
        this.studentCode = studentCode;
        this.password = password;
        this.examCode = examCode;
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        String studentCode = args.length > 2 ? args[2] : "SV001";
        String password = args.length > 3 ? args[3] : "123456";
        String examCode = args.length > 4 ? args[4] : "CA001";

        StudentMain app = new StudentMain(host, port, studentCode, password, examCode);
        // Swing yêu cầu tạo cửa sổ trên thread giao diện (Event Dispatch Thread).
        SwingUtilities.invokeLater(app::start);
    }

    private void start() {
        frame = new StudentFrame(studentCode, ruleEngine);
        frame.setVisible(true);

        serverLink.addListener(this);

        // Kết nối có thể chậm nên chạy trên thread khác, không làm đơ giao diện.
        Thread.startVirtualThread(this::connectAndLogin);
    }

    private void connectAndLogin() {
        try {
            serverLink.connect(host, port);
        } catch (IOException e) {
            System.out.println("[Student] Không kết nối được tới " + host + ":" + port + ": " + e.getMessage());
            frame.showStatus("Cannot connect (" + e.getMessage() + ")");
            return;
        }

        serverLink.send(new LoginMessage(studentCode, password, Role.STUDENT, examCode));
    }

    // ------------------------------------------------------------------
    // Nhận từ Server (chạy trên thread đọc socket)
    // ------------------------------------------------------------------

    @Override
    public void onMessage(Message message) {
        if (message instanceof LoginOkMessage loginOk) {
            System.out.println("[Student] LOGIN_OK, token=" + loginOk.token);
            frame.showLogin("OK");
            monitoringLoop.start();
            Thread.startVirtualThread(this::runHeartbeatLoop);
        } else if (message instanceof LoginFailMessage loginFail) {
            System.out.println("[Student] LOGIN_FAIL: " + loginFail.reason);
            frame.showLogin("FAIL (" + loginFail.reason + ")");
        } else if (message instanceof HeartbeatAckMessage ack) {
            frame.showHeartbeat(ack.seq);
        } else if (message instanceof ErrorMessage error) {
            System.out.println("[Student] ERROR " + error.code + ": " + error.message);
            frame.appendLog("ERROR " + error.code + ": " + error.message);
        } else {
            // TODO(Nguoi2): EXAM_START, TIME_SYNC, ANSWER_ACK, SUBMIT_OK, EXAM_END, NOTICE, ... chuyển cho StudentFrame.
            // TODO(Nguoi3): RULES_CONFIG, SET_RATE (monitoringLoop.setRate), ROOM_STATS.
            frame.appendLog("Nhận " + message.type + " (chưa xử lý)");
        }
    }

    @Override
    public void onStatusChanged(ConnectionStatus newStatus) {
        frame.showStatus(newStatus == ConnectionStatus.CONNECTED ? "Connected" : newStatus.toString());
    }

    // ------------------------------------------------------------------
    // Gửi HEARTBEAT mỗi 10 giây
    // ------------------------------------------------------------------

    private void runHeartbeatLoop() {
        while (serverLink.getStatus() == ConnectionStatus.CONNECTED) {
            heartbeatSeq++;

            Metrics summary = metricsSource.collectMetrics();
            summary.focusLostCount = ruleEngine.getFocusLostCount();
            serverLink.send(new HeartbeatMessage(heartbeatSeq, summary));

            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
            } catch (InterruptedException e) {
                // Có ai đó yêu cầu dừng: giữ lại cờ interrupt rồi thoát.
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
