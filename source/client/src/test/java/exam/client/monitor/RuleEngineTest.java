// Owner: Nguoi3

package exam.client.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import exam.client.testutil.FakeMetricsSource;
import exam.common.model.MonitoringRules;
import exam.common.model.ViolationType;
import exam.common.protocol.ViolationMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Test 5 luật giám sát với MetricsSource giả (không cần OSHI, không cần mạng). */
class RuleEngineTest {

    private FakeMetricsSource source;
    private RuleEngineImpl engine;
    private MonitoringRules rules;

    @BeforeEach
    void setUp() {
        source = new FakeMetricsSource();
        source.processes.addAll(List.of("java", "explorer.exe", "Code"));
        source.usbDevices.add("Root Hub [usb0]");
        source.networkCards.addAll(List.of("lo", "eth0"));

        // Giả lập DNS: chatgpt.com -> 104.18.1.1, gemini.google.com -> hai IP, api.openai.com không resolve được
        Map<String, List<String>> dns = Map.of(
                "chatgpt.com", List.of("104.18.1.1"),
                "gemini.google.com", List.of("142.250.1.1", "142.250.1.2"));
        DomainResolver resolver = domain -> dns.getOrDefault(domain, List.of());

        rules = MonitoringRules.createDefault();
        engine = new RuleEngineImpl(source, resolver);
    }

    private ViolationMessage onlyViolation(List<ViolationMessage> violations) {
        assertEquals(1, violations.size(), "mong đợi đúng 1 vi phạm");
        return violations.get(0);
    }

    // ------------------------------------------------------------------
    // Luật 1: process
    // ------------------------------------------------------------------

    @Test
    void noViolationWhenNothingChanges() {
        engine.start(rules);

        assertTrue(engine.checkForViolations().isEmpty());
        assertTrue(engine.checkForViolations().isEmpty());
    }

    @Test
    void newProcessNotInAllowlistIsViolation() {
        engine.start(rules);
        source.processes.add("notepad.exe");

        // Lần đầu thấy: chưa báo (có thể là tiến trình chớp nhoáng). Lần kiểm tra thứ hai vẫn còn: báo.
        assertTrue(engine.checkForViolations().isEmpty());
        ViolationMessage violation = onlyViolation(engine.checkForViolations());

        assertEquals(ViolationType.PROCESS_NOT_ALLOWED, violation.violationType);
        assertEquals("notepad.exe", violation.evidence);
        assertTrue(violation.time > 0);
    }

    @Test
    void shortLivedNewProcessIsNotReported() {
        engine.start(rules);

        source.processes.add("kworker/0:1");
        assertTrue(engine.checkForViolations().isEmpty());
        source.processes.remove("kworker/0:1");
        assertTrue(engine.checkForViolations().isEmpty());
        assertTrue(engine.checkForViolations().isEmpty());
    }

    @Test
    void processFromTheSnapshotAtStartIsAllowed() {
        engine.start(rules);

        // "Code" và "java" đã chạy từ lúc bắt đầu: không bị coi là vi phạm dù cũng không nằm trong danh sách nào
        assertTrue(engine.checkForViolations().isEmpty());
    }

    @Test
    void newProcessInConfiguredAllowlistIsAccepted() {
        rules.processAllowlist.add("calc");
        engine.start(rules);
        source.processes.add("Calc.exe");

        assertTrue(engine.checkForViolations().isEmpty());
    }

    @Test
    void denylistedProcessIsViolationEvenIfNameCaseDiffers() {
        engine.start(rules);
        source.processes.add("ZALO.EXE");

        ViolationMessage violation = onlyViolation(engine.checkForViolations());

        assertEquals(ViolationType.PROCESS_DENYLIST, violation.violationType);
        assertEquals("ZALO.EXE", violation.evidence);
    }

    @Test
    void everyProcessInTheDefaultDenylistIsDetected() {
        engine.start(rules);
        for (String name : List.of("Zalo", "Telegram", "Messenger", "TeamViewer", "AnyDesk")) {
            source.processes.add(name + ".exe");
        }

        List<ViolationMessage> violations = engine.checkForViolations();

        assertEquals(5, violations.size());
        for (ViolationMessage violation : violations) {
            assertEquals(ViolationType.PROCESS_DENYLIST, violation.violationType);
        }
    }

    @Test
    void denylistedProcessAlreadyRunningAtStartIsStillReported() {
        source.processes.add("Telegram");

        engine.start(rules);

        assertEquals(ViolationType.PROCESS_DENYLIST, onlyViolation(engine.checkForViolations()).violationType);
    }

    @Test
    void sameViolationIsReportedOnlyOnce() {
        engine.start(rules);
        source.processes.add("notepad.exe");

        assertTrue(engine.checkForViolations().isEmpty());
        assertEquals(1, engine.checkForViolations().size());
        assertTrue(engine.checkForViolations().isEmpty());
        assertTrue(engine.checkForViolations().isEmpty());
    }

    // ------------------------------------------------------------------
    // Luật 2: domain/IP
    // ------------------------------------------------------------------

