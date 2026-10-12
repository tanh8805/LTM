// Luật giám sát ở client: baseline, phát hiện vi phạm (APP/USB/NET), đếm focus/đáp án và gom feature mỗi 30 giây (Người 2).
package exam.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

public class Monitor {
    /**
     * Tiến trình do chính client sinh ra hoặc Windows tự bật/tắt liên tục. tasklist liệt kê cả chính nó,
     * nên thiếu danh sách này thì máy nào cũng bị báo vi phạm.
     */
    private static final Set<String> SYSTEM_PROCESSES = Set.of("tasklist.exe", "netstat.exe", "powershell.exe",
        "conhost.exe", "cmd.exe", "java.exe", "javaw.exe", "backgroundtaskhost.exe", "runtimebroker.exe",
        "searchprotocolhost.exe", "searchfilterhost.exe", "wmiprvse.exe", "dllhost.exe", "audiodg.exe",
        "taskhostw.exe", "smartscreen.exe", "wermgr.exe", "consent.exe", "svchost.exe");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final boolean windows = WindowsProbe.isWindows();
    private ScheduledExecutorService scheduler;
    private volatile Consumer<JsonObject> sender;

    // Luật và baseline lấy ở lần PRECHECK cuối cùng
    private volatile JsonObject rules = new JsonObject();
    private Set<String> baselineProcesses = new HashSet<>(), baselineRoots = new HashSet<>(),
        baselineIps = new HashSet<>();
    private Set<String> whitelist = new HashSet<>();
    private Map<String, String> blockedIpToDomain = new HashMap<>();
    private final Set<String> sentViolations = new HashSet<>();   // (kind, detail) chỉ gửi một lần cả buổi

    // Bộ đếm của cửa sổ 30 giây hiện tại
    private int window = 1, focusLostCount, violationsInWindow, answers;
    private final Set<String> newProcesses = new HashSet<>(), newHosts = new HashSet<>();
    private long focusLostMillis;
    private long lostSince = -1;                                  // -1 = đang ở trong cửa sổ thi

    public Monitor() {}

    /** Danh sách vấn đề (rỗng = đạt). Chạy lệnh hệ thống nên phải gọi ngoài luồng giao diện. */
    public List<String> precheck(JsonObject rules) {
        List<String> issues = new ArrayList<>();
        if (!windows) {
            System.err.println("Cảnh báo: không phải Windows, bỏ qua PRECHECK và quét tiến trình/mạng/USB");
            return issues;
        }
        Set<String> procs = WindowsProbe.processes();
        for (String app : lowerSet(rules, "blacklistApps")) {
            if (procs.contains(app)) { issues.add("Đang mở ứng dụng bị cấm: " + app); }
        }
        for (String drive : WindowsProbe.removableDrives()) {
            issues.add("Đang cắm USB: " + drive);
        }
        // Luôn lưu baseline: lần PRECHECK đạt cuối cùng là mốc so sánh trong giờ thi
        setBaseline(procs, WindowsProbe.roots(), WindowsProbe.remoteIps(), rules);
        return issues;
    }

    private synchronized void setBaseline(Set<String> procs, Set<String> roots, Set<String> ips, JsonObject rules) {
        this.baselineProcesses = procs;
        this.baselineRoots = roots;
        this.baselineIps = ips;
        this.rules = rules;
        this.whitelist = lowerSet(rules, "whitelistApps");
    }

    /** Cho test đặt baseline trực tiếp, không cần chạy lệnh Windows. */
    public synchronized void setBaselineForTest(Set<String> processes, Set<String> roots, Set<String> remoteIps,
            JsonObject rules, Map<String, String> blockedIpToDomain, Consumer<JsonObject> sender) {
        setBaseline(processes, roots, remoteIps, rules);
        this.blockedIpToDomain = blockedIpToDomain;
        this.sender = sender;
    }

    public void start(Consumer<JsonObject> sender) {
        this.sender = sender;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monitor");
            t.setDaemon(true);
            return t;
        });
        scheduler.execute(() -> safely(this::resolveBlockedDomains));      // DNS có thể chậm nên không chạy ở luồng giao diện
        scheduler.scheduleWithFixedDelay(() -> safely(this::scan), 3, 3, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> safely(this::sendFeatures), 30, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) { scheduler.shutdownNow(); }
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
        processSnapshot(WindowsProbe.processes(), WindowsProbe.roots(), WindowsProbe.remoteIps());
    }

    /** Áp dụng một lần quét: cập nhật bộ đếm và gửi VIOLATION cho điều mới. */
    public synchronized void processSnapshot(Set<String> processes, Set<String> roots, Set<String> remoteIps) {
        for (String p : processes) {
            if (SYSTEM_PROCESSES.contains(p) || baselineProcesses.contains(p)) { continue; }
            newProcesses.add(p);
            if (!whitelist.contains(p)) { violate("APP", p); }
        }
        for (String root : roots) {
            if (!baselineRoots.contains(root)) { violate("USB", root); }
        }
        for (String ip : remoteIps) {
            String domain = blockedIpToDomain.get(ip);
            if (domain != null) { violate("NET", domain + " (" + ip + ")"); }
            if (!baselineIps.contains(ip)) { newHosts.add(ip); }
        }
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
        Consumer<JsonObject> s = sender;
        if (s != null) { s.accept(m); }
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
