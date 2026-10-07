// Owner: Nguoi1

package exam.client.net;

import exam.client.api.ConnectionStatus;
import exam.client.api.ServerLink;
import exam.client.api.ServerLinkListener;
import exam.common.protocol.Message;
import exam.common.protocol.MessageCodec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Kết nối TCP tới Server, mỗi message một dòng JSON.
 *
 *   gửi : send(message) -> codec.encode() -> ghi một dòng
 *   nhận: một virtual thread đọc từng dòng -> codec.decode() -> gọi các listener
 *
 * Mất kết nối (Server tắt, mất mạng): trạng thái chuyển sang RECONNECTING và một virtual thread thử nối lại TCP
 * cho tới khi được (mỗi lần cách nhau reconnectDelayMs, tối đa maxAttempts lần). Khi nối lại được, trạng thái về CONNECTED
 * và listener tự gửi RECONNECT bằng token (việc đó thuộc tầng ứng dụng, không phải tầng này).
 */
public class TcpServerLink implements ServerLink {

    private final MessageCodec codec = new MessageCodec();
    // CopyOnWriteArrayList: thêm listener và duyệt gọi listener từ thread khác nhau vẫn an toàn.
    private final List<ServerLinkListener> listeners = new CopyOnWriteArrayList<>();
    private final int reconnectDelayMs;
    private final int maxReconnectAttempts;

    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private volatile boolean closedByUser = false;
    private String host;
    private int port;
    private Socket socket;
    private BufferedWriter writer;

    public TcpServerLink() {
        this(2000, 60);
    }

    public TcpServerLink(int reconnectDelayMs, int maxReconnectAttempts) {
        this.reconnectDelayMs = reconnectDelayMs;
        this.maxReconnectAttempts = maxReconnectAttempts;
    }

    @Override
    public void connect(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.closedByUser = false;
        openSocketAndStartReader();
        changeStatus(ConnectionStatus.CONNECTED);
    }

    /** Mở socket mới và bắt đầu một virtual thread đọc từ socket đó. */
    private synchronized void openSocketAndStartReader() throws IOException {
        Socket newSocket = new Socket(host, port);
        newSocket.setTcpNoDelay(true);
        BufferedReader reader = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8));
        socket = newSocket;

        Thread.startVirtualThread(() -> readUntilDisconnected(reader));
    }

    /** Gửi từ nhiều thread (heartbeat, ANSWER, VIOLATION...) nên synchronized. */
    @Override
    public synchronized boolean send(Message message) {
        if (status != ConnectionStatus.CONNECTED) {
            return false;
        }
        try {
            writer.write(codec.encode(message));
            writer.write('\n');
            writer.flush();
            return true;
        } catch (IOException e) {
            System.out.println("[Link] Gửi " + message.type + " thất bại: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void addListener(ServerLinkListener listener) {
        listeners.add(listener);
    }

    @Override
    public ConnectionStatus getStatus() {
        return status;
    }

    @Override
    public void resetConnection() {
        // Đóng socket nhưng KHÔNG đặt closedByUser: thread đọc sẽ báo lỗi và chuyển sang RECONNECTING.
        closeSocket();
    }

    @Override
    public void close() {
        closedByUser = true;
        changeStatus(ConnectionStatus.DISCONNECTED);
        closeSocket();
    }

    private synchronized void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("[Link] Lỗi khi đóng socket: " + e.getMessage());
        }
    }

    /** Chạy trên một virtual thread riêng: đọc cho tới khi Server đóng kết nối hoặc lỗi mạng. */
    private void readUntilDisconnected(BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line);
            }
            System.out.println("[Link] Server đã đóng kết nối");
        } catch (IOException e) {
            if (!closedByUser) {
                System.out.println("[Link] Mất kết nối tới Server: " + e.getMessage());
            }
        }
        handleConnectionLost();
    }

    private void handleConnectionLost() {
        closeSocket();
        if (closedByUser) {
            changeStatus(ConnectionStatus.DISCONNECTED);
            return;
        }
        changeStatus(ConnectionStatus.RECONNECTING);
        Thread.startVirtualThread(this::reconnectLoop);
    }

    /** Thử nối lại TCP cho tới khi được hoặc hết số lần thử. */
    private void reconnectLoop() {
        for (int attempt = 1; attempt <= maxReconnectAttempts; attempt++) {
            try {
                Thread.sleep(reconnectDelayMs);
            } catch (InterruptedException e) {
                // Có ai đó yêu cầu dừng: giữ lại cờ interrupt rồi thoát.
                Thread.currentThread().interrupt();
                return;
            }
            if (closedByUser) {
                return;
            }
            try {
                openSocketAndStartReader();
                System.out.println("[Link] Đã nối lại TCP (lần thử " + attempt + ")");
                changeStatus(ConnectionStatus.CONNECTED);
                return;
            } catch (IOException e) {
                System.out.println("[Link] Nối lại lần " + attempt + " thất bại: " + e.getMessage());
            }
        }
        System.out.println("[Link] Bỏ cuộc sau " + maxReconnectAttempts + " lần nối lại");
        changeStatus(ConnectionStatus.DISCONNECTED);
    }

    private void handleLine(String line) {
        Message message;
        try {
            message = codec.decode(line);
        } catch (IllegalArgumentException e) {
            System.out.println("[Link] Bỏ qua message sai định dạng: " + e.getMessage());
            return;
        }

        for (ServerLinkListener listener : listeners) {
            try {
                listener.onMessage(message);
            } catch (RuntimeException e) {
                // Một listener lỗi không được làm chết thread đọc socket.
                e.printStackTrace();
            }
        }
    }

    private synchronized void changeStatus(ConnectionStatus newStatus) {
        if (status == newStatus) {
            return;
        }
        status = newStatus;
        for (ServerLinkListener listener : listeners) {
            try {
                listener.onStatusChanged(newStatus);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }
}
