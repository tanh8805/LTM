// Owner: Nguoi1

package exam.server.net;

import exam.server.ServerConfig;
import exam.server.api.ExamService;
import exam.server.api.MessageSender;
import exam.server.api.MonitorService;
import exam.server.api.RateController;
import exam.server.api.SessionRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private final ServerConfig config;
    private final SessionRegistry sessionRegistry;
    private final MessageSender messageSender;
    private final ExamService examService;
    private final MonitorService monitorService;
    private final RateController rateController;

    private final Set<ClientConnection> openConnections = ConcurrentHashMap.newKeySet();
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TcpServer(ServerConfig config, SessionRegistry sessionRegistry, MessageSender messageSender,
                     ExamService examService, MonitorService monitorService, RateController rateController) {
        this.config = config;
        this.sessionRegistry = sessionRegistry;
        this.messageSender = messageSender;
        this.examService = examService;
        this.monitorService = monitorService;
        this.rateController = rateController;
    }

    /**
     * Mở cổng rồi chạy vòng lặp accept trên một virtual thread; trả về ngay khi đã sẵn sàng nhận client.
     * Cổng 0 nghĩa là hệ điều hành tự chọn cổng trống (dùng trong test), xem getPort().
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(config.port);
        System.out.println("[Server] Đang lắng nghe TCP cổng " + serverSocket.getLocalPort());
        acceptThread = Thread.startVirtualThread(this::acceptLoop);
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** Chặn thread gọi cho tới khi Server tắt. */
    public void waitUntilStopped() throws InterruptedException {
        acceptThread.join();
    }

    /** Tắt Server: đóng cổng và mọi connection đang mở. */
    public void stop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.out.println("[Server] Lỗi khi đóng cổng: " + e.getMessage());
        }
        for (ClientConnection connection : openConnections) {
            connection.close();
        }
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.out.println("[Server] Lỗi accept: " + e.getMessage());
                }
                return;
            }

            // Mỗi client chạy trên một virtual thread.
            // Nếu client A bị treo hoặc chậm, client B vẫn được phục vụ bình thường.
            Thread.startVirtualThread(() -> serveOneClient(socket));
        }
    }

    private void serveOneClient(Socket socket) {
        ClientConnection connection = null;
        try {
            socket.setTcpNoDelay(true);
            connection = new ClientConnection(socket, config.maxMessageBytes);
            openConnections.add(connection);

            ClientHandler handler = new ClientHandler(connection, config, sessionRegistry, messageSender,
                    examService, monitorService, rateController);
            handler.run();
        } catch (IOException e) {
            System.out.println("[Server] Không phục vụ được client: " + e.getMessage());
        } finally {
            if (connection != null) {
                openConnections.remove(connection);
                connection.close();
            }
        }
    }
}
