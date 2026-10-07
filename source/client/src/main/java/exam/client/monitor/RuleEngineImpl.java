// Owner: Nguoi3

package exam.client.monitor;

import exam.client.api.MetricsSource;
import exam.client.api.RuleEngine;
import exam.common.model.MonitoringRules;
import exam.common.model.ViolationType;
import exam.common.protocol.ViolationMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 5 luật giám sát chạy trên máy sinh viên.
 *
 *   Luật 1 Process     : lúc start() chụp danh sách process (allowlist). Process MỚI không nằm trong allowlist
 *                        -> PROCESS_NOT_ALLOWED, nhưng chỉ khi thấy nó ở 2 lần kiểm tra liên tiếp
 *                        (tiến trình sống chớp nhoáng như kworker của Linux hay tiến trình con tự thoát không bị báo oan).
 *                        Process thuộc denylist (Zalo, Telegram...) -> PROCESS_DENYLIST, báo ngay lần đầu thấy.
 *   Luật 2 Domain/IP   : lúc start() resolve các domain bị chặn ra IP. Có kết nối tới IP đó -> BLOCKED_IP.
 *   Luật 3 USB         : thiết bị USB mới so với lúc start() -> USB_DEVICE.
 *   Luật 4 Network card: network card mới so với lúc start() (hotspot, VPN) -> NETWORK_CARD.
 *   Luật 5 Focus       : đếm số lần cửa sổ thi mất focus; vượt ngưỡng -> FOCUS_LOSS (cảnh báo WARNING).
 *
 * Mỗi vi phạm chỉ được báo MỘT lần (nhớ bằng khóa "loại + bằng chứng"), tránh spam Server mỗi chu kỳ.
 * Nếu một nguồn số liệu bị lỗi (OSHI không đọc được) thì ghi log và bỏ qua luật đó ở lần này, các luật khác vẫn chạy.
 */
public class RuleEngineImpl implements RuleEngine {

    private final MetricsSource metricsSource;
    private final DomainResolver domainResolver;

    private MonitoringRules rules;
    private volatile boolean started = false;
    private final AtomicInteger focusLostCount = new AtomicInteger(0);
    private int lastReportedFocusLostCount = 0;

    private final Set<String> processesAtStart = new HashSet<>();
    private final Set<String> usbDevicesAtStart = new HashSet<>();
    private final Set<String> networkCardsAtStart = new HashSet<>();
    private final Map<String, String> blockedIpToDomain = new HashMap<>();
    private final Set<String> alreadyReported = new HashSet<>();
    // Process mới (ngoài allowlist) đã thấy ở lần kiểm tra trước; thấy lại lần này thì mới báo.
    private Set<String> newProcessesSeenLastCheck = new HashSet<>();

    public RuleEngineImpl(MetricsSource metricsSource) {
        this(metricsSource, new SystemDomainResolver());
    }

    public RuleEngineImpl(MetricsSource metricsSource, DomainResolver domainResolver) {
        this.metricsSource = metricsSource;
        this.domainResolver = domainResolver;
    }

    @Override
    public synchronized void start(MonitoringRules rules) {
        this.rules = rules;
        processesAtStart.clear();
        usbDevicesAtStart.clear();
        networkCardsAtStart.clear();
        blockedIpToDomain.clear();
        alreadyReported.clear();
        newProcessesSeenLastCheck.clear();
        focusLostCount.set(0);
        lastReportedFocusLostCount = 0;

        try {
            for (String name : metricsSource.listProcessNames()) {
                processesAtStart.add(normalizeProcessName(name));
            }
            usbDevicesAtStart.addAll(metricsSource.listUsbDevices());
            networkCardsAtStart.addAll(metricsSource.listNetworkCardNames());
        } catch (RuntimeException e) {
            System.out.println("[Rule] Không chụp được trạng thái ban đầu: " + e);
        }

        for (String domain : rules.blockedDomains) {
            for (String ip : domainResolver.resolve(domain)) {
                blockedIpToDomain.put(ip, domain);
            }
        }
        System.out.println("[Rule] Bắt đầu giám sát: " + processesAtStart.size() + " process, "
                + usbDevicesAtStart.size() + " USB, " + networkCardsAtStart.size() + " network card, "
                + blockedIpToDomain.size() + " IP bị chặn");
        started = true;
    }

    @Override
    public synchronized List<ViolationMessage> checkForViolations() {
        List<ViolationMessage> violations = new ArrayList<>();
        if (!started) {
            return violations;
        }
        checkProcesses(violations);
        checkBlockedIps(violations);
        checkUsbDevices(violations);
        checkNetworkCards(violations);
        checkFocus(violations);
        return violations;
    }

