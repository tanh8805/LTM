// Owner: Nguoi1

package exam.server.net;

import exam.common.protocol.Message;
import exam.common.protocol.MessageCodec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Bọc một TCP socket của một client. Giao thức: mỗi message là MỘT dòng JSON kết thúc bằng '\n'.
 *
 *   đọc : readLine()  -> chuỗi JSON (chạy trên thread của client đó)
 *   ghi : send(message) -> encode + '\n' (có thể được nhiều thread gọi nên là synchronized)
 */
public class ClientConnection {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final MessageCodec codec = new MessageCodec();
    private final int maxLineLength;

    public ClientConnection(Socket socket, int maxLineLength) throws IOException {
        this.socket = socket;
        this.maxLineLength = maxLineLength;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Đọc một dòng (chặn đến khi có dữ liệu). Trả về null khi client đã đóng kết nối.
     * Dòng dài hơn maxLineLength thì ném IOException: không để một client gửi dữ liệu vô hạn
     * làm tràn bộ nhớ Server.
     */
    public String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1) {
            if (character == '\n') {
                int length = line.length();
                if (length > 0 && line.charAt(length - 1) == '\r') {
                    line.setLength(length - 1);
                }
                return line.toString();
            }
            line.append((char) character);
            if (line.length() > maxLineLength) {
                throw new IOException("Dòng message dài quá " + maxLineLength + " ký tự");
            }
        }
        return null;
    }

    /**
     * Gửi một message. Có thể nhiều thread gọi cùng lúc (ClientHandler trả lời, PeriodicJobs gửi ALERT, ...),
     * nên synchronized để hai message không bị ghi chồng lên nhau trên cùng một dòng.
     *
     * @return true nếu gửi được, false nếu mất kết nối
     */
    public synchronized boolean send(Message message) {
        try {
            writer.write(codec.encode(message));
            writer.write('\n');
            writer.flush();
            return true;
        } catch (IOException e) {
            System.out.println("[Net] Gửi " + message.type + " tới " + getRemoteAddress() + " thất bại: " + e.getMessage());
            return false;
        }
    }

    /** Đặt thời gian chờ tối đa khi đọc. Quá hạn readLine() ném SocketTimeoutException. */
    public void setReadTimeout(int milliseconds) throws SocketException {
        socket.setSoTimeout(milliseconds);
    }

    public String getRemoteAddress() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    /** Đóng socket. Gọi nhiều lần vẫn an toàn. */
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("[Net] Lỗi khi đóng socket " + getRemoteAddress() + ": " + e.getMessage());
        }
    }
}
