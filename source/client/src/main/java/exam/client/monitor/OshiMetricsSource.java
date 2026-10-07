// Owner: Nguoi3

package exam.client.monitor;

import exam.client.api.MetricsSource;
import exam.common.model.Metrics;
import java.util.ArrayList;
import java.util.List;

/** STUB của MetricsSource: trả số liệu rỗng. Bản thật dùng thư viện OSHI. */
public class OshiMetricsSource implements MetricsSource {

    @Override
    public Metrics collectMetrics() {
        // TODO(Nguoi3): Dùng OSHI đo KB gửi/nhận (so với lần đo trước), số kết nối, số địa chỉ đích khác nhau,
        //  %CPU, %RAM, số process. focusLostCount để 0 (RuleEngine đếm).
        return new Metrics();
    }

    @Override
    public List<String> listProcessNames() {
        // TODO(Nguoi3): OSHI: SystemInfo.getOperatingSystem().getProcesses().
        return new ArrayList<>();
    }

    @Override
    public List<String> listUsbDevices() {
        // TODO(Nguoi3): OSHI: HardwareAbstractionLayer.getUsbDevices(...).
        return new ArrayList<>();
    }

    @Override
    public List<String> listNetworkCardNames() {
        // TODO(Nguoi3): OSHI: HardwareAbstractionLayer.getNetworkIFs().
        return new ArrayList<>();
    }

    @Override
    public List<String> listRemoteAddresses() {
        // TODO(Nguoi3): OSHI: OperatingSystem.getInternetProtocolStats().getConnections().
        return new ArrayList<>();
    }
}
