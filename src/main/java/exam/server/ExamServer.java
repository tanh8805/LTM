// Server thi: giữ các session, nạp đề, hẹn giờ, chấm điểm và ghi results.csv. Mọi thay đổi trạng thái chung nằm ở đây.
package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class ExamServer {
    public static final String ST_LOGGED_IN = "Đã đăng nhập", ST_PRECHECK_FAIL = "PRECHECK lỗi",
        ST_READY = "Sẵn sàng", ST_IN_EXAM = "Đang thi", ST_SUBMITTED = "Đã nộp", ST_DISCONNECTED = "Mất kết nối";

    public record Row(String studentId, String name, String status, int violations, String lastViolation,
                      Double anomalyScore, boolean anomaly, Double score) {}

    /** Trạng thái của một sinh viên; vẫn giữ nguyên khi máy mất kết nối để vào lại được. */
    static class Session {
        final String id, name;
        Socket socket;
        PrintWriter out;                       // null = đang mất kết nối
        boolean precheckOk, precheckFailed, submitted, anomaly;
        int violations;
        String lastViolation = "";
        final List<JsonObject> samples = new ArrayList<>();
        Double anomalyScore, maxAnomaly;
        JsonObject result;                     // {score, correct, total}, null nếu chưa nộp
        long lastSeen;

        Session(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JsonObject rules;
    private final Map<String, Session> sessions = new TreeMap<>();    // sắp theo mã SV
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "scheduler");
        t.setDaemon(true);
        return t;
    });
    private Consumer<String> listener;
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    private MlClient mlClient;                 // null = không chấm bất thường
    private Path resultsFile = Path.of("results.csv");
    private JsonObject exam;
    private JsonArray publicQuestions;         // bản sao câu hỏi không có "answer"
    private boolean started, ended;
    private long startMillis;
    private ScheduledFuture<?> autoEnd;
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
        this.rules = rules;
    }

    public void setListener(Consumer<String> logListener) {
        this.listener = logListener;
    }

    public void setResultsFile(Path p) {
        this.resultsFile = p;
    }

    void log(String msg) {
        String line = LocalTime.now().format(TIME) + " " + msg;
        Consumer<String> l = listener;
        if (l != null) {
            l.accept(line);
        } else {
            System.out.println(line);
        }
    }

    // ---------- Mạng ----------

    public void start(int tcpPort) throws IOException {
        serverSocket = new ServerSocket(tcpPort);
        Thread t = new Thread(this::acceptLoop, "accept");
        t.setDaemon(true);
        t.start();
        scheduler.scheduleAtFixedRate(this::checkIdle, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::scoreIfRunning, 30, 30, TimeUnit.SECONDS);
        log("Server lắng nghe TCP cổng " + tcpPort());
    }

    public int tcpPort() {
        return serverSocket.getLocalPort();
    }

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

    public void stop() {
        scheduler.shutdownNow();
        synchronized (this) {
            closeQuietly(serverSocket);
            if (udpSocket != null) {
                udpSocket.close();
            }
            for (Session s : sessions.values()) {
                closeQuietly(s.socket);
            }
        }
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

    // ---------- Đề thi ----------

    public synchronized void loadExam(Path file) throws IOException {
        if (started) {
            throw new IllegalStateException("Bài thi đã bắt đầu, không nạp đề khác được");
        }
        JsonObject e;
        try {
            e = Protocol.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("File đề không phải JSON hợp lệ");
        }
        publicQuestions = validate(e);
        exam = e;
        log("Đã nạp đề: " + examTitle());
    }

    private static IllegalArgumentException bad(String msg) {
        return new IllegalArgumentException(msg);
    }

    /** Trả null nếu không phải số nguyên (chuỗi, số lẻ, thiếu). */
    static Integer asInt(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double d = e.getAsDouble();
        return d == Math.rint(d) ? (int) d : null;
    }

    /** Kiểm tra đề; trả về danh sách câu hỏi đã bỏ đáp án để gửi cho sinh viên. */
    private static JsonArray validate(JsonObject e) {
        Integer minutes = asInt(e.get("durationMinutes"));
        if (Protocol.str(e, "title").isEmpty()) {
            throw bad("Đề thiếu tiêu đề (title)");
        }
        if (minutes == null || minutes <= 0) {
            throw bad("durationMinutes phải là số nguyên > 0");
        }
        if (!(e.get("questions") instanceof JsonArray qs) || qs.isEmpty()) {
            throw bad("Đề phải có ít nhất một câu hỏi (questions)");
        }
        Set<Integer> ids = new HashSet<>();
        JsonArray pub = new JsonArray();
        for (int i = 0; i < qs.size(); i++) {
            String at = "Câu thứ " + (i + 1) + ": ";
            if (!(qs.get(i) instanceof JsonObject q)) {
                throw bad(at + "không phải object");
            }
            Integer id = asInt(q.get("id"));
            if (id == null || !ids.add(id)) {
                throw bad(at + "id phải là số nguyên và không được trùng");
            }
            if (Protocol.str(q, "text").isEmpty()) {
                throw bad(at + "thiếu nội dung (text)");
            }
            if (!(q.get("options") instanceof JsonArray opts) || opts.size() < 2) {
                throw bad(at + "cần ít nhất 2 lựa chọn (options)");
            }
            Integer ans = asInt(q.get("answer"));
            if (ans == null || ans < 0 || ans >= opts.size()) {
                throw bad(at + "answer phải là chỉ số từ 0 đến " + (opts.size() - 1));
            }
            JsonObject copy = new JsonObject();
            copy.add("id", q.get("id"));
            copy.add("text", q.get("text"));
            copy.add("options", opts);
            pub.add(copy);
        }
        return pub;
    }

    /** Chấm điểm: câu bỏ trống hoặc đáp án không phải số nguyên tính là sai. */
    public static JsonObject grade(JsonObject exam, JsonObject answers) {
        JsonArray qs = exam.getAsJsonArray("questions");
        int correct = 0;
        for (JsonElement el : qs) {
            JsonObject q = el.getAsJsonObject();
            Integer given = asInt(answers.get(String.valueOf(q.get("id").getAsInt())));
            if (given != null && given == q.get("answer").getAsInt()) {
                correct++;
            }
        }
        JsonObject r = new JsonObject();
        r.addProperty("score", qs.isEmpty() ? 0.0 : Math.round(correct * 1000.0 / qs.size()) / 100.0);
        r.addProperty("correct", correct);
        r.addProperty("total", qs.size());
        return r;
    }

    // ---------- Điều khiển bài thi ----------

    public synchronized String examTitle() {
        return exam == null ? null : exam.get("title").getAsString();
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public synchronized boolean isEnded() {
        return ended;
    }

    public synchronized void startExam() {
        if (exam == null) {
            throw new IllegalStateException("Chưa nạp đề");
        }
        if (started) {
            throw new IllegalStateException("Bài thi đã bắt đầu");
        }
        started = true;
        startMillis = System.currentTimeMillis();
        autoEnd = scheduler.schedule(() -> endExam("Hết giờ"),
            exam.get("durationMinutes").getAsInt(), TimeUnit.MINUTES);
        log("Bắt đầu thi: " + examTitle());
        for (Session s : sessions.values()) {
            trySendExam(s);
        }
    }

    public synchronized void endExam(String reason) {
        if (!started || ended) {
            return;
        }
        ended = true;
        autoEnd.cancel(false);
        for (Session s : sessions.values()) {
            if (s.out != null && !s.submitted) {
                JsonObject m = Protocol.msg(Protocol.EXAM_END);
                m.addProperty("reason", reason);
                Protocol.send(s.out, m);
            }
        }
        log("Kết thúc thi: " + reason);
        writeResults();
    }

    public synchronized int remainingSeconds() {
        if (exam == null) {
            return 0;
        }
        int total = exam.get("durationMinutes").getAsInt() * 60;
        long elapsed = started ? (System.currentTimeMillis() - startMillis) / 1000 : 0;
        return (int) Math.max(0, total - elapsed);
    }

    /** Điểm gửi đề duy nhất: dùng cho cả vào thi muộn lẫn vào lại sau khi rớt mạng. */
    private void trySendExam(Session s) {
        if (!started || s.out == null || !s.precheckOk || s.submitted) {
            return;
        }
        JsonObject m = Protocol.msg(Protocol.EXAM_START);
        m.addProperty("title", examTitle());
        m.addProperty("remainingSeconds", remainingSeconds());
        m.add("questions", publicQuestions);
        Protocol.send(s.out, m);
    }

    // ---------- Xử lý tin nhắn (ClientHandler gọi) ----------

    /** Trả session nếu đăng nhập được; nếu không thì đã gửi LOGIN_FAIL và trả null. */
    synchronized Session login(String id, String name, Socket socket, PrintWriter out) {
        String fail = null;
        Session s = sessions.get(id);
        if (id.isEmpty() || name.isEmpty()) {
            fail = "Thiếu mã SV hoặc họ tên";
        } else if (s != null && s.out != null) {
            fail = "Mã SV này đang đăng nhập ở máy khác";
        }
        if (fail != null) {
            JsonObject m = Protocol.msg(Protocol.LOGIN_FAIL);
            m.addProperty("reason", fail);
            Protocol.send(out, m);
            return null;
        }
        if (s == null) {
            s = new Session(id, name);
            sessions.put(id, s);
        }
        s.socket = socket;
        s.out = out;
        s.lastSeen = System.currentTimeMillis();
        JsonObject ok = Protocol.msg(Protocol.LOGIN_OK);
        ok.add("rules", rules);
        Protocol.send(out, ok);
        log("Đăng nhập: " + id + " - " + s.name);
        trySendExam(s);
        return s;
    }

    synchronized void touch(Session s) {
        s.lastSeen = System.currentTimeMillis();
    }

    synchronized void precheckResult(Session s, boolean ok, String issues) {
        s.precheckOk = ok;
        s.precheckFailed = !ok;
        log("PRECHECK " + s.id + (ok ? ": đạt" : ": lỗi - " + issues));
        trySendExam(s);
    }

    synchronized void violation(Session s, String kind, String detail) {
        s.violations++;
        s.lastViolation = kind + ": " + detail;
        log("Vi phạm " + s.id + " - " + s.lastViolation);
    }

    synchronized void features(Session s, JsonObject sample) {
        s.samples.add(sample);
    }

    synchronized void submit(Session s, JsonObject answers) {
        if (!started) {
            log("Bỏ qua SUBMIT của " + s.id + ": bài thi chưa bắt đầu");
            return;
        }
        if (s.result == null) {                // nộp lần hai không chấm lại
            s.result = grade(exam, answers);
            s.submitted = true;
            log("Nộp bài: " + s.id + " - điểm " + s.result.get("score").getAsString());
            writeResults();
        }
        if (s.out != null) {
            JsonObject m = s.result.deepCopy();
            m.addProperty("type", Protocol.RESULT);
            Protocol.send(s.out, m);
        }
    }

    /** Chỉ đánh dấu mất kết nối nếu socket vẫn là socket của luồng đọc vừa kết thúc. */
    synchronized void disconnected(Session s, Socket socket) {
        if (s != null && s.socket == socket) {
            markDisconnected(s);
        }
    }

    private void markDisconnected(Session s) {
        s.socket = null;
        s.out = null;
        log("Mất kết nối: " + s.id);
    }

    /** Máy đang thi mà im lặng quá lâu (mất heartbeat): đánh dấu ngay rồi đóng socket. */
    public synchronized void checkIdle() {
        long now = System.currentTimeMillis();
        for (Session s : sessions.values()) {
            if (ST_IN_EXAM.equals(statusOf(s)) && now - s.lastSeen > idleTimeoutMs) {
                Socket old = s.socket;
                markDisconnected(s);
                closeQuietly(old);
            }
        }
    }

    // ---------- Chấm bất thường bằng service ML ----------

    public synchronized void setMlClient(MlClient ml) {
        this.mlClient = ml;
    }

    private void scoreIfRunning() {
        if (isStarted() && !isEnded()) {
            scoreRoomNow();
        }
    }

    /** Gom mẫu trong khối synchronized, gọi HTTP ngoài khối để ML chậm không treo cả server. */
    public void scoreRoomNow() {
        MlClient ml;
        JsonArray samples = new JsonArray();
        synchronized (this) {
            ml = mlClient;
            for (Session s : sessions.values()) {
                for (JsonObject sample : s.samples) {
                    JsonObject copy = sample.deepCopy();
                    copy.addProperty("studentId", s.id);
                    samples.add(copy);
                }
            }
        }
        if (ml == null || samples.isEmpty()) {
            return;
        }
        try {
            applyScores(ml.score(samples));
        } catch (IOException | RuntimeException e) {
            log("ML lỗi: " + e);          // giữ điểm cũ, không để lỗi ML làm sập server
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void applyScores(JsonObject response) {
        if (!"ok".equals(Protocol.str(response, "status"))) {
            return;                                    // not_enough_data: chưa đủ mẫu để so với số đông
        }
        for (JsonElement el : response.getAsJsonArray("results")) {
            JsonObject r = el.getAsJsonObject();
            Session s = sessions.get(Protocol.str(r, "studentId"));
            if (s == null) {
                continue;
            }
            double score = r.get("score").getAsDouble();
            s.anomalyScore = score;
            s.anomaly = r.get("anomaly").getAsBoolean();
            s.maxAnomaly = s.maxAnomaly == null ? score : Math.max(s.maxAnomaly, score);
        }
    }

    // ---------- Hiển thị và xuất file ----------

    private String statusOf(Session s) {
        if (s.submitted) {
            return ST_SUBMITTED;
        }
        if (s.out == null) {
            return ST_DISCONNECTED;
        }
        if (s.precheckOk) {
            return started ? ST_IN_EXAM : ST_READY;
        }
        return s.precheckFailed ? ST_PRECHECK_FAIL : ST_LOGGED_IN;
    }

    public synchronized List<Row> rows() {
        List<Row> list = new ArrayList<>();
        for (Session s : sessions.values()) {
            Double score = s.result == null ? null : s.result.get("score").getAsDouble();
            list.add(new Row(s.id, s.name, statusOf(s), s.violations, s.lastViolation,
                s.anomalyScore, s.anomaly, score));
        }
        return list;
    }

    private void writeResults() {
        StringBuilder sb = new StringBuilder("﻿studentId,name,score,correct,total,violations,maxAnomaly,status\n");
        for (Session s : sessions.values()) {
            JsonObject r = s.result;
            sb.append(csv(s.id)).append(',').append(csv(s.name)).append(',')
              .append(r == null ? "" : r.get("score").getAsString()).append(',')
              .append(r == null ? "" : r.get("correct").getAsString()).append(',')
              .append(r == null ? "" : r.get("total").getAsString()).append(',')
              .append(s.violations).append(',')
              .append(s.maxAnomaly == null ? "" : s.maxAnomaly.toString()).append(',')
              .append(csv(statusOf(s))).append('\n');
        }
        try {
            Files.writeString(resultsFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log("Không ghi được " + resultsFile + ": " + e.getMessage());
        }
    }

    private static String csv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