    @Test
    void connectionToResolvedForbiddenIpIsViolation() {
        engine.start(rules);
        source.remoteAddresses.addAll(List.of("8.8.8.8", "104.18.1.1"));

        ViolationMessage violation = onlyViolation(engine.checkForViolations());

        assertEquals(ViolationType.BLOCKED_IP, violation.violationType);
        assertTrue(violation.evidence.contains("104.18.1.1"));
        assertTrue(violation.evidence.contains("chatgpt.com"));
    }

    @Test
    void anyOfTheIpsOfADomainCounts() {
        engine.start(rules);
        source.remoteAddresses.add("142.250.1.2");

        ViolationMessage violation = onlyViolation(engine.checkForViolations());

        assertTrue(violation.evidence.contains("gemini.google.com"));
    }

    @Test
    void connectionToOtherIpIsFine() {
        engine.start(rules);
        source.remoteAddresses.addAll(List.of("8.8.8.8", "1.1.1.1"));

        assertTrue(engine.checkForViolations().isEmpty());
    }

    @Test
    void ipConnectedBeforeStartIsStillDetectedBecauseRuleIsAboutForbiddenIp() {
        source.remoteAddresses.add("104.18.1.1");
        engine.start(rules);

        assertEquals(ViolationType.BLOCKED_IP, onlyViolation(engine.checkForViolations()).violationType);
    }

    @Test
    void domainThatCannotBeResolvedDoesNotBreakAnything() {
        // api.openai.com không resolve được trong DNS giả: các luật khác vẫn chạy
        engine.start(rules);
        source.usbDevices.add("USB Flash [usb9]");

        assertEquals(ViolationType.USB_DEVICE, onlyViolation(engine.checkForViolations()).violationType);
    }

    // ------------------------------------------------------------------
    // Luật 3, 4: USB, network card
    // ------------------------------------------------------------------

    @Test
    void newUsbDeviceIsViolationButExistingOneIsNot() {
        engine.start(rules);
        assertTrue(engine.checkForViolations().isEmpty());

        source.usbDevices.add("SanDisk Cruzer [usb5]");
        ViolationMessage violation = onlyViolation(engine.checkForViolations());

        assertEquals(ViolationType.USB_DEVICE, violation.violationType);
        assertEquals("SanDisk Cruzer [usb5]", violation.evidence);
    }

    @Test
    void newNetworkCardIsViolation() {
        engine.start(rules);
        source.networkCards.add("tun0");

        ViolationMessage violation = onlyViolation(engine.checkForViolations());

        assertEquals(ViolationType.NETWORK_CARD, violation.violationType);
        assertEquals("tun0", violation.evidence);
    }

    @Test
    void networkCardPresentAtStartIsNotViolation() {
        engine.start(rules);

        assertTrue(engine.checkForViolations().isEmpty());
    }

    // ------------------------------------------------------------------
    // Luật 5: focus
    // ------------------------------------------------------------------

    @Test
    void focusLossBelowThresholdIsNotViolation() {
        engine.start(rules); // ngưỡng mặc định 3

        engine.onFocusLost();
        engine.onFocusLost();

        assertEquals(2, engine.getFocusLostCount());
        assertTrue(engine.checkForViolations().isEmpty());
    }

    @Test
    void focusLossAtThresholdIsViolationAndMoreLossesAreReportedAgain() {
        engine.start(rules);
        for (int i = 0; i < 3; i++) {
            engine.onFocusLost();
        }

        ViolationMessage first = onlyViolation(engine.checkForViolations());
        assertEquals(ViolationType.FOCUS_LOSS, first.violationType);
        assertTrue(first.evidence.contains("3"));

        assertTrue(engine.checkForViolations().isEmpty(), "chưa mất focus thêm thì không báo lại");

        engine.onFocusLost();
        assertTrue(onlyViolation(engine.checkForViolations()).evidence.contains("4"));
    }

    @Test
    void focusThresholdComesFromRules() {
        rules.focusLossThreshold = 1;
        engine.start(rules);

        engine.onFocusLost();

        assertEquals(ViolationType.FOCUS_LOSS, onlyViolation(engine.checkForViolations()).violationType);
    }

    @Test
    void focusLossBeforeStartIsNotCounted() {
        engine.onFocusLost();
        engine.onFocusLost();

        assertEquals(0, engine.getFocusLostCount());
    }

    // ------------------------------------------------------------------
    // Độ bền
    // ------------------------------------------------------------------

    @Test
    void checkBeforeStartReturnsNothing() {
        source.processes.add("Zalo");

        assertTrue(engine.checkForViolations().isEmpty());
    }

    @Test
    void failureToReadProcessesDoesNotStopOtherRules() {
        engine.start(rules);
        source.failProcessListing = true;
        source.networkCards.add("wlan1");

        ViolationMessage violation = onlyViolation(engine.checkForViolations());

        assertEquals(ViolationType.NETWORK_CARD, violation.violationType);
    }

    @Test
    void startingAgainTakesAFreshSnapshotAndResetsCounters() {
        engine.start(rules);
        engine.onFocusLost();
        source.processes.add("notepad.exe");
        engine.checkForViolations();
        assertEquals(1, engine.checkForViolations().size());

        engine.start(rules); // ca thi mới

        assertEquals(0, engine.getFocusLostCount());
        assertTrue(engine.checkForViolations().isEmpty(), "notepad đã nằm trong mốc mới");
    }
}
