// Owner: Nguoi3

package exam.client.monitor;

import exam.client.api.MetricsSource;
import exam.common.model.Metrics;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.UsbDevice;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/**
 * Đọc số liệu thật của máy bằng thư viện OSHI.
 *
 * Các metric:
 *   kbSent, kbReceived        : tốc độ KB/giây của mọi network card (trừ loopback) kể từ lần đo trước
 *   connectionCount           : số kết nối TCP đang ESTABLISHED tới địa chỉ bên ngoài
 *   distinctDestinationCount  : số địa chỉ IP đích khác nhau trong các kết nối đó
 *   cpuPercent, ramPercent    : % CPU kể từ lần đo trước, % RAM đang dùng
 *   processCount              : số tiến trình
 *   focusLostCount            : luôn 0 ở đây (RuleEngine đếm)
 *
 * collectMetrics() synchronized vì lưu "lần đo trước" để tính hiệu số; nhiều thread gọi cùng lúc vẫn đúng.
 */
public class OshiMetricsSource implements MetricsSource {

    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem operatingSystem;
    private final CentralProcessor processor;

    private long[] previousCpuTicks;
    private long previousBytesSent;
    private long previousBytesReceived;
    private long previousTime;

    public OshiMetricsSource() {
        SystemInfo systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.processor = hardware.getProcessor();

        // Lần đo đầu tiên so với thời điểm khởi tạo.
        this.previousCpuTicks = processor.getSystemCpuLoadTicks();
        long[] bytes = readTotalNetworkBytes();
        this.previousBytesSent = bytes[0];
        this.previousBytesReceived = bytes[1];
        this.previousTime = System.currentTimeMillis();
    }

    @Override
    public synchronized Metrics collectMetrics() {
        long now = System.currentTimeMillis();
        double elapsedSeconds = Math.max(0.001, (now - previousTime) / 1000.0);

        long[] bytes = readTotalNetworkBytes();
        double kbSent = Math.max(0, bytes[0] - previousBytesSent) / 1024.0 / elapsedSeconds;
        double kbReceived = Math.max(0, bytes[1] - previousBytesReceived) / 1024.0 / elapsedSeconds;
        previousBytesSent = bytes[0];
        previousBytesReceived = bytes[1];
        previousTime = now;

        double cpuPercent = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0;
        previousCpuTicks = processor.getSystemCpuLoadTicks();

        long totalMemory = hardware.getMemory().getTotal();
        long availableMemory = hardware.getMemory().getAvailable();
        double ramPercent = totalMemory > 0 ? (totalMemory - availableMemory) * 100.0 / totalMemory : 0;

        List<String> destinations = listEstablishedForeignAddresses();
        Set<String> distinctDestinations = new LinkedHashSet<>(destinations);

        return new Metrics(
                round(kbSent), round(kbReceived),
                destinations.size(), distinctDestinations.size(),
                round(clampPercent(cpuPercent)), round(clampPercent(ramPercent)),
                operatingSystem.getProcessCount(), 0);
    }

    @Override
    public List<String> listProcessNames() {
        List<String> names = new ArrayList<>();
        for (OSProcess process : operatingSystem.getProcesses()) {
            names.add(process.getName());
        }
        return names;
    }

    @Override
    public List<String> listUsbDevices() {
        List<String> devices = new ArrayList<>();
        collectUsbDevices(hardware.getUsbDevices(true), devices);
        return devices;
    }

    /** Thiết bị USB là một cây (hub chứa thiết bị): đi qua toàn bộ cây. */
    private void collectUsbDevices(List<UsbDevice> deviceTree, List<String> result) {
        for (UsbDevice device : deviceTree) {
            result.add(device.getName() + " [" + device.getUniqueDeviceId() + "]");
            collectUsbDevices(device.getConnectedDevices(), result);
        }
    }

    @Override
    public List<String> listNetworkCardNames() {
        List<String> names = new ArrayList<>();
        for (NetworkIF networkInterface : hardware.getNetworkIFs(true)) {
            names.add(networkInterface.getName());
        }
        return names;
    }

    @Override
    public List<String> listRemoteAddresses() {
        return new ArrayList<>(new LinkedHashSet<>(listEstablishedForeignAddresses()));
    }

    // ------------------------------------------------------------------

    /** Tổng byte gửi/nhận của mọi network card (không gồm loopback). Trả về {gửi, nhận}. */
    private long[] readTotalNetworkBytes() {
        long sent = 0;
        long received = 0;
        for (NetworkIF networkInterface : hardware.getNetworkIFs(false)) {
            networkInterface.updateAttributes();
            sent += networkInterface.getBytesSent();
            received += networkInterface.getBytesRecv();
        }
        return new long[] {sent, received};
    }

    /** Địa chỉ IP đích của mọi kết nối TCP ESTABLISHED (có thể trùng nhau), bỏ loopback và địa chỉ rỗng. */
    private List<String> listEstablishedForeignAddresses() {
        List<String> addresses = new ArrayList<>();
        for (InternetProtocolStats.IPConnection connection : operatingSystem.getInternetProtocolStats().getConnections()) {
            if (connection.getState() != InternetProtocolStats.TcpState.ESTABLISHED) {
                continue;
            }
            try {
                InetAddress address = InetAddress.getByAddress(connection.getForeignAddress());
                if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                    continue;
                }
                addresses.add(address.getHostAddress());
            } catch (UnknownHostException e) {
                // Độ dài địa chỉ lạ: bỏ qua kết nối này, không làm hỏng cả lần đo.
                System.out.println("[Oshi] Bỏ qua địa chỉ không đọc được: " + e.getMessage());
            }
        }
        return addresses;
    }

    private double clampPercent(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }
        return Math.min(100, value);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
