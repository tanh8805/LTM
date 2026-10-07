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
 */
public class TcpServerLink implements ServerLink {

    private final MessageCodec codec = new MessageCodec();
    // CopyOnWriteArrayList: thêm listener và duyệt gọi listener từ thread khác nhau vẫn an toàn.
    private final List<ServerLinkListener> listeners = new CopyOnWriteArrayList<>();

    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    @Override
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        changeStatus(ConnectionStatus.CONNECTED);
        Thread.startVirtualThread(this::readUntilDisconnected);
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
    public void close() {
        changeStatus(ConnectionStatus.DISCONNECTED);
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            // TODO(Nguoi1): Add proper error handling.
            e.printStackTrace();
        }
    }

    /** Chạy trên một virtual thread riêng: đọc cho tới khi Server đóng kết nối hoặc lỗi mạng. */
    private void readUntilDisconnected() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line);
            }
            System.out.println("[Link] Server đã đóng kết nối");
        } catch (IOException e) {
            if (status != ConnectionStatus.DISCONNECTED) {
                System.out.println("[Link] Mất kết nối tới Server: " + e.getMessage());
            }
        } finally {
            // TODO(Nguoi1): Tự RECONNECT bằng token (trạng thái RECONNECTING) thay vì chỉ báo DISCONNECTED.
            changeStatus(ConnectionStatus.DISCONNECTED);
        }
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
