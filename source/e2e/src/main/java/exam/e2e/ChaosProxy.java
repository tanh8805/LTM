// Owner: Nguoi1

package exam.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Proxy TCP đứng giữa client và Server để GÂY LỖI MẠNG trong kiểm thử:
 *
 *   cutAll()     : đóng mọi kết nối đang chạy (hai phía thấy kết nối bị đóng, như rút dây mạng).
 *   silenceAll() : mọi kết nối đang chạy vẫn "mở" nhưng không chuyển thêm byte nào theo cả hai chiều
 *                  (mạng chết lặng, không có FIN/RST). Kết nối MỚI sau đó vẫn chạy bình thường.
 */
public class ChaosProxy implements AutoCloseable {

    /** Một kết nối client <-> Server đi qua proxy. */
    private static class Pipe {
        final Socket client;
        final Socket server;
        volatile boolean silenced = false;

        Pipe(Socket client, Socket server) {
            this.client = client;
            this.server = server;
        }

        void closeBoth() {
            closeQuietly(client);
            closeQuietly(server);
        }
    }

    private final ServerSocket listener;
    private final String targetHost;
    private final int targetPort;
    private final List<Pipe> pipes = new CopyOnWriteArrayList<>();

    public ChaosProxy(String targetHost, int targetPort) throws IOException {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.listener = new ServerSocket(0, 50, InetAddress.getByName("localhost"));
        Thread.startVirtualThread(this::acceptLoop);
    }

    public int getPort() {
        return listener.getLocalPort();
    }

    public void cutAll() {
        for (Pipe pipe : pipes) {
            pipe.closeBoth();
        }
        pipes.clear();
    }

    public void silenceAll() {
        for (Pipe pipe : pipes) {
            pipe.silenced = true;
        }
    }

    private void acceptLoop() {
        while (!listener.isClosed()) {
            try {
                Socket client = listener.accept();
                Socket server = new Socket(targetHost, targetPort);
                Pipe pipe = new Pipe(client, server);
                pipes.add(pipe);
                Thread.startVirtualThread(() -> pump(pipe, client, server));
                Thread.startVirtualThread(() -> pump(pipe, server, client));
            } catch (IOException e) {
                if (!listener.isClosed()) {
                    System.out.println("[Proxy] Lỗi: " + e.getMessage());
                }
            }
        }
    }

    /** Chép byte từ from sang to. Khi pipe bị "silenced" thì đọc và vứt bỏ. Khi một đầu đóng thì đóng cả hai. */
    private void pump(Pipe pipe, Socket from, Socket to) {
        byte[] buffer = new byte[4096];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (!pipe.silenced) {
                    out.write(buffer, 0, count);
                    out.flush();
                }
            }
        } catch (IOException e) {
            // Một đầu bị đóng hoặc cắt: bình thường trong kiểm thử lỗi mạng.
        }
        pipe.closeBoth();
    }

    @Override
    public void close() {
        closeQuietly(listener);
        cutAll();
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // Đã đóng rồi: không có gì để làm.
        }
    }
}
