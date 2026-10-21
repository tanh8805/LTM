package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.common.Look;
import exam.common.Protocol;
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

    public volatile long idleTimeoutMs = 90_000;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        task -> newDaemonThread(task, "scheduler"));
    private final ExamLog records = new ExamLog();
    private final ExamRoom room;
    private volatile Consumer<String> listener;
    private volatile MlClient mlClient;
    private volatile Path rulesFile;               // null = không ghi ra file (dùng trong test)
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;

    public static void main(String[] args) {
        String rulesPath = "config/rules.json";
        if (args.length > 0) {
            rulesPath = args[0];
        }
        JsonObject rules = null;
        try {
            rules = Protocol.parse(Files.readString(Path.of(rulesPath), StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Không đọc được file luật " + Path.of(rulesPath).toAbsolutePath() + ": " + e.getMessage());
            System.exit(1);
        }
        ExamServer server = new ExamServer(rules);
        server.setRulesFile(Path.of(rulesPath));
        loadStudents(server);
        server.setMlClient(new MlClient("http://localhost:8000"));
        SwingUtilities.invokeLater(() -> openTeacherWindow(server));
    }

    /** Tự nạp lại danh sách sinh viên đã lưu lần trước (nếu có). */
    private static void loadStudents(ExamServer server) {
        if (!Files.exists(StudentList.FILE)) {
            return;
        }
        try {
            server.room().setStudents(StudentList.load(StudentList.FILE));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Không nạp được " + StudentList.FILE + ":\n" + e.getMessage());
        }
    }

    private static void openTeacherWindow(ExamServer server) {
        // phải cài giao diện trước khi tạo bất kỳ cửa sổ nào
        Look.install(14f);

        // tạo cửa sổ trước để log lúc khởi động hiện được
        new TeacherWindow(server).setVisible(true);
        try {
            server.start(Protocol.TCP_PORT);
            server.startDiscovery(Protocol.UDP_PORT);
        } catch (IOException e) {
            System.err.println("Không mở được cổng (đã có server khác đang chạy?): " + e.getMessage());
            System.exit(1);
        }
    }

    public ExamServer(JsonObject rules) {
        room = new ExamRoom(rules, scheduler, this::log, records);
    }

    public ExamRoom room() {
        return room;
    }

    public void setListener(Consumer<String> logListener) {
        listener = logListener;
    }

    public void setRulesFile(Path file) {
        rulesFile = file;
    }

    /** Ghi luật mới ra file (nếu có) rồi gửi cho các máy đang kết nối; ghi lỗi thì không đổi gì. */
    public void applyRules(JsonObject newRules) throws IOException {
        if (rulesFile != null) {
            RulesFile.save(rulesFile, newRules);
        }
        room.updateRules(newRules);
    }

    public void setMlClient(MlClient ml) {
        mlClient = ml;
    }

    public int tcpPort() {
        return serverSocket.getLocalPort();
    }

    void log(String msg) {
        String line = LocalTime.now().format(TIME) + " " + msg;
        // server.log của kỳ thi phải có đủ mọi dòng mà cửa sổ giám thị thấy
        records.appendLog(line);
        Consumer<String> sink = listener;
        if (sink == null) {
            // chưa có cửa sổ thì in ra console
            System.out.println(line);
            return;
        }
        sink.accept(line);
    }

    public void start(int tcpPort) throws IOException {
        serverSocket = new ServerSocket(tcpPort);
        newDaemonThread(this::acceptLoop, "accept").start();
        scheduler.scheduleAtFixedRate(this::checkIdle, 10, 10, TimeUnit.SECONDS);
        // chấm ML cùng chu kỳ với FEATURES của client
        int scoreSeconds = room.featureIntervalSeconds();
        scheduler.scheduleAtFixedRate(this::scoreIfRunning, scoreSeconds, scoreSeconds, TimeUnit.SECONDS);
        log("Server lắng nghe TCP cổng " + tcpPort());
    }

    public void startDiscovery(int udpPort) throws SocketException {
        udpSocket = new DatagramSocket(udpPort);
        newDaemonThread(this::discoveryLoop, "udp-discovery").start();
        log("Server lắng nghe UDP cổng " + udpPort);
    }

    public void stop() {
        scheduler.shutdownNow();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // đã đóng hoặc hỏng sẵn, không còn gì để làm
            }
        }
        if (udpSocket != null) {
            udpSocket.close();
        }
        room.closeAll();
        records.close();
    }

    private static Thread newDaemonThread(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    // Mỗi kết nối TCP một luồng ClientHandler riêng
    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                ClientHandler handler = new ClientHandler(this, serverSocket.accept());
                newDaemonThread(handler, "client").start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    log("Lỗi nhận kết nối: " + e.getMessage());
                }
            }
        }
    }

    // Trả lời EXAM_DISCOVER bằng "EXAM_SERVER <cổng TCP>" về đúng máy vừa hỏi
    private void discoveryLoop() {
        DatagramSocket socket = udpSocket;
        byte[] buffer = new byte[256];
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                if (text.equals(Protocol.DISCOVER)) {
                    byte[] reply = (Protocol.SERVER_REPLY + " " + tcpPort()).getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(reply, reply.length, packet.getAddress(), packet.getPort()));
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    log("Lỗi UDP: " + e.getMessage());
                }
            }
        }
    }

    // Watchdog: máy đang thi mà im lặng quá idleTimeoutMs (kể cả heartbeat FEATURES) bị coi là mất kết nối
    public void checkIdle() {
        room.dropIdle(idleTimeoutMs);
    }

    private void scoreIfRunning() {
        if (room.isStarted() && !room.isEnded()) {
            scoreRoomNow();
        }
    }

    public void scoreRoomNow() {
        MlClient ml = mlClient;
        // lấy epoch trước khi gọi HTTP: giáo viên làm mới kỳ thi giữa chừng thì bỏ điểm này
        int epoch = room.epoch();
        JsonArray samples = room.samplesForMl();
        if (ml == null || samples.isEmpty()) {
            return;
        }
        // gọi HTTP ngoài khối synchronized của phòng để ML chậm không treo cả phòng
        try {
            room.applyScores(ml.score(samples), epoch);
        } catch (IOException | RuntimeException e) {
            // giữ điểm cũ, không để lỗi ML làm sập server
            log("ML lỗi: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