    @Override
    public void onFocusLost() {
        if (started) {
            focusLostCount.incrementAndGet();
        }
    }

    @Override
    public int getFocusLostCount() {
        return focusLostCount.get();
    }

    // ----- Luật 1: process -----

    private void checkProcesses(List<ViolationMessage> violations) {
        List<String> names;
        try {
            names = metricsSource.listProcessNames();
        } catch (RuntimeException e) {
            System.out.println("[Rule] Không đọc được danh sách process: " + e);
            return;
        }

        Set<String> newProcessesNow = new HashSet<>();
        for (String name : names) {
            String normalized = normalizeProcessName(name);
            if (isInList(normalized, rules.processDenylist)) {
                addOnce(violations, ViolationType.PROCESS_DENYLIST, name);
            } else if (!processesAtStart.contains(normalized) && !isInList(normalized, rules.processAllowlist)) {
                newProcessesNow.add(normalized);
                if (newProcessesSeenLastCheck.contains(normalized)) {
                    addOnce(violations, ViolationType.PROCESS_NOT_ALLOWED, name);
                }
            }
        }
        newProcessesSeenLastCheck = newProcessesNow;
    }

    /** "Zalo.exe" -> "zalo": chữ thường và bỏ đuôi .exe để so khớp giữa Windows và Linux. */
    private String normalizeProcessName(String name) {
        String lower = name.trim().toLowerCase();
        if (lower.endsWith(".exe")) {
            lower = lower.substring(0, lower.length() - 4);
        }
        return lower;
    }

    /** Tên process khớp một mục trong danh sách nếu chứa tên mục đó (ví dụ "zalo" khớp "zalopc"). */
    private boolean isInList(String normalizedProcessName, List<String> list) {
        for (String entry : list) {
            String normalizedEntry = normalizeProcessName(entry);
            if (!normalizedEntry.isEmpty() && normalizedProcessName.contains(normalizedEntry)) {
                return true;
            }
        }
        return false;
    }

    // ----- Luật 2: domain/IP -----

    private void checkBlockedIps(List<ViolationMessage> violations) {
        if (blockedIpToDomain.isEmpty()) {
            return;
        }
        List<String> addresses;
        try {
            addresses = metricsSource.listRemoteAddresses();
        } catch (RuntimeException e) {
            System.out.println("[Rule] Không đọc được danh sách kết nối: " + e);
            return;
        }
        for (String address : addresses) {
            String domain = blockedIpToDomain.get(address);
            if (domain != null) {
                addOnce(violations, ViolationType.BLOCKED_IP, address + " (" + domain + ")");
            }
        }
    }

    // ----- Luật 3: USB -----

    private void checkUsbDevices(List<ViolationMessage> violations) {
        try {
            for (String device : metricsSource.listUsbDevices()) {
                if (!usbDevicesAtStart.contains(device)) {
                    addOnce(violations, ViolationType.USB_DEVICE, device);
                }
            }
        } catch (RuntimeException e) {
            System.out.println("[Rule] Không đọc được danh sách USB: " + e);
        }
    }

    // ----- Luật 4: network card -----

    private void checkNetworkCards(List<ViolationMessage> violations) {
        try {
            for (String card : metricsSource.listNetworkCardNames()) {
                if (!networkCardsAtStart.contains(card)) {
                    addOnce(violations, ViolationType.NETWORK_CARD, card);
                }
            }
        } catch (RuntimeException e) {
            System.out.println("[Rule] Không đọc được danh sách network card: " + e);
        }
    }

    // ----- Luật 5: focus -----

    private void checkFocus(List<ViolationMessage> violations) {
        int count = focusLostCount.get();
        if (count >= rules.focusLossThreshold && count > lastReportedFocusLostCount) {
            lastReportedFocusLostCount = count;
            violations.add(new ViolationMessage(ViolationType.FOCUS_LOSS,
                    "mất focus " + count + " lần (ngưỡng " + rules.focusLossThreshold + ")", System.currentTimeMillis()));
        }
    }

    /** Thêm vi phạm nếu cặp (loại, bằng chứng) này chưa từng được báo. */
    private void addOnce(List<ViolationMessage> violations, ViolationType type, String evidence) {
        if (alreadyReported.add(type + "|" + evidence)) {
            violations.add(new ViolationMessage(type, evidence, System.currentTimeMillis()));
        }
    }
}
