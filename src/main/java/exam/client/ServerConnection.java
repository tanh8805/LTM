// Kết nối TCP tới server: luồng đọc nhận từng dòng JSON và tự kết nối lại mỗi 5 giây khi rớt mạng (Người 1).
package exam.client;

import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

public class ServerConnection {
    private static final int CONNECT_TIMEOUT_MS = 5000, RETRY_MS = 5000;

    private final InetSocketAddress address;
    private final Consumer<JsonObject> onMessage;
    private final Runnable onDisconnected, onReconnected;
    private volatile Socket socket;
    private volatile PrintWriter out;          // null = đang mất kết nối
    private volatile boolean closed;

    public ServerConnection(InetSocketAddress address, Consumer<JsonObject> onMessage,
                            Runnable onDisconnected, Runnable onReconnected) {
        this.address = address;
        this.onMessage = onMessage;
        this.onDisconnected = onDisconnected;
        this.onReconnected = onReconnected;
    }

    /** Kết nối lần đầu (ném IOException nếu thất bại) và bật luồng đọc. */
    public void connect() throws IOException {
        Socket s = new Socket();
        try {
            s.connect(address, CONNECT_TIMEOUT_MS);
        } catch (IOException e) {
            s.close();
            throw e;
        }
        socket = s;
        out = Protocol.writer(s);
        Thread reader = new Thread(() -> readLoop(s), "server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Tin gửi khi đang mất kết nối thì bỏ. */
    public void send(JsonObject m) {
        PrintWriter o = out;
        if (o != null) {
            Protocol.send(o, m);
        }
    }

    public void close() {
        closed = true;
        try {
            Socket s = socket;
            if (s != null) {
                s.close();
            }
        } catch (IOException e) {
            // đã đóng rồi
        }
    }

    private void readLoop(Socket s) {
        try (BufferedReader in = Protocol.reader(s)) {
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    onMessage.accept(Protocol.parse(line));
                } catch (IllegalArgumentException e) {
                    System.err.println("Bỏ qua dòng hỏng từ server: " + line);
                }
            }
        } catch (IOException e) {
            // socket đóng hoặc rớt mạng: xử lý ngay bên dưới
        }
        if (!closed) {
            out = null;
            onDisconnected.run();
            reconnect();
        }
    }

    /** Chạy trên chính luồng đọc cũ, thử lại cho tới khi vào được hoặc bị close(). */
    private void reconnect() {
        while (!closed) {
            try {
                Thread.sleep(RETRY_MS);
                if (closed) {
                    return;
                }
                connect();
                if (closed) {                  // close() gọi đúng lúc đang nối lại
                    close();
                    return;
                }
                onReconnected.run();
                return;
            } catch (IOException e) {
                // server chưa lên lại, 5 giây sau thử tiếp
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
