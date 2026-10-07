// Owner: Nguoi1

package exam.server.net;

import exam.server.api.ExamService;
import exam.server.api.MessageSender;
import exam.server.api.MonitorService;
import exam.server.api.SessionRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Mở cổng TCP, chờ client kết nối.
 *
 * Luồng chạy:
 *   accept() -> có client mới -> tạo ClientConnection -> ClientHandler.run() trên một virtual thread riêng.
 *
 * Mỗi client chạy trên MỘT virtual thread. Virtual thread rất nhẹ nên Server có thể giữ hàng nghìn
 * connection đang chờ đọc (blocking readLine) mà không cần tự quản lý thread pool.
 * Nhờ đó code của ClientHandler viết kiểu đơn giản: đọc một dòng, xử lý, đọc dòng tiếp.
 */
public class TcpServer {

    private final int port;
    private final SessionRegistry sessionRegistry;
    private final MessageSender messageSender;
    private final ExamService examService;
    private final MonitorService monitorService;

    public TcpServer(int port, SessionRegistry sessionRegistry, MessageSender messageSender,
                     ExamService examService, MonitorService monitorService) {
        this.port = port;
        this.sessionRegistry = sessionRegistry;
        this.messageSender = messageSender;
        this.examService = examService;
        this.monitorService = monitorService;
    }

    /** Chạy vòng lặp accept. Chặn thread gọi cho đến khi Server tắt. */
    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[Server] Đang lắng nghe TCP cổng " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                // Mỗi client chạy trên một virtual thread.
                // Nếu client A bị treo hoặc chậm, client B vẫn được phục vụ bình thường.
                Thread.startVirtualThread(() -> serveOneClient(socket));
            }
        }
    }

    private void serveOneClient(Socket socket) {
        try {
            ClientConnection connection = new ClientConnection(socket);
            ClientHandler handler = new ClientHandler(
                    connection, sessionRegistry, messageSender, examService, monitorService);
            handler.run();
        } catch (IOException e) {
            // TODO(Nguoi1): Add proper error handling.
            e.printStackTrace();
        }
    }
}
