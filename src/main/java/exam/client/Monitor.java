// Luật giám sát ở client: baseline, phát hiện vi phạm (APP có cửa sổ/USB/NET), đếm focus/đáp án và gom feature mỗi 30 giây (Người 2).
package exam.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.client.WindowsProbe.Proc;
import exam.client.WindowsProbe.Win;
import exam.common.Protocol;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class Monitor {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final boolean windows = WindowsProbe.isWindows();
    private final Runnable scanTask;
    private final long scanMs, featuresMs;
    private ScheduledExecutorService scanner, heartbeat;        // hai luồng riêng: quét chậm không được làm trễ FEATURES
    private volatile Consumer<JsonObject> sender;

    // Luật và baseline lấy ở lần PRECHECK cuối cùng
    private volatile JsonObject rules = new JsonObject();
    private Set<String> baselineProcesses = new HashSet<>(), baselineRoots = new HashSet<>(),
        baselineIps = new HashSet<>();
    private AppRules appRules = new AppRules(new JsonObject());
    private Function<Collection<Integer>, List<Win>> windowLookup = WindowsProbe::windowsOf;
    private Map<String, String> blockedIpToDomain = new HashMap<>();
    private final Set<String> sentViolations = new HashSet<>();   // (kind, detail) chỉ gửi một lần cả buổi
    private final Set<String> flaggedWindowApps = new HashSet<>();   // mỗi app có cửa sổ chỉ báo một lần dù tiêu đề đổi
    /** pid tiến trình mới -> số lần còn được hỏi cửa sổ (cửa sổ có thể hiện chậm hơn tiến trình vài giây). */
    private final Map<Integer, Integer> watching = new HashMap<>();
    private static final int WINDOW_CHECKS = 10;

    // Bộ đếm của cửa sổ 30 giây hiện tại
    private int window = 1, focusLostCount, violationsInWindow, answers;
    private final Set<String> newProcesses = new HashSet<>(), newHosts = new HashSet<>();
    private long focusLostMillis;
    private long lostSince = -1;                                  // -1 = đang ở trong cửa sổ thi

    public Monitor() { this(null, 3000, 30_000); }
    /** Cho test: {@code scanTask} thay phép quét thật (null = quét tiến trình/USB/mạng như bình thường). */
    public Monitor(Runnable scanTask, long scanMs, long featuresMs) {
        this.scanTask = scanTask != null ? scanTask : this::scan;
        this.scanMs = scanMs; this.featuresMs = featuresMs;
    }

    /** Danh sách vấn đề (rỗng = đạt). Chạy lệnh hệ thống nên phải gọi ngoài luồng giao diện. */
    public List<String> precheck(JsonObject rules) {
        if (!windows) {
            System.err.println("Cảnh báo: không phải Windows, bỏ qua PRECHECK và quét tiến trình/mạng/USB");
            return new ArrayList<>();
        }
        List<Proc> procs = Debug.timed("PRECHECK tasklist", WindowsProbe::processList);
        List<Win> wins = Debug.timed("PRECHECK app có cửa sổ", WindowsProbe::windowedApps);
        List<String> issues = precheckFrom(rules, procs, wins, WindowsProbe.removableDrives());
        // Luôn lưu baseline: lần PRECHECK đạt cuối cùng là mốc so sánh trong giờ thi
        setBaseline(names(procs), WindowsProbe.roots(), WindowsProbe.remoteIps(), rules);
        return issues;
    }

    /** Phần luật của PRECHECK, không chạy lệnh nào nên test được bằng dữ liệu mẫu. */
    public List<String> precheckFrom(JsonObject rules, List<Proc> procs, List<Win> wins, Set<String> drives) {
        List<String> issues = new ArrayList<>(new AppRules(rules).appIssues(procs.stream().map(Proc::name).toList(), wins));
        for (String drive : drives) {
            issues.add("Đang cắm USB: " + drive);
        }
        return issues;
    }

    private static Set<String> names(List<Proc> procs) {
        Set<String> set = new HashSet<>();
        for (Proc p : procs) { set.add(p.name()); }
        return set;
    }

    private synchronized void setBaseline(Set<String> procs, Set<String> roots, Set<String> ips, JsonObject rules) {
        Set<String> normalized = new HashSet<>();
        for (String p : procs) { normalized.add(AppRules.norm(p)); }
        this.baselineProcesses = normalized;
        this.baselineRoots = roots;
        this.baselineIps = ips;
        this.rules = rules;
        this.appRules = new AppRules(rules);
        this.watching.clear();
    }

    /** Cho test thay lệnh PowerShell hỏi cửa sổ theo pid. */
    public void setWindowLookupForTest(Function<Collection<Integer>, List<Win>> lookup) { this.windowLookup = lookup; }

    /** Cho test đặt baseline trực tiếp, không cần chạy lệnh Windows. */
    public synchronized void setBaselineForTest(Set<String> processes, Set<String> roots, Set<String> remoteIps,
            JsonObject rules, Map<String, String> blockedIpToDomain, Consumer<JsonObject> sender) {
        setBaseline(processes, roots, remoteIps, rules);
        this.blockedIpToDomain = blockedIpToDomain;
        this.sender = sender;
    }

    public void start(Consumer<JsonObject> sender) {
        this.sender = sender;
        scanner = newThread("monitor-scan");
        heartbeat = newThread("monitor-features");
        scanner.execute(() -> safely(this::resolveBlockedDomains));        // DNS có thể chậm nên không chạy ở luồng giao diện
        scanner.scheduleWithFixedDelay(() -> safely(scanTask), scanMs, scanMs, TimeUnit.MILLISECONDS);
        heartbeat.scheduleAtFixedRate(() -> safely(this::sendFeatures), featuresMs, featuresMs, TimeUnit.MILLISECONDS);
    }

    private static ScheduledExecutorService newThread(String name) {
        return Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; });
    }

    public void stop() {
        for (ScheduledExecutorService e : new ScheduledExecutorService[] {scanner, heartbeat}) {
            if (e != null) { e.shutdownNow(); }
        }
    }

    /** Một việc định kỳ ném exception sẽ bị executor hủy lặng lẽ, nên bắt lại và báo ra stderr. */
    private void safely(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            System.err.println("Lỗi giám sát: " + e);
        }
    }

    private void resolveBlockedDomains() {
        Map<String, String> table = WindowsProbe.resolve(lowerSet(rules, "blockedDomains"));
        synchronized (this) {
            blockedIpToDomain = table;
        }
    }

    private void scan() {
        if (!windows) { return; }
        processSnapshot(Debug.timed("quét tasklist", WindowsProbe::processList), Debug.timed("quét ổ đĩa", WindowsProbe::roots),
            Debug.timed("quét netstat", WindowsProbe::remoteIps));
    }

    /** Dành cho test cũ chỉ có tên tiến trình, không có pid nên không hỏi cửa sổ. */
    public void processSnapshot(Set<String> processes, Set<String> roots, Set<String> remoteIps) {
        processSnapshot(processes.stream().map(n -> new Proc(n, -1)).toList(), roots, remoteIps);
    }

    /**
     * Áp dụng một lần quét. Tiến trình nền không bao giờ là vi phạm (chỉ đếm vào newProcesses); chỉ app bị cấm hoặc
     * app mới có cửa sổ không được phép mới bị báo. Hỏi PowerShell chậm nên làm ngoài khối synchronized.
     */
    public void processSnapshot(List<Proc> procs, Set<String> roots, Set<String> remoteIps) {
        List<Integer> toCheck;
        synchronized (this) {
            toCheck = applySnapshot(procs, roots, remoteIps);
        }
        if (toCheck.isEmpty()) { return; }
        List<Win> found = Debug.timed("hỏi cửa sổ của " + toCheck.size() + " tiến trình mới", () -> windowLookup.apply(toCheck));
        synchronized (this) {
            for (Win w : found) {
                watching.put(w.pid(), 0);                          // đã thấy cửa sổ, không hỏi lại
                if (!appRules.skip(w.name()) && !appRules.windowAllowed(w.name()) && flaggedWindowApps.add(AppRules.norm(w.name()))) {
                    violate("APP", AppRules.describe(w));
                }
            }
        }
    }

    /** Trả về các pid mới cần hỏi xem có cửa sổ không. */
    private List<Integer> applySnapshot(List<Proc> procs, Set<String> roots, Set<String> remoteIps) {
        Set<Integer> alive = new HashSet<>();
        for (Proc p : procs) {
            String n = AppRules.norm(p.name());
            if (appRules.skip(n) || baselineProcesses.contains(n)) { continue; }
            newProcesses.add(n);
            if (appRules.blacklisted(n)) {
                violate("APP", p.name().toLowerCase());
            } else if (p.pid() >= 0) {
                alive.add(p.pid());
                watching.putIfAbsent(p.pid(), WINDOW_CHECKS);
            }
        }
        for (String root : roots) {
            if (!baselineRoots.contains(root)) { violate("USB", root); }
        }
        for (String ip : remoteIps) {
            String domain = blockedIpToDomain.get(ip);
            if (domain != null) { violate("NET", domain + " (" + ip + ")"); }
            if (!baselineIps.contains(ip)) { newHosts.add(ip); }
        }
        watching.keySet().retainAll(alive);
        List<Integer> toCheck = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : watching.entrySet()) {
            if (e.getValue() > 0) {
                e.setValue(e.getValue() - 1);
                toCheck.add(e.getKey());
            }
        }
        return toCheck;
    }

    private void violate(String kind, String detail) {
        if (!sentViolations.add(kind + "|" + detail)) { return; }
        violationsInWindow++;
        JsonObject m = Protocol.msg(Protocol.VIOLATION);
        m.addProperty("kind", kind);
        m.addProperty("detail", detail);
        m.addProperty("time", LocalTime.now().format(TIME));
        if (sender != null) { sender.accept(m); }
    }

    private void sendFeatures() {
        JsonObject m = buildFeatures();
        Debug.log("gửi FEATURES " + m);
        if (sender != null) { sender.accept(m); }
    }

    /** Tạo tin FEATURES của cửa sổ hiện tại rồi reset bộ đếm. */
    public synchronized JsonObject buildFeatures() {
        long now = System.currentTimeMillis();
        if (lostSince >= 0) {                  // đang rời cửa sổ: chốt phần đến giờ, phần sau tính cho cửa sổ kế
            focusLostMillis += now - lostSince;
            lostSince = now;
        }
        int[] v = {newProcesses.size(), focusLostCount, (int) Math.round(focusLostMillis / 1000.0),
            newHosts.size(), violationsInWindow, answers};
        JsonArray values = new JsonArray();
        for (int x : v) {
            values.add(x);
        }
        JsonObject m = Protocol.msg(Protocol.FEATURES);
        m.addProperty("window", window++);
        m.add("values", values);
        newProcesses.clear();
        newHosts.clear();
        focusLostCount = violationsInWindow = answers = 0;
        focusLostMillis = 0;
        return m;
    }

    public synchronized void recordAnswer() { answers++; }

    public synchronized void focusLost() {
        if (lostSince < 0) {
            lostSince = System.currentTimeMillis();
            focusLostCount++;
        }
    }

    public synchronized void focusGained() {
        if (lostSince >= 0) {
            focusLostMillis += System.currentTimeMillis() - lostSince;
            lostSince = -1;
        }
    }

    private static Set<String> lowerSet(JsonObject rules, String key) {
        Set<String> set = new LinkedHashSet<>();
        if (rules.get(key) instanceof JsonArray arr) {
            for (JsonElement e : arr) {
                set.add(e.getAsString().toLowerCase());
            }
        }
        return set;
    }
}
