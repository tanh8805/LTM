// Luật về ứng dụng: so khớp tên (không phân biệt hoa thường, bỏ .exe) và kiểm tra app có cửa sổ lúc PRECHECK (Người 2).
package exam.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.client.WindowsProbe.Win;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AppRules {
    /**
     * Tiến trình do chính client sinh ra hoặc Windows tự bật/tắt liên tục. tasklist liệt kê cả chính nó,
     * nên thiếu danh sách này thì máy nào cũng bị báo vi phạm. Luôn bỏ qua, không đòi cấu hình.
     */
    static final Set<String> SYSTEM_PROCESSES = Set.of("tasklist", "netstat", "powershell", "conhost", "cmd", "java",
        "javaw", "backgroundtaskhost", "runtimebroker", "searchprotocolhost", "searchfilterhost", "wmiprvse", "dllhost",
        "audiodg", "taskhostw", "smartscreen", "wermgr", "consent", "svchost");

    private final Set<String> blacklist, whitelist, allowedWindow, ignore;

    public AppRules(JsonObject rules) {
        blacklist = names(rules, "blacklistApps");
        whitelist = names(rules, "whitelistApps");
        allowedWindow = names(rules, "allowedWindowApps");
        ignore = names(rules, "ignoreProcesses");
    }

    /** "Antigravity.EXE" và "antigravity" cùng thành "antigravity". */
    public static String norm(String name) {
        String n = name.trim().toLowerCase();
        return n.endsWith(".exe") ? n.substring(0, n.length() - 4) : n;
    }

    /** Tiến trình bị bỏ qua hoàn toàn (hệ thống hoặc nằm trong ignoreProcesses). */
    public boolean skip(String name) {
        String n = norm(name);
        return SYSTEM_PROCESSES.contains(n) || ignore.contains(n);
    }

    public boolean blacklisted(String name) { return blacklist.contains(norm(name)); }

    /** Được phép có cửa sổ: nằm trong whitelistApps hoặc allowedWindowApps. */
    public boolean windowAllowed(String name) {
        String n = norm(name);
        return whitelist.contains(n) || allowedWindow.contains(n);
    }

    /** Vấn đề về ứng dụng lúc PRECHECK: app bị cấm đang chạy, rồi app có cửa sổ không được phép (mỗi tên một lần). */
    public List<String> appIssues(Collection<String> processNames, Collection<Win> windows) {
        List<String> issues = new ArrayList<>();
        Set<String> reported = new LinkedHashSet<>();
        for (String p : processNames) {
            if (!skip(p) && blacklisted(p) && reported.add(norm(p))) { issues.add("Đang mở ứng dụng bị cấm: " + p); }
        }
        for (Win w : windows) {
            if (!skip(w.name()) && !windowAllowed(w.name()) && reported.add(norm(w.name()))) {
                issues.add("Hãy đóng ứng dụng: " + describe(w));
            }
        }
        return issues;
    }

    static String describe(Win w) { return w.title().isEmpty() ? w.name() : w.name() + " (" + w.title() + ")"; }

    private static Set<String> names(JsonObject rules, String key) {
        Set<String> set = new LinkedHashSet<>();
        if (rules.get(key) instanceof JsonArray arr) {
            for (JsonElement e : arr) { set.add(norm(e.getAsString())); }
        }
        return set;
    }
}
