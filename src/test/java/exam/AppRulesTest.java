// Test luật ứng dụng "chỉ xét app có cửa sổ": dùng output mẫu và bộ tra cứu cửa sổ giả nên chạy được trên Linux.
package exam;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import exam.client.AppRules;
import exam.client.Monitor;
import exam.client.WindowsProbe.Proc;
import exam.client.WindowsProbe.Win;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppRulesTest {
    static final JsonObject RULES = JsonParser.parseString("{\"blacklistApps\":[\"chrome.exe\",\"Zalo\"],"
        + "\"whitelistApps\":[\"calc.exe\",\"notepad.exe\"],"
        + "\"allowedWindowApps\":[\"java\",\"javaw\",\"WindowsTerminal\",\"Explorer.exe\"],"
        + "\"ignoreProcesses\":[\"OneDrive\",\"rtkauduservice64.exe\"],\"blockedDomains\":[]}").getAsJsonObject();

    Monitor monitor;
    List<JsonObject> sent;
    Map<Integer, Win> windows;       // cửa sổ mà "PowerShell giả" sẽ trả về theo pid
    AtomicInteger lookups;

    @BeforeEach
    void setUp() {
        monitor = new Monitor();
        sent = new ArrayList<>();
        windows = new HashMap<>();
        lookups = new AtomicInteger();
        monitor.setBaselineForTest(Set.of("explorer.exe"), Set.of("C:\\"), Set.of(), RULES, Map.of(), sent::add);
        monitor.setWindowLookupForTest(pids -> {
            lookups.incrementAndGet();
            return pids.stream().filter(windows::containsKey).map(windows::get).toList();
        });
    }

    private void snapshot(Proc... procs) {
        monitor.processSnapshot(List.of(procs), Set.of("C:\\"), Set.of());
    }

    private int newProcessFeature() {
        return monitor.buildFeatures().getAsJsonArray("values").get(0).getAsInt();
    }

    @Test
    void backgroundProcessIsNotViolation() {
        snapshot(new Proc("explorer.exe", 4), new Proc("somehelper.exe", 100), new Proc("micservice.exe", 101));
        assertTrue(sent.isEmpty());
        assertEquals(2, newProcessFeature());          // chỉ đếm vào newProcesses
    }

    @Test
    void newWindowedAppIsViolation() {
        windows.put(200, new Win("figma", 200, "Figma - Thiết kế"));
        snapshot(new Proc("figma.exe", 200));
        assertEquals(1, sent.size());
        assertEquals("APP", sent.get(0).get("kind").getAsString());
        assertEquals("figma (Figma - Thiết kế)", sent.get(0).get("detail").getAsString());
    }

    @Test
    void newWindowedAllowedAppIsNotViolation() {
        windows.put(1, new Win("WindowsTerminal", 1, "Windows PowerShell"));
        windows.put(2, new Win("notepad", 2, "Không có tiêu đề - Notepad"));
        snapshot(new Proc("WindowsTerminal.exe", 1), new Proc("notepad.exe", 2));
        assertTrue(sent.isEmpty());
        assertEquals(2, newProcessFeature());
    }

    @Test
    void windowAppearingLaterIsStillCaught() {
        snapshot(new Proc("slowapp.exe", 300));                  // chưa có cửa sổ
        assertTrue(sent.isEmpty());
        windows.put(300, new Win("slowapp", 300, "Slow"));
        snapshot(new Proc("slowapp.exe", 300));                  // lần quét sau cửa sổ mới hiện
        assertEquals(1, sent.size());
    }

    @Test
    void backgroundProcessIsNotLookedUpForever() {
        for (int i = 0; i < 40; i++) {
            snapshot(new Proc("daemon.exe", 400));
        }
        assertTrue(lookups.get() <= 12, "gọi PowerShell " + lookups.get() + " lần cho một tiến trình nền");
    }

    @Test
    void sameWindowedAppFlaggedOnceEvenIfTitleChanges() {
        windows.put(500, new Win("editor", 500, "a.txt - Editor"));
        snapshot(new Proc("editor.exe", 500));
        windows.put(500, new Win("editor", 500, "b.txt - Editor"));
        windows.put(501, new Win("editor", 501, "c.txt - Editor"));
        snapshot(new Proc("editor.exe", 500), new Proc("editor.exe", 501));
        assertEquals(1, sent.size());
    }

    @Test
    void newBlacklistedProcessIsViolationEvenWithoutWindow() {
        snapshot(new Proc("Zalo.exe", 600));
        assertEquals(1, sent.size());
        assertEquals("Zalo.exe".toLowerCase(), sent.get(0).get("detail").getAsString());
    }

    @Test
    void ignoredProcessNeverCounts() {
        windows.put(700, new Win("onedrive", 700, "OneDrive"));      // dù có cửa sổ cũng bị bỏ qua hoàn toàn
        snapshot(new Proc("OneDrive.exe", 700), new Proc("RtkAudUService64.exe", 701), new Proc("conhost.exe", 702));
        assertTrue(sent.isEmpty());
        assertEquals(0, newProcessFeature());
        assertEquals(0, lookups.get());
    }

    @Test
    void namesMatchWithOrWithoutExe() {
        assertEquals(AppRules.norm("Antigravity.exe"), AppRules.norm("antigravity"));
        assertEquals("antigravity", AppRules.norm(" Antigravity.EXE "));
        AppRules rules = new AppRules(RULES);
        assertTrue(rules.blacklisted("zalo.exe"));                   // luật ghi "Zalo", tasklist ghi "zalo.exe"
        assertTrue(rules.blacklisted("chrome"));                     // luật ghi "chrome.exe", PowerShell ghi "chrome"
        assertTrue(rules.windowAllowed("explorer"));
        assertTrue(rules.windowAllowed("windowsterminal.exe"));
        assertTrue(rules.skip("onedrive.exe"));
        assertTrue(rules.skip("rtkauduservice64"));
    }

    @Test
    void precheckFailsForBlacklistedApp() {
        List<String> issues = monitor.precheckFrom(RULES, List.of(new Proc("explorer.exe", 4), new Proc("chrome.exe", 9)),
            List.of(new Win("chrome", 9, "Google Chrome")), Set.of());
        assertEquals(List.of("Đang mở ứng dụng bị cấm: chrome.exe"), issues);   // không báo trùng thêm "Hãy đóng"
    }

    @Test
    void precheckFailsForUnknownWindowedApp() {
        List<String> issues = monitor.precheckFrom(RULES, List.of(new Proc("antigravity.exe", 9)),
            List.of(new Win("antigravity", 9, "Welcome - Antigravity")), Set.of());
        assertEquals(List.of("Hãy đóng ứng dụng: antigravity (Welcome - Antigravity)"), issues);
    }

    @Test
    void precheckPassesForAllowedWindows() {
        List<String> issues = monitor.precheckFrom(RULES,
            List.of(new Proc("explorer.exe", 4), new Proc("java.exe", 5), new Proc("WindowsTerminal.exe", 6)),
            List.of(new Win("explorer", 4, "Tệp"), new Win("java", 5, "Thi trắc nghiệm"),
                new Win("WindowsTerminal", 6, "Windows PowerShell")), Set.of());
        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void precheckIgnoresBackgroundProcessesAndIgnoredApps() {
        List<String> issues = monitor.precheckFrom(RULES, List.of(new Proc("micservice.exe", 8)),
            List.of(new Win("onedrive", 7, "OneDrive")), Set.of());
        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void precheckReportsUsb() {
        assertEquals(List.of("Đang cắm USB: E:"), monitor.precheckFrom(RULES, List.of(), List.of(), Set.of("E:")));
    }

    // ----- luật mặc định trong config/rules.json -----

    private static JsonObject defaultRules() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("config/rules.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    @Test
    void defaultRulesIgnoreCommonBackgroundNoise() throws Exception {
        AppRules rules = new AppRules(defaultRules());
        for (String name : List.of("RtkAudUService64.exe", "RtkNGUI64.exe", "NahimicService.exe", "NVDisplay.Container.exe",
                "nvcontainer.exe", "igfxEM.exe", "OneDrive.exe", "FileCoAuth.exe", "MicrosoftEdgeUpdate.exe",
                "GoogleUpdate.exe", "PhoneExperienceHost.exe", "YourPhone.exe", "CrossDeviceService.exe",
                "SecurityHealthSystray.exe", "msedgewebview2.exe")) {
            assertTrue(rules.skip(name), name + " phải nằm trong ignoreProcesses");
        }
    }

    @Test
    void defaultRulesAllowShellAndConsoleWindows() throws Exception {
        AppRules rules = new AppRules(defaultRules());
        for (String name : List.of("java", "javaw", "OpenConsole",
                "conhost", "explorer", "ApplicationFrameHost", "TextInputHost", "SearchHost",
                "StartMenuExperienceHost", "ShellExperienceHost", "LockApp")) {
            assertTrue(rules.windowAllowed(name) || rules.skip(name), name + " phải được phép có cửa sổ");
        }
    }

    private List<String> precheckWithTerminalWindow(String name, String title) throws Exception {
        return monitor.precheckFrom(defaultRules(), List.of(new Proc("explorer.exe", 4), new Proc(name + ".exe", 6)),
            List.of(new Win(name, 6, title)), Set.of());
    }

    @Test
    void terminalWindowFailsPrecheck() throws Exception {
        for (String name : List.of("cmd", "powershell", "pwsh", "WindowsTerminal")) {
            List<String> issues = precheckWithTerminalWindow(name, "Terminal");
            assertEquals(1, issues.size(), name + ": " + issues);
            assertTrue(issues.get(0).toLowerCase().contains(name.toLowerCase()), issues.get(0));
        }
    }

    @Test
    void terminalOpenedDuringExamIsViolation() throws Exception {
        monitor.setBaselineForTest(Set.of("explorer.exe"), Set.of("C:\\"), Set.of(), defaultRules(), Map.of(), sent::add);
        windows.put(700, new Win("powershell", 700, "Windows PowerShell"));
        snapshot(new Proc("powershell.exe", 700));
        assertEquals(1, sent.size());
        assertEquals("APP", sent.get(0).get("kind").getAsString());
    }

    @Test
    void gitProcessIsViolationEvenWithoutWindow() throws Exception {
        monitor.setBaselineForTest(Set.of("explorer.exe"), Set.of("C:\\"), Set.of(), defaultRules(), Map.of(), sent::add);
        snapshot(new Proc("git.exe", 800), new Proc("git-remote-https.exe", 801), new Proc("curl.exe", 802));
        assertEquals(3, sent.size());
        assertEquals("git.exe", sent.get(0).get("detail").getAsString());
    }

    @Test
    void allowTerminalFlagSkipsTerminal() throws Exception {
        System.setProperty("exam.allowTerminal", "true");
        try {
            assertEquals(List.of(), precheckWithTerminalWindow("powershell", "Windows PowerShell"));
            assertEquals(List.of(), precheckWithTerminalWindow("WindowsTerminal", "Terminal"));
        } finally {
            System.clearProperty("exam.allowTerminal");
        }
        assertEquals(1, precheckWithTerminalWindow("powershell", "Windows PowerShell").size(), "Cờ tắt thì lại bị bắt");
    }

    @Test
    void defaultRulesBlacklistTransferToolsAndBlockCodeHosts() throws Exception {
        AppRules rules = new AppRules(defaultRules());
        for (String name : List.of("git", "git-remote-https", "gh", "curl", "wget", "ssh", "scp", "sftp", "ftp",
                "bitsadmin", "winscp", "filezilla")) {
            assertTrue(rules.blacklisted(name + ".exe"), name + " phải bị cấm");
        }
        List<String> have = new ArrayList<>();
        defaultRules().getAsJsonArray("blockedDomains").forEach(e -> have.add(e.getAsString()));
        for (String d : List.of("github.com", "api.github.com", "codeload.github.com", "raw.githubusercontent.com",
                "objects.githubusercontent.com", "gitlab.com", "bitbucket.org", "pastebin.com")) {
            assertTrue(have.contains(d), d);
        }
    }

    @Test
    void defaultRulesBlacklistAiApps() throws Exception {
        AppRules rules = new AppRules(defaultRules());
        for (String name : List.of("Antigravity.exe", "Cursor.exe", "Windsurf.exe", "Code.exe", "ChatGPT.exe",
                "Claude.exe", "Copilot.exe", "ms-copilot.exe", "Perplexity.exe", "Gemini.exe")) {
            assertTrue(rules.blacklisted(name), name + " phải bị cấm");
        }
        assertTrue(rules.blacklisted("chrome.exe"), "mục cũ có đuôi .exe vẫn hoạt động");
    }

    @Test
    void defaultRulesBlockAiDomains() throws Exception {
        JsonArray domains = defaultRules().getAsJsonArray("blockedDomains");
        List<String> have = new ArrayList<>();
        domains.forEach(e -> have.add(e.getAsString()));
        for (String d : List.of("aistudio.google.com", "generativelanguage.googleapis.com", "api.openai.com",
                "api.anthropic.com", "cursor.sh", "api2.cursor.sh", "windsurf.com", "codeium.com", "perplexity.ai",
                "www.perplexity.ai", "chatgpt.com", "claude.ai")) {
            assertTrue(have.contains(d), d);
        }
    }

    @Test
    void antigravityOpenedBeforeLoginFailsPrecheckWithDefaultRules() throws Exception {
        List<String> issues = monitor.precheckFrom(defaultRules(), List.of(new Proc("Antigravity.exe", 9)),
            List.of(new Win("antigravity", 9, "Antigravity")), Set.of());
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).toLowerCase().contains("antigravity"), issues.get(0));
    }

    @Test
    void antigravityOpenedDuringExamIsViolationWithDefaultRules() throws Exception {
        monitor.setBaselineForTest(Set.of("explorer.exe"), Set.of("C:\\"), Set.of(), defaultRules(), Map.of(), sent::add);
        snapshot(new Proc("Antigravity.exe", 900));
        assertEquals(1, sent.size());
        assertEquals("APP", sent.get(0).get("kind").getAsString());
    }
}
