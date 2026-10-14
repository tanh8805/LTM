// Mạng và các luồng của server: nhận kết nối TCP, trả lời UDP tìm server, watchdog, lịch gọi ML (Người 3).
package exam.server;

import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class ExamServer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "scheduler");
        t.setDaemon(true);
        return t;
    });
    private final ExamRoom room;
    private final ExamLog records = new ExamLog();
    private volatile Consumer<String> listener;
    private volatile MlClient mlClient;        // null = không chấm bất thường
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    public volatile long idleTimeoutMs = 90_000;   // public volatile để test rút ngắn

    /** Chạy: java -cp target/exam.jar exam.server.ExamServer [đường dẫn rules.json] */
    public static void main(String[] args) {
        Path rulesFile = Path.of(args.length > 0 ? args[0] : "config/rules.json");
        JsonObject rules;
        try {
            rules = Protocol.parse(Files.readString(rulesFile, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Không đọc được file luật " + rulesFile.toAbsolutePath() + ": " + e.getMessage());
            System.exit(1);
            return;
        }
        ExamServer server = new ExamServer(rules);
        server.setMlClient(new MlClient("http://localhost:8000"));
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();                           // phải cài giao diện trước khi tạo cửa sổ
            new TeacherWindow(server).setVisible(true);     // tạo cửa sổ trước để log lúc khởi động hiện được
            try {
                server.start(Protocol.TCP_PORT);
                server.startDiscovery(Protocol.UDP_PORT);
            } catch (IOException e) {
                System.err.println("Không mở được cổng (đã có server khác đang chạy?): " + e.getMessage());
                System.exit(1);
            }
        });
    }

    public ExamServer(JsonObject rules) {
        room = new ExamRoom(rules, scheduler, this::log, records);
    }

    /** Phòng thi: giáo viên và test điều khiển bài thi qua đây. */
    public ExamRoom room() { return room; }

    public void setListener(Consumer<String> logListener) { listener = logListener; }

    public void setMlClient(MlClient ml) { mlClient = ml; }

    void log(String msg) {
        Consumer<String> sink = listener != null ? listener : System.out::println;    // chưa có cửa sổ thì in ra console
        String line = LocalTime.now().format(TIME) + " " + msg;
        records.appendLog(line);               // server.log của kỳ thi có đủ mọi dòng cửa sổ giám thị
        sink.accept(line);
    }

    public void start(int tcpPort) throws IOException {
        serverSocket = new ServerSocket(tcpPort);
        Thread t = new Thread(this::acceptLoop, "accept");
        t.setDaemon(true);
        t.start();
        scheduler.scheduleAtFixedRate(this::checkIdle, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::scoreIfRunning, 30, 30, TimeUnit.SECONDS);
        log("Server lắng nghe TCP cổng " + tcpPort());
    }

    public int tcpPort() { return serverSocket.getLocalPort(); }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Thread t = new Thread(new ClientHandler(this, serverSocket.accept()), "client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    log("Lỗi nhận kết nối: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        scheduler.shutdownNow();
        closeQuietly(serverSocket);
        if (udpSocket != null) {
            udpSocket.close();
        }
        room.closeAll();
        records.close();
    }

    private static void closeQuietly(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (IOException e) {
            // đã đóng hoặc hỏng sẵn, không còn gì để làm
        }
    }

    /** Watchdog: máy đang thi mà quá idleTimeoutMs không có tin nào (kể cả heartbeat FEATURES) bị coi là mất kết nối. */
    public void checkIdle() { room.dropIdle(idleTimeoutMs); }

    /** Luồng daemon trả lời EXAM_DISCOVER bằng EXAM_SERVER <cổng TCP> về đúng người gửi. */
    public void startDiscovery(int udpPort) throws SocketException {
        udpSocket = new DatagramSocket(udpPort);
        Thread t = new Thread(this::discoveryLoop, "udp-discovery");
        t.setDaemon(true);
        t.start();
        log("Server lắng nghe UDP cổng " + udpPort);
    }

    private void discoveryLoop() {
        DatagramSocket socket = udpSocket;
        byte[] buf = new byte[256];
        while (!socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String text = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
                if (text.equals(Protocol.DISCOVER)) {
                    byte[] reply = (Protocol.SERVER_REPLY + " " + tcpPort()).getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(reply, reply.length, p.getAddress(), p.getPort()));
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    log("Lỗi UDP: " + e.getMessage());
                }
            }
        }
    }

    private void scoreIfRunning() {
        if (room.isStarted() && !room.isEnded()) {
            scoreRoomNow();
        }
    }

    /** Lấy mẫu trong khối synchronized của phòng, gọi HTTP ngoài khối để ML chậm không treo cả phòng. */
    public void scoreRoomNow() {
        MlClient ml = mlClient;
        int epoch = room.epoch();              // lấy trước khi gọi HTTP: nếu giáo viên làm mới kỳ thi giữa chừng thì bỏ điểm này
        JsonArray samples = room.samplesForMl();
        if (ml == null || samples.isEmpty()) {
            return;
        }
        try {
            room.applyScores(ml.score(samples), epoch);
        } catch (IOException | RuntimeException e) {
            log("ML lỗi: " + e);               // giữ điểm cũ, không để lỗi ML làm sập server
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
