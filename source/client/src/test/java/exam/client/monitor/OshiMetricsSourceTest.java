// Owner: Nguoi3

package exam.client.monitor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import exam.common.model.Metrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Smoke test với OSHI thật trên máy chạy test: giá trị phải nằm trong khoảng hợp lý. */
class OshiMetricsSourceTest {

    @Test
    void collectedMetricsAreInSaneRanges() throws Exception {
        OshiMetricsSource source = new OshiMetricsSource();
        source.collectMetrics(); // lần đầu làm mốc
        Thread.sleep(300);

        Metrics metrics = source.collectMetrics();

        assertTrue(metrics.cpuPercent >= 0 && metrics.cpuPercent <= 100, "cpu=" + metrics.cpuPercent);
        assertTrue(metrics.ramPercent > 0 && metrics.ramPercent <= 100, "ram=" + metrics.ramPercent);
        assertTrue(metrics.kbSent >= 0);
        assertTrue(metrics.kbReceived >= 0);
        assertTrue(metrics.connectionCount >= 0);
        assertTrue(metrics.distinctDestinationCount >= 0);
        assertTrue(metrics.distinctDestinationCount <= metrics.connectionCount);
        assertTrue(metrics.processCount > 0);
        assertTrue(metrics.focusLostCount == 0, "focus do RuleEngine đếm");
    }

    @Test
    void processListContainsTheTestJvmItself() {
        OshiMetricsSource source = new OshiMetricsSource();

        List<String> processes = source.listProcessNames();

        assertFalse(processes.isEmpty());
        boolean foundJava = false;
        for (String name : processes) {
            if (name.toLowerCase().contains("java")) {
                foundJava = true;
            }
        }
        assertTrue(foundJava, "phải thấy process java của chính test");
    }

    @Test
    void hardwareListsAreAvailable() {
        OshiMetricsSource source = new OshiMetricsSource();

        assertNotNull(source.listUsbDevices());
        assertNotNull(source.listRemoteAddresses());
        assertFalse(source.listNetworkCardNames().isEmpty(), "máy luôn có ít nhất loopback hoặc một network card");
    }

    @Test
    void repeatedMeasurementsNeverGoNegative() throws Exception {
        OshiMetricsSource source = new OshiMetricsSource();
        source.collectMetrics();

        Thread.sleep(200);
        Metrics second = source.collectMetrics();
        Thread.sleep(200);
        Metrics third = source.collectMetrics();

        assertTrue(second.kbSent >= 0 && third.kbSent >= 0);
        assertTrue(second.kbReceived >= 0 && third.kbReceived >= 0);
    }
}
