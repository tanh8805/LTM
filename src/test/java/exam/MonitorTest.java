// Test Monitor trên Linux: tách phần xử lý (processSnapshot) khỏi phần chạy lệnh Windows nên không cần Windows.
package exam;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import exam.client.Monitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitorTest {
    static final JsonObject RULES = JsonParser.parseString("{\"blacklistApps\":[\"chrome.exe\",\"zalo.exe\"],"
        + "\"whitelistApps\":[\"calc.exe\",\"calculatorapp.exe\",\"notepad.exe\"],\"blockedDomains\":[\"chatgpt.com\"]}")
        .getAsJsonObject();

    Monitor monitor;
    List<JsonObject> sent;

    @BeforeEach
    void setUp() {
        monitor = new Monitor();
        sent = new ArrayList<>();
        monitor.setBaselineForTest(Set.of("explorer.exe"), Set.of("C:\\"), Set.of("10.0.0.5"),
            RULES, Map.of("104.18.1.1", "chatgpt.com"), sent::add);
    }

    private void snapshot(Set<String> procs, Set<String> roots, Set<String> ips) {
        monitor.processSnapshot(procs, roots, ips);
    }

    private int[] values(JsonObject features) {
        JsonArray arr = features.getAsJsonArray("values");
        int[] v = new int[arr.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = arr.get(i).getAsInt();
        }
        return v;
    }

    @Test
    void resetForNewRoundForgetsOldViolationsAndCounters() {
        snapshot(Set.of("explorer.exe", "chrome.exe"), Set.of("C:\\"), Set.of());
        monitor.recordAnswer();
        assertEquals(1, sent.size());
        monitor.reset();
        sent.clear();
        snapshot(Set.of("explorer.exe", "chrome.exe"), Set.of("C:\\"), Set.of());
        assertEquals(1, sent.size(), "Lượt mới phải báo lại được cùng một vi phạm");
        JsonObject f = monitor.buildFeatures();
        assertEquals(1, f.get("window").getAsInt(), "Cửa sổ đếm lại từ 1");
        assertEquals(0, values(f)[5], "Số câu trả lời của lượt trước không được cộng dồn");
    }

    @Test
    void newBlacklistedProcessIsViolation() {
        snapshot(Set.of("explorer.exe", "chrome.exe"), Set.of("C:\\"), Set.of());
        assertEquals(1, sent.size());
        assertEquals("VIOLATION", sent.get(0).get("type").getAsString());
        assertEquals("APP", sent.get(0).get("kind").getAsString());
        assertEquals("chrome.exe", sent.get(0).get("detail").getAsString());
    }

    @Test
    void whitelistedProcessIsNotViolationButCountsAsNew() {
        snapshot(Set.of("explorer.exe", "calculatorapp.exe"), Set.of("C:\\"), Set.of());
        assertTrue(sent.isEmpty());
        assertEquals(1, values(monitor.buildFeatures())[0]);
    }

    @Test
    void systemProcessesNeverCount() {
        snapshot(Set.of("explorer.exe", "tasklist.exe", "conhost.exe", "runtimebroker.exe"), Set.of("C:\\"), Set.of());
        assertTrue(sent.isEmpty());
        assertEquals(0, values(monitor.buildFeatures())[0]);
    }

    @Test
    void newDriveIsUsbViolation() {
        snapshot(Set.of("explorer.exe"), Set.of("C:\\", "E:\\"), Set.of());
        assertEquals(1, sent.size());
        assertEquals("USB", sent.get(0).get("kind").getAsString());
        assertEquals("E:\\", sent.get(0).get("detail").getAsString());
    }

    @Test
    void blockedIpIsNetViolation() {
        snapshot(Set.of("explorer.exe"), Set.of("C:\\"), Set.of("104.18.1.1", "10.0.0.5"));
        assertEquals(1, sent.size());
        assertEquals("NET", sent.get(0).get("kind").getAsString());
        assertTrue(sent.get(0).get("detail").getAsString().contains("chatgpt.com"));
        assertEquals(1, values(monitor.buildFeatures())[3]);   // IP lạ cũng là host mới; 10.0.0.5 nằm trong baseline
    }

    @Test
    void sameViolationSentOnce() {
        for (int i = 0; i < 3; i++) {
            snapshot(Set.of("explorer.exe", "chrome.exe"), Set.of("C:\\", "E:\\"), Set.of("104.18.1.1"));
        }
        assertEquals(3, sent.size());                          // APP + USB + NET, mỗi loại một lần
    }

    @Test
    void featuresHaveSixValuesAndReset() {
        snapshot(Set.of("explorer.exe", "chrome.exe"), Set.of("C:\\"), Set.of("1.2.3.4"));
        monitor.recordAnswer();
        JsonObject first = monitor.buildFeatures();
        assertEquals("FEATURES", first.get("type").getAsString());
        assertEquals(1, first.get("window").getAsInt());
        assertArrayEquals(new int[] {1, 0, 0, 1, 1, 1}, values(first));
        JsonObject second = monitor.buildFeatures();
        assertEquals(2, second.get("window").getAsInt());
        assertArrayEquals(new int[] {0, 0, 0, 0, 0, 0}, values(second));
    }

    @Test
    void focusLostTwiceCountsOnce() {
        monitor.focusLost();
        monitor.focusLost();
        monitor.focusGained();
        monitor.focusGained();                                 // không đang rời cửa sổ nên không làm gì
        assertEquals(1, values(monitor.buildFeatures())[1]);
    }

    @Test
    void answersCounted() {
        monitor.recordAnswer();
        monitor.recordAnswer();
        monitor.recordAnswer();
        assertEquals(3, values(monitor.buildFeatures())[5]);
    }

    @Test
    void focusLossStillOpenCarriesIntoNextWindow() throws InterruptedException {
        monitor.focusLost();
        Thread.sleep(1100);
        int[] first = values(monitor.buildFeatures());
        assertEquals(1, first[1]);
        assertTrue(first[2] >= 1);
        Thread.sleep(1100);
        monitor.focusGained();
        int[] second = values(monitor.buildFeatures());
        assertEquals(0, second[1]);                            // không đếm lại lần rời cửa sổ
        assertTrue(second[2] >= 1);
    }
    /** Quét chậm (tasklist/PowerShell treo) không được làm trễ FEATURES: nếu trễ quá 90 giây server coi là mất kết nối. */
    @Test
    void featuresSentEvenWhenScanIsSlow() throws Exception {
        List<JsonObject> got = java.util.Collections.synchronizedList(new ArrayList<>());
        Monitor slow = new Monitor(() -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 50, 100);
        slow.start(got::add);
        try {
            Thread.sleep(1500);
        } finally {
            slow.stop();
        }
        long features = got.stream().filter(m -> "FEATURES".equals(m.get("type").getAsString())).count();
        assertTrue(features >= 5, "chỉ gửi được " + features + " FEATURES trong 1,5 giây trong khi quét đang chậm");
    }
}
