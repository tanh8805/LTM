package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.client.AppRules;
import exam.common.Protocol;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
                      Double anomalyScore, boolean anomaly, Double score, int samples, String os, int focusLost,
                      String lastViolationAt) {}

    public record AppInfo(String name, String title) {}

    /** BLOCKED = trong blacklistApps, ALLOWED = được phép có cửa sổ, OTHER = chưa có trong danh sách nào. */
    public enum AppKind { BLOCKED, ALLOWED, OTHER }

    public record AppLine(String name, String title, AppKind kind) {}

    /** updatedAt rỗng nghĩa là máy chưa báo danh sách app lần nào. */
    public record AppsView(List<AppLine> apps, String updatedAt) {}

    public record Summary(String title, int questions, int minutes, String startedAt) {}

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm"),
        HOUR_MINUTE_SECOND = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JsonObject rules;
    private final ScheduledExecutorService scheduler;
    private final Consumer<String> log;
    private final ExamLog records;
    private final Map<String, Session> sessions = new TreeMap<>();

    private Path resultsRoot = Path.of("ket-qua");
    private Path examDir;
    private JsonObject exam;
    private JsonArray clientQuestions;
    private boolean started;
    private boolean ended;
    private String endReason = "";
    private long startMillis;
    private ScheduledFuture<?> autoEnd;
    private final int featureIntervalSeconds;
    private MlScores mlScores;
    private int epoch;
    private StudentList students = StudentList.empty();

    ExamRoom(JsonObject rules, ScheduledExecutorService scheduler, Consumer<String> log, ExamLog records) {
        this.rules = rules;
        this.scheduler = scheduler;
        this.log = log;
        this.records = records;
        this.featureIntervalSeconds = Protocol.featureInterval(rules);
        this.mlScores = new MlScores(featureIntervalSeconds);
    }

    // ---------- Giám thị điều khiển phòng ----------

    public synchronized void loadExam(Path file) throws IOException {
        if (started) {
            throw new IllegalStateException("Bài thi đã bắt đầu, không nạp đề khác được");
        }
        // đề sai thì ExamFile.load ném IllegalArgumentException và đề cũ vẫn còn nguyên
        exam = ExamFile.load(file);
        clientQuestions = ExamFile.questionsForClient(exam);
        log.accept("Đã nạp đề: " + examTitle());
    }

    public synchronized void startExam() {
        if (exam == null) {
            throw new IllegalStateException("Chưa nạp đề");
        }
        if (started) {
            throw new IllegalStateException("Bài thi đã bắt đầu");
        }
        try {
            examDir = records.attach(resultsRoot, examTitle());
        } catch (IOException e) {
            // vẫn cho thi, chỉ mất file lưu
            log.accept("Không tạo được thư mục kết quả " + resultsRoot + ": " + e.getMessage());
        }
        started = true;
        startMillis = System.currentTimeMillis();
        int minutes = exam.get("durationMinutes").getAsInt();
        autoEnd = scheduler.schedule(() -> endExam("Hết giờ"), minutes, TimeUnit.MINUTES);
        log.accept("Bắt đầu thi: " + examTitle());
        for (Session s : sessions.values()) {
            trySendExam(s);
        }
    }

    public synchronized void endExam(String reason) {
        if (!started || ended) {
            return;
        }
        ended = true;
        endReason = reason;
        autoEnd.cancel(false);

        int submittedCount = 0;
        for (Session s : sessions.values()) {
            if (s.submitted) {
                submittedCount++;
            } else if (s.connected()) {
                sendEnd(s);
            }
        }
        String counts = "Đã nộp " + submittedCount + "/" + sessions.size() + " máy";
        log.accept("Kết thúc thi: " + reason + ". " + counts);
        records.record("", "", "EXAM_END", reason + ". " + counts);
        writeResults();
    }

    public synchronized void newSession() {
        if (!ended) {
            throw new IllegalStateException("Kỳ thi chưa kết thúc");
        }
        for (Session s : sessions.values()) {
            if (s.connected() && !s.submitted) {
                sendEnd(s);
            }
        }
        closeAll();
        sessions.clear();
        mlScores = new MlScores(featureIntervalSeconds);
        started = false;
        ended = false;
        endReason = "";
        startMillis = 0;
        autoEnd = null;
        examDir = null;
        // epoch tăng để điểm ML trả về muộn của kỳ cũ bị bỏ qua
        epoch++;
        records.reset();
        log.accept("Kỳ thi mới: đã xoá phiên và mẫu cũ, giữ đề \"" + examTitle() + "\"");
    }

    // ---------- Tin nhắn từ máy sinh viên (ClientHandler gọi) ----------

    synchronized Session login(String id, String name, Socket socket, PrintWriter out) {
        return login(id, name, "", "", socket, out);
    }

    synchronized Session login(String id, String name, String password, String os, Socket socket, PrintWriter out) {
        StudentList.Account account = students.find(id);
        if (students.size() > 0 && account != null && account.password().equals(password)) {
            id = account.id();          // mã SV và họ tên lấy từ danh sách
            name = account.name();
        } else if (students.size() > 0 && !id.isEmpty()) {
            return loginFailed(id, "Sai mã sinh viên hoặc mật khẩu", out);
        } else if (name.isEmpty()) {
            name = id;                  // chưa nạp danh sách: client không gửi họ tên
        }
        Session s = sessions.get(id);
        String error = loginError(id, s);
        if (error != null) {
            return loginFailed(id, error, out);
        }

        // máy cũ đã mất kết nối thì giữ nguyên session, chỉ gắn socket mới
        boolean returning = s != null;
        if (!returning) {
            s = new Session(id, name);
            sessions.put(id, s);
        }
        s.socket = socket;
        s.out = out;
        s.os = os;
        s.lastSeen = System.currentTimeMillis();

        JsonObject ok = Protocol.msg(Protocol.LOGIN_OK);
        ok.add("rules", rules);
        ok.addProperty("name", name);
        ok.addProperty("featureIntervalSeconds", featureIntervalSeconds);
        Protocol.send(out, ok);
        log.accept("Đăng nhập: " + id + " - " + s.name);
        if (returning) {
            event(s, "RECONNECT", "");
        } else {
            event(s, "LOGIN", "");
        }

        if (s.submitted) {
            // RESULT có thể đã mất khi rớt mạng nên gửi lại
            sendResult(s);
        } else {
            trySendExam(s);
        }
        return s;
    }

    private Session loginFailed(String id, String reason, PrintWriter out) {
        JsonObject fail = Protocol.msg(Protocol.LOGIN_FAIL);
        fail.addProperty("reason", reason);
        Protocol.send(out, fail);
        records.record(id, "", "LOGIN_FAIL", reason);
        return null;
    }

    /** Giám thị nạp danh sách mới (null hoặc rỗng nghĩa là bỏ danh sách, ai cũng vào được). */
    public synchronized void setStudents(StudentList list) {
        students = list == null ? StudentList.empty() : list;
        log.accept(students.size() == 0 ? "Chưa nạp danh sách sinh viên" : "Đã nạp danh sách: " + students.size() + " sinh viên");
    }

    public synchronized int studentCount() { return students.size(); }

    private String loginError(String id, Session existing) {
        if (id.isEmpty()) {
            return "Thiếu mã SV";
        }
        if (existing != null && existing.connected()) {
            return "Mã SV này đang đăng nhập ở máy khác";
        }
        if (existing == null && ended) {
            return "Bài thi đã kết thúc";
        }
        return null;
    }

    synchronized void touch(Session s) {
        if (live(s)) {
            s.lastSeen = System.currentTimeMillis();
        }
    }

    synchronized void features(Session s, JsonObject sample) {
        if (live(s)) {
            s.samples.add(sample);
        }
    }

    synchronized void precheckResult(Session s, boolean ok, String issues) {
        if (!live(s)) {
            return;
        }
        s.precheckOk = ok;
        s.precheckFailed = !ok;
        if (ok) {
            log.accept("PRECHECK " + s.id + ": đạt");
            event(s, "PRECHECK_OK", "");
        } else {
            log.accept("PRECHECK " + s.id + ": lỗi - " + issues);
            event(s, "PRECHECK_FAIL", issues);
        }
        trySendExam(s);
    }

    synchronized void violation(Session s, String kind, String detail) {
        if (!live(s)) {
            return;
        }
        s.violations++;
        s.lastViolation = kind + ": " + detail;
        s.lastViolationAt = LocalTime.now().format(HOUR_MINUTE);
        log.accept("Vi phạm " + s.id + " - " + s.lastViolation);
        event(s, "VIOLATION", s.lastViolation);
    }

    synchronized void apps(Session s, List<AppInfo> apps) {
        if (!live(s)) {
            return;
        }
        s.apps = apps;
        s.appsAt = LocalTime.now().format(HOUR_MINUTE_SECOND);
    }

    synchronized void submit(Session s, JsonObject answers) {
        if (!live(s)) {
            return;
        }
        if (!started) {
            log.accept("Bỏ qua SUBMIT của " + s.id + ": bài thi chưa bắt đầu");
            return;
        }
        // nộp lần hai không chấm lại, chỉ gửi lại RESULT cũ
        if (s.result == null) {
            s.result = ExamFile.grade(exam, answers);
            s.submitted = true;
            String score = s.result.get("score").getAsString();
            String correct = s.result.get("correct").getAsString();
            String total = s.result.get("total").getAsString();
            log.accept("Nộp bài: " + s.id + " - điểm " + score);
            event(s, "SUBMIT", "điểm " + score + " (" + correct + "/" + total + ")");
            writeResults();
        }
        if (s.connected()) {
            sendResult(s);
        }
    }

    synchronized void disconnected(Session s, Socket socket) {
        if (s == null || !live(s)) {
            return;
        }
        // socket đã bị máy vào lại thay bằng socket khác thì không được đá máy đó
        if (s.socket != socket) {
            return;
        }
        markDisconnected(s, "socket đóng");
    }

    // ---------- Watchdog và ML ----------

    synchronized void dropIdle(long timeoutMs) {
        long now = System.currentTimeMillis();
        for (Session s : sessions.values()) {
            boolean inExam = Session.ST_IN_EXAM.equals(s.status(started));
            if (inExam && now - s.lastSeen > timeoutMs) {
                // đóng socket trước vì markDisconnected sẽ xoá tham chiếu tới nó
                s.close();
                markDisconnected(s, "im lặng quá " + timeoutMs / 1000 + " giây (watchdog)");
            }
        }
    }

    synchronized JsonArray samplesForMl() {
        return MlScores.samples(sessions.values());
    }

    synchronized void applyScores(JsonObject response) {
        applyScores(response, epoch);
    }

    synchronized void applyScores(JsonObject response, int forEpoch) {
        // điểm ML của kỳ cũ thì bỏ, không áp vào máy cùng mã SV của kỳ mới
        if (forEpoch != epoch) {
            return;
        }
        mlScores.apply(response, sessions, log, this::recordAnomaly);
    }

    private void recordAnomaly(Session s, String type) {
        String detail = String.format(Locale.ROOT, "%.2f", s.anomalyScore);
        if (type.equals("ANOMALY_ON")) {
            // lúc bị gắn cờ thì kèm lý do để giám thị biết máy lạ ở đâu
            detail = MlScores.describe(s.anomalyScore, s.reasons, featureIntervalSeconds);
        }
        event(s, type, detail);
    }

    synchronized void closeAll() {
        for (Session s : sessions.values()) {
            s.close();
        }
    }

    // ---------- Dữ liệu cho giao diện giám thị ----------

    public synchronized List<Row> rows() {
        List<Row> list = new ArrayList<>();
        for (Session s : sessions.values()) {
            Double score = null;
            if (s.result != null) {
                score = s.result.get("score").getAsDouble();
            }
            list.add(new Row(s.id, s.name, s.status(started), s.violations, s.lastViolation, s.anomalyScore,
                s.anomaly, score, s.samples.size(), s.os, s.focusLost(), s.lastViolationAt));
        }
        return list;
    }

    /** App đang mở của một thí sinh, đã tô loại theo luật hiện tại; null nếu không có thí sinh đó. */
    public synchronized AppsView appsOf(String studentId) {
        Session s = sessions.get(studentId);
        if (s == null) {
            return null;
        }
        AppRules appRules = new AppRules(rules);
        List<AppLine> lines = new ArrayList<>();
        for (AppInfo a : s.apps) {
            AppKind kind = AppKind.OTHER;
            if (appRules.blacklisted(a.name())) {
                kind = AppKind.BLOCKED;
            } else if (appRules.windowAllowed(a.name())) {
                kind = AppKind.ALLOWED;
            }
            lines.add(new AppLine(a.name(), a.title(), kind));
        }
        return new AppsView(lines, s.appsAt);
    }

    public synchronized Summary summary() {
        if (exam == null) {
            return null;
        }
        String startedAt = "";
        if (started) {
            startedAt = LocalTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneId.systemDefault()).format(HOUR_MINUTE);
        }
        int questionCount = exam.getAsJsonArray("questions").size();
        return new Summary(examTitle(), questionCount, exam.get("durationMinutes").getAsInt(), startedAt);
    }

    public synchronized String examTitle() {
        if (exam == null) {
            return null;
        }
        return exam.get("title").getAsString();
    }

    public synchronized int remainingSeconds() {
        if (exam == null || ended) {
            return 0;
        }
        int total = exam.get("durationMinutes").getAsInt() * 60;
        long elapsed = 0;
        if (started) {
            elapsed = (System.currentTimeMillis() - startMillis) / 1000;
        }
        return (int) Math.max(0, total - elapsed);
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public synchronized boolean isEnded() {
        return ended;
    }

    public int featureIntervalSeconds() {
        return featureIntervalSeconds;
    }

    public synchronized int epoch() {
        return epoch;
    }

    public synchronized Path examDir() {
        return examDir;
    }

    public synchronized Path resultsRoot() {
        return resultsRoot;
    }

    public synchronized void setResultsRoot(Path path) {
        resultsRoot = path;
    }

    public List<ExamLog.Event> history(String studentId) {
        return records.history(studentId);
    }

    // ---------- Hàm nội bộ ----------

    // Điểm gửi đề duy nhất: dùng cho bấm Bắt đầu, PRECHECK đạt, vào thi muộn và vào lại sau khi rớt mạng
    private void trySendExam(Session s) {
        if (!started || !s.connected() || !s.precheckOk || s.submitted) {
            return;
        }
        if (ended) {
            // vào sau khi kết thúc: báo hết giờ để client tự nộp
            sendEnd(s);
            return;
        }
        int remaining = remainingSeconds();
        JsonObject m = Protocol.msg(Protocol.EXAM_START);
        m.addProperty("title", examTitle());
        m.addProperty("remainingSeconds", remaining);
        m.add("questions", clientQuestions);
        Protocol.send(s.out, m);
        event(s, "EXAM_SENT", "còn " + remaining + " giây");
        // thời gian im lặng chỉ tính từ lúc máy nhận đề, không tính từ lúc chờ trước giờ thi
        s.lastSeen = System.currentTimeMillis();
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

    private void markDisconnected(Session s, String why) {
        s.socket = null;
        s.out = null;
        log.accept("Mất kết nối: " + s.id);
        event(s, "DISCONNECT", why);
    }

    private boolean live(Session s) {
        // session của kỳ trước (đã bị xoá khi làm mới) còn nhận tin muộn thì phải bỏ qua
        return sessions.get(s.id) == s;
    }

    private void event(Session s, String type, String detail) {
        records.record(s.id, s.name, type, detail);
    }

    private void writeResults() {
        if (examDir == null) {
            return;
        }
        try {
            ExamFile.writeResults(examDir.resolve("results.csv"), sessions.values(), started);
        } catch (IOException e) {
            log.accept("Không ghi được results.csv: " + e.getMessage());
        }
    }
}
