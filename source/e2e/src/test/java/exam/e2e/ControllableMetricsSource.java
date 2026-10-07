// Owner: Nguoi3

package exam.e2e;

import exam.client.api.MetricsSource;
import exam.common.model.Metrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Nguồn số liệu giả cho test e2e: số liệu "bình thường" có nhiễu nhỏ (để Isolation Forest học được),
 * và test có thể ép một đột biến hoặc cắm "USB mới".
 */
class ControllableMetricsSource implements MetricsSource {

    final List<String> processes = new CopyOnWriteArrayList<>(List.of("java", "explorer"));
    final List<String> usbDevices = new CopyOnWriteArrayList<>(List.of("Root Hub [usb0]"));
    final List<String> networkCards = new CopyOnWriteArrayList<>(List.of("lo", "eth0"));
    final List<String> remoteAddresses = new CopyOnWriteArrayList<>(List.of("8.8.8.8"));
    volatile boolean spike = false;
    private final Random random = new Random();

    @Override
    public synchronized Metrics collectMetrics() {
        double noise = random.nextGaussian();
        if (spike) {
            return new Metrics(9000 + noise, 7000, 60, 40, 95, 80, 100, 0);
        }
        return new Metrics(10 + noise, 20 + random.nextGaussian(), 6 + random.nextInt(3), 4 + random.nextInt(3),
                20 + random.nextGaussian(), 40 + random.nextGaussian(), 100 + random.nextInt(3), 0);
    }

    @Override
    public List<String> listProcessNames() {
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
