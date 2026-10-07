// Owner: Nguoi1

package exam.client;

import exam.client.api.ConnectionStatus;
import exam.client.api.ServerLink;
import exam.client.api.ServerLinkListener;
import exam.client.net.TcpServerLink;
import exam.client.teacher.TeacherFrame;
import exam.common.model.Role;
import exam.common.protocol.AlertMessage;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.LoginFailMessage;
import exam.common.protocol.LoginMessage;
import exam.common.protocol.LoginOkMessage;
import exam.common.protocol.Message;
import java.io.IOException;
import javax.swing.SwingUtilities;

/**
 * Ứng dụng của giáo viên.
 *
 *   java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.TeacherMain [host] [port] [username] [matKhau]
 *
 * Mặc định: localhost 5000 gv01 teacher123
 *
 * Luồng: mở cửa sổ -> kết nối Server -> gửi LOGIN -> nhận LOGIN_OK -> hiện trạng thái.
 */
public class TeacherMain implements ServerLinkListener {

    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private final ServerLink serverLink = new TcpServerLink();
    private TeacherFrame frame;

    public TeacherMain(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        String username = args.length > 2 ? args[2] : "gv01";
        String password = args.length > 3 ? args[3] : "teacher123";

        TeacherMain app = new TeacherMain(host, port, username, password);
        // Swing yêu cầu tạo cửa sổ trên thread giao diện (Event Dispatch Thread).
        SwingUtilities.invokeLater(app::start);
    }

    private void start() {
        frame = new TeacherFrame(username);
        frame.setVisible(true);

        serverLink.addListener(this);

        // Kết nối có thể chậm nên chạy trên thread khác, không làm đơ giao diện.
        Thread.startVirtualThread(this::connectAndLogin);
    }

    private void connectAndLogin() {
        try {
            serverLink.connect(host, port);
        } catch (IOException e) {
            System.out.println("[Teacher] Không kết nối được tới " + host + ":" + port + ": " + e.getMessage());
            frame.showStatus("Cannot connect (" + e.getMessage() + ")");
            return;
        }

        // Giáo viên không có mã ca thi nên examCode = null.
        serverLink.send(new LoginMessage(username, password, Role.TEACHER, null));
    }

    // ------------------------------------------------------------------
    // Nhận từ Server (chạy trên thread đọc socket)
    // ------------------------------------------------------------------

    @Override
    public void onMessage(Message message) {
        if (message instanceof LoginOkMessage loginOk) {
            System.out.println("[Teacher] LOGIN_OK, token=" + loginOk.token);
            frame.showLogin("OK");
        } else if (message instanceof LoginFailMessage loginFail) {
            System.out.println("[Teacher] LOGIN_FAIL: " + loginFail.reason);
            frame.showLogin("FAIL (" + loginFail.reason + ")");
        } else if (message instanceof AlertMessage alert) {
            frame.getMonitorPanel().showAlert(alert);
        } else if (message instanceof ErrorMessage error) {
            System.out.println("[Teacher] ERROR " + error.code + ": " + error.message);
        } else {
            // TODO(Nguoi2): RESPONSE (kết quả của REQUEST) chuyển cho các tab quản lý câu hỏi, đề, ca thi, điểm.
            System.out.println("[Teacher] Nhận " + message.type + " (chưa xử lý)");
        }
    }

    @Override
    public void onStatusChanged(ConnectionStatus newStatus) {
        frame.showStatus(newStatus == ConnectionStatus.CONNECTED ? "Connected" : newStatus.toString());
    }
}
