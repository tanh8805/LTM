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
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ExamRoom {
    public record Row(String studentId, String name, String status, int violations, String lastViolation,
                      Double anomalyScore, boolean anomaly, Double score, int samples) {}

    private final JsonObject rules;
    private final ScheduledExecutorService scheduler;
    private final Consumer<String> log;
    private final Map<String, Session> sessions = new TreeMap<>();    // sắp theo mã SV
    private final ExamLog records;
    private Path resultsRoot = Path.of("ket-qua"), examDir;
    private JsonObject exam;
    private JsonArray clientQuestions;         // câu hỏi không có "answer"
    private boolean started, ended;
    private String endReason = "";
    private MlScores mlScores = new MlScores();
    private int epoch;                         // tăng mỗi lần làm mới kỳ thi, để bỏ qua điểm ML trả về muộn của kỳ cũ
    private long startMillis;
    private ScheduledFuture<?> autoEnd;

    ExamRoom(JsonObject rules, ScheduledExecutorService scheduler, Consumer<String> log, ExamLog records) {
        this.rules = rules;
        this.scheduler = scheduler;
        this.log = log;
        this.records = records;
    }

    /** Thư mục gốc chứa thư mục của từng kỳ thi (mặc định ket-qua/). */
    public synchronized void setResultsRoot(Path p) { resultsRoot = p; }
    /** Thư mục của kỳ thi hiện tại, null nếu chưa bấm Bắt đầu. */
    public synchronized Path examDir() { return examDir; }
    public synchronized Path resultsRoot() { return resultsRoot; }
    public List<ExamLog.Event> history(String studentId) { return records.history(studentId); }
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
        try {
            examDir = records.attach(resultsRoot, examTitle());
        } catch (IOException e) {
            log.accept("Không tạo được thư mục kết quả " + resultsRoot + ": " + e.getMessage());   // vẫn cho thi, chỉ mất file lưu
        }
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
        records.record("", "", "EXAM_END", reason + ". Đã nộp " + done + "/" + sessions.size() + " máy");
        writeResults();
    }

    /** Làm mới phòng cho kỳ thi kế tiếp: chỉ được gọi sau khi kết thúc; đề đang nạp được giữ lại, kết quả kỳ trước nằm nguyên trong thư mục của nó. */
    public synchronized void newSession() {
        if (!ended) { throw new IllegalStateException("Kỳ thi chưa kết thúc"); }
        sessions.values().stream().filter(s -> s.connected() && !s.submitted).forEach(this::sendEnd);
        closeAll();
        sessions.clear();
        mlScores = new MlScores();
        started = ended = false;
        endReason = "";
        startMillis = 0;
        autoEnd = null;
        examDir = null;
        epoch++;
        records.reset();
        log.accept("Kỳ thi mới: đã xoá phiên và mẫu cũ, giữ đề \"" + examTitle() + "\"");
    }

    public synchronized int epoch() { return epoch; }

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
        event(s, "EXAM_SENT", "còn " + remainingSeconds() + " giây");
        s.lastSeen = System.currentTimeMillis();       // thời gian im lặng chỉ tính từ lúc máy nhận đề, không tính từ lúc chờ trước giờ thi
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

    private void event(Session s, String type, String detail) { records.record(s.id, s.name, type, detail); }

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
            records.record(id, name, "LOGIN_FAIL", fail);
            return null;
        }
        boolean returning = s != null;         // máy cũ đã mất kết nối thì giữ nguyên session và chỉ gắn socket mới
        if (!returning) {
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
        event(s, returning ? "RECONNECT" : "LOGIN", "");
        if (s.submitted) { sendResult(s); } else { trySendExam(s); }   // RESULT có thể đã mất khi rớt mạng
        return s;
    }

    /** Session của kỳ trước (đã bị xoá khi làm mới) còn nhận tin muộn thì phải bỏ qua. */
    private boolean live(Session s) { return sessions.get(s.id) == s; }

    synchronized void touch(Session s) { if (live(s)) { s.lastSeen = System.currentTimeMillis(); } }
    synchronized void features(Session s, JsonObject sample) { if (live(s)) { s.samples.add(sample); } }

    synchronized void precheckResult(Session s, boolean ok, String issues) {
        if (!live(s)) { return; }
        s.precheckOk = ok;
        s.precheckFailed = !ok;
        log.accept("PRECHECK " + s.id + (ok ? ": đạt" : ": lỗi - " + issues));
        if (ok) { event(s, "PRECHECK_OK", ""); } else { event(s, "PRECHECK_FAIL", issues); }
        trySendExam(s);
    }

    synchronized void violation(Session s, String kind, String detail) {
        if (!live(s)) { return; }
        s.violations++;
        s.lastViolation = kind + ": " + detail;
        log.accept("Vi phạm " + s.id + " - " + s.lastViolation);
        event(s, "VIOLATION", s.lastViolation);
    }

    synchronized void submit(Session s, JsonObject answers) {
        if (!live(s)) { return; }
        if (!started) { log.accept("Bỏ qua SUBMIT của " + s.id + ": bài thi chưa bắt đầu"); return; }
        if (s.result == null) {                // nộp lần hai không chấm lại, chỉ gửi lại RESULT cũ
            s.result = ExamFile.grade(exam, answers);
            s.submitted = true;
            log.accept("Nộp bài: " + s.id + " - điểm " + s.result.get("score").getAsString());
            event(s, "SUBMIT", "điểm " + s.result.get("score").getAsString() + " ("
                + s.result.get("correct").getAsString() + "/" + s.result.get("total").getAsString() + ")");
            writeResults();
        }
        if (s.connected()) { sendResult(s); }
    }

    /** Chỉ đánh dấu mất kết nối nếu socket vẫn là socket của luồng đọc vừa kết thúc (tránh đá máy đã vào lại). */
    synchronized void disconnected(Session s, Socket socket) {
        if (s != null && live(s) && s.socket == socket) { markDisconnected(s, "socket đóng"); }
    }

    private void markDisconnected(Session s, String why) {
        s.socket = null;
        s.out = null;
        log.accept("Mất kết nối: " + s.id);
        event(s, "DISCONNECT", why);
    }

    /** Máy đang thi mà im lặng quá timeoutMs (mất heartbeat): đánh dấu mất kết nối ngay rồi đóng socket. */
    synchronized void dropIdle(long timeoutMs) {
        long now = System.currentTimeMillis();
        for (Session s : sessions.values()) {
            if (Session.ST_IN_EXAM.equals(s.status(started)) && now - s.lastSeen > timeoutMs) {
                s.close();                     // đóng trước vì markDisconnected sẽ xoá tham chiếu socket
                markDisconnected(s, "im lặng quá " + timeoutMs / 1000 + " giây (watchdog)");
            }
        }
    }

    synchronized void closeAll() { sessions.values().forEach(Session::close); }

    synchronized JsonArray samplesForMl() { return MlScores.samples(sessions.values()); }

    synchronized void applyScores(JsonObject response) { applyScores(response, epoch); }

    /** Điểm ML tính cho kỳ thi cũ (epoch khác) thì bỏ, không áp vào máy cùng mã SV của kỳ mới. */
    synchronized void applyScores(JsonObject response, int forEpoch) {
        if (forEpoch != epoch) { return; }
        mlScores.apply(response, sessions, log, (s, type) -> event(s, type, String.format(Locale.ROOT, "%.2f", s.anomalyScore)));
    }

    public synchronized List<Row> rows() {
        List<Row> list = new ArrayList<>();
        for (Session s : sessions.values()) {
            list.add(new Row(s.id, s.name, s.status(started), s.violations, s.lastViolation, s.anomalyScore,
                s.anomaly, s.result == null ? null : s.result.get("score").getAsDouble(), s.samples.size()));
        }
        return list;
    }

    private void writeResults() {
        try {
            if (examDir == null) { return; }
            ExamFile.writeResults(examDir.resolve("results.csv"), new ArrayList<>(sessions.values()), s -> s.status(started));
        } catch (IOException e) {
            log.accept("Không ghi được results.csv: " + e.getMessage());
        }
    }
}
