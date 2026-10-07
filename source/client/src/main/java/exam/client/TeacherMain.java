// Owner: Nguoi1

package exam.client;

import exam.client.net.TcpServerLink;
import exam.client.teacher.TeacherFrame;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * Ứng dụng của giáo viên.
 *
 *   java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.TeacherMain [host] [port] [username] [matKhau] [auto]
 *
 * Giá trị mặc định: host, port từ config/client.properties; tài khoản mẫu gv01 / teacher123.
 * Thêm tham số cuối "auto" để tự bấm Login (dùng khi chạy thử).
 *
 * Luồng: mở cửa sổ đăng nhập -> Login -> LOGIN_OK -> các tab quản lý. Logic mạng nằm trong TeacherClient.
 */
public class TeacherMain {

    public static void main(String[] args) throws IOException {
        ClientConfig config = ClientConfig.load(Path.of("config/client.properties"));

        String host = args.length > 0 ? args[0] : config.serverHost;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : config.serverPort;
        String username = args.length > 2 ? args[2] : "gv01";
        String password = args.length > 3 ? args[3] : "teacher123";
        boolean autoLogin = args.length > 4 && args[4].equalsIgnoreCase("auto");

        TcpServerLink serverLink = new TcpServerLink(config.reconnectDelayMs, config.reconnectMaxAttempts);
        TeacherClient client = new TeacherClient(serverLink, host, port);

        // Swing yêu cầu tạo cửa sổ trên thread giao diện (Event Dispatch Thread).
        SwingUtilities.invokeLater(() -> {
            TeacherFrame frame = new TeacherFrame(client, username, password);
            client.setView(frame);
            frame.setVisible(true);
            if (autoLogin) {
                frame.clickLoginForAutoStart();
            }
        });
    }
}
