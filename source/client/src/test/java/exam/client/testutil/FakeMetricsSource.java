// Owner: Nguoi3

package exam.client.testutil;

import exam.client.api.MetricsSource;
import exam.common.model.Metrics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** MetricsSource giả: test đặt trước process, USB, network card, kết nối, và số liệu. */
public class FakeMetricsSource implements MetricsSource {

    public final List<String> processes = new CopyOnWriteArrayList<>();
    public final List<String> usbDevices = new CopyOnWriteArrayList<>();
    public final List<String> networkCards = new CopyOnWriteArrayList<>();
    public final List<String> remoteAddresses = new CopyOnWriteArrayList<>();
    public volatile Metrics nextMetrics = new Metrics(5, 5, 4, 3, 20, 40, 100, 0);
    public volatile boolean failProcessListing = false;

    @Override
    public Metrics collectMetrics() {
        Metrics m = nextMetrics;
        // Trả về bản sao: MonitoringLoop sẽ ghi focusLostCount vào object này.
        return new Metrics(m.kbSent, m.kbReceived, m.connectionCount, m.distinctDestinationCount,
                m.cpuPercent, m.ramPercent, m.processCount, m.focusLostCount);
    }

    @Override
    public List<String> listProcessNames() {
        if (failProcessListing) {
            throw new IllegalStateException("OSHI không đọc được process (giả lập)");
        }
        return new ArrayList<>(processes);
    }

    @Override
    public List<String> listUsbDevices() {
        return new ArrayList<>(usbDevices);
    }

    @Override
    public List<String> listNetworkCardNames() {
        return new ArrayList<>(networkCards);
    }

    @Override
    public List<String> listRemoteAddresses() {
        return new ArrayList<>(remoteAddresses);
    }
}
