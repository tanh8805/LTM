// Luật của phòng thi: session, đề đang dùng, bắt đầu/kết thúc, nộp bài. Mọi thay đổi trạng thái đều synchronized ở đây (Người 3).
package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ExamRoom {
    public record Row(String studentId, String name, String status, int violations, String lastViolation,
                      Double anomalyScore, boolean anomaly, Double score) {}

    private final JsonObject rules;
    private final ScheduledExecutorService scheduler;
    private final Consumer<String> log;
    private final Map<String, Session> sessions = new TreeMap<>();    // sắp theo mã SV
    private Path resultsFile = Path.of("results.csv");
    private JsonObject exam;
    private JsonArray clientQuestions;         // câu hỏi không có "answer"
    private boolean started, ended;
    private String endReason = "";
    private long startMillis;
    private ScheduledFuture<?> autoEnd;

    ExamRoom(JsonObject rules, ScheduledExecutorService scheduler, Consumer<String> log) {
        this.rules = rules;
        this.scheduler = scheduler;
        this.log = log;
    }

    public synchronized void setResultsFile(Path p) { resultsFile = p; }
    public synchronized String examTitle() { return exam == null ? null : exam.get("title").getAsString(); }
    public synchronized boolean isStarted() { return started; }
    public synchronized boolean isEnded() { return ended; }

    public synchronized void loadExam(Path file) throws IOException {
        if (started) { throw new IllegalStateException("Bài thi đã bắt đầu, không nạp đề khác được"); }
        exam = ExamFile.load(file);            // ném IllegalArgumentException nếu đề sai, khi đó đề cũ vẫn còn
        clientQuestions = ExamFile.questionsForClient(exam);
        log.accept("Đã nạp đề: " + examTitle());
    }

    public synchronized void startExam() {
        if (exam == null) { throw new IllegalStateException("Chưa nạp đề"); }
        if (started) { throw new IllegalStateException("Bài thi đã bắt đầu"); }
        started = true;
        startMillis = System.currentTimeMillis();
        autoEnd = scheduler.schedule(() -> endExam("Hết giờ"), exam.get("durationMinutes").getAsInt(), TimeUnit.MINUTES);
        log.accept("Bắt đầu thi: " + examTitle());
        sessions.values().forEach(this::trySendExam);
    }

    public synchronized void endExam(String reason) {
        if (!started || ended) { return; }
        ended = true;
        endReason = reason;
        autoEnd.cancel(false);
        long done = sessions.values().stream().filter(s -> s.submitted).count();
        sessions.values().stream().filter(s -> s.connected() && !s.submitted).forEach(this::sendEnd);
        log.accept("Kết thúc thi: " + reason + ". Đã nộp " + done + "/" + sessions.size() + " máy");
        writeResults();
    }

    public synchronized int remainingSeconds() {
        if (exam == null || ended) { return 0; }     // đã kết thúc thì đồng hồ phải dừng ở 0
        int total = exam.get("durationMinutes").getAsInt() * 60;
        long elapsed = started ? (System.currentTimeMillis() - startMillis) / 1000 : 0;
        return (int) Math.max(0, total - elapsed);
    }

    /** Điểm gửi đề duy nhất: dùng cho cả vào thi muộn lẫn vào lại sau khi rớt mạng. */
    private void trySendExam(Session s) {
        if (!started || !s.connected() || !s.precheckOk || s.submitted) { return; }
        if (ended) { sendEnd(s); return; }             // vào sau khi kết thúc: báo hết giờ để client tự nộp
        JsonObject m = Protocol.msg(Protocol.EXAM_START);
        m.addProperty("title", examTitle());
        m.addProperty("remainingSeconds", remainingSeconds());
        m.add("questions", clientQuestions);
        Protocol.send(s.out, m);
    }

    private void sendEnd(Session s) {
        JsonObject m = Protocol.msg(Protocol.EXAM_END);
        m.addProperty("reason", endReason);
        Protocol.send(s.out, m);
    }

    private void sendResult(Session s) {
        JsonObject m = s.result.deepCopy();
        m.addProperty("type", Protocol.RESULT);
        Protocol.send(s.out, m);
    }

    /** Trả session nếu đăng nhập được; nếu không thì đã gửi LOGIN_FAIL và trả null. */
    synchronized Session login(String id, String name, Socket socket, PrintWriter out) {
        Session s = sessions.get(id);
        String fail = id.isEmpty() || name.isEmpty() ? "Thiếu mã SV hoặc họ tên"
            : s != null && s.connected() ? "Mã SV này đang đăng nhập ở máy khác"
            : s == null && ended ? "Bài thi đã kết thúc" : null;
        if (fail != null) {
            JsonObject m = Protocol.msg(Protocol.LOGIN_FAIL);
            m.addProperty("reason", fail);
            Protocol.send(out, m);
            return null;
        }
        if (s == null) {                       // máy cũ đã mất kết nối thì giữ nguyên session và chỉ gắn socket mới
            s = new Session(id, name);
            sessions.put(id, s);
        }
        s.socket = socket;
        s.out = out;
        s.lastSeen = System.currentTimeMillis();
        JsonObject ok = Protocol.msg(Protocol.LOGIN_OK);
        ok.add("rules", rules);
        Protocol.send(out, ok);
        log.accept("Đăng nhập: " + id + " - " + s.name);
        if (s.submitted) { sendResult(s); } else { trySendExam(s); }   // RESULT có thể đã mất khi rớt mạng
        return s;
    }

    synchronized void touch(Session s) { s.lastSeen = System.currentTimeMillis(); }
    synchronized void features(Session s, JsonObject sample) { s.samples.add(sample); }

    synchronized void precheckResult(Session s, boolean ok, String issues) {
        s.precheckOk = ok;
        s.precheckFailed = !ok;
        log.accept("PRECHECK " + s.id + (ok ? ": đạt" : ": lỗi - " + issues));
        trySendExam(s);
    }

    synchronized void violation(Session s, String kind, String detail) {
        s.violations++;
        s.lastViolation = kind + ": " + detail;
        log.accept("Vi phạm " + s.id + " - " + s.lastViolation);
    }

    synchronized void submit(Session s, JsonObject answers) {
        if (!started) { log.accept("Bỏ qua SUBMIT của " + s.id + ": bài thi chưa bắt đầu"); return; }
        if (s.result == null) {                // nộp lần hai không chấm lại, chỉ gửi lại RESULT cũ
            s.result = ExamFile.grade(exam, answers);
            s.submitted = true;
            log.accept("Nộp bài: " + s.id + " - điểm " + s.result.get("score").getAsString());
            writeResults();
        }
        if (s.connected()) { sendResult(s); }
    }

    /** Chỉ đánh dấu mất kết nối nếu socket vẫn là socket của luồng đọc vừa kết thúc (tránh đá máy đã vào lại). */
    synchronized void disconnected(Session s, Socket socket) {
        if (s != null && s.socket == socket) { markDisconnected(s); }
    }

    private void markDisconnected(Session s) {
        s.socket = null;
        s.out = null;
        log.accept("Mất kết nối: " + s.id);
    }

    /** Máy đang thi mà im lặng quá timeoutMs (mất heartbeat): đánh dấu mất kết nối ngay rồi đóng socket. */
    synchronized void dropIdle(long timeoutMs) {
        long now = System.currentTimeMillis();
        for (Session s : sessions.values()) {
            if (Session.ST_IN_EXAM.equals(s.status(started)) && now - s.lastSeen > timeoutMs) {
                s.close();                     // đóng trước vì markDisconnected sẽ xoá tham chiếu socket
                markDisconnected(s);
            }
        }
    }

    synchronized void closeAll() { sessions.values().forEach(Session::close); }

    synchronized JsonArray samplesForMl() { return MlScores.samples(sessions.values()); }

    synchronized void applyScores(JsonObject response) { MlScores.apply(response, sessions, log); }

    public synchronized List<Row> rows() {
        List<Row> list = new ArrayList<>();
        for (Session s : sessions.values()) {
            list.add(new Row(s.id, s.name, s.status(started), s.violations, s.lastViolation, s.anomalyScore,
                s.anomaly, s.result == null ? null : s.result.get("score").getAsDouble()));
        }
        return list;
    }

    private void writeResults() {
        try {
            ExamFile.writeResults(resultsFile, new ArrayList<>(sessions.values()), s -> s.status(started));
        } catch (IOException e) {
            log.accept("Không ghi được " + resultsFile + ": " + e.getMessage());
        }
    }
}
