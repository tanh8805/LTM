// Owner: Nguoi1

package exam.client.api;

import exam.common.model.Metrics;
import java.util.List;

/**
 * Nguồn dữ liệu về trạng thái máy tính của sinh viên (CPU, RAM, process, USB, network...).
 *
 * Ai cài đặt: Nguoi3 (exam.client.monitor.OshiMetricsSource, dùng thư viện OSHI)
 * Ai gọi: Nguoi3 (RuleEngine, MonitoringLoop), Nguoi1 (StudentMain lấy summary cho HEARTBEAT),
 *         Nguoi4 (tools.Recorder ghi trace)
 */
public interface MetricsSource {

    /** Đo vector 8 chiều ngay lúc này. focusLostCount do RuleEngine đếm nên ở đây để 0. */
    Metrics collectMetrics();

    /** Tên các process đang chạy. */
    List<String> listProcessNames();

    /** Các thiết bị USB đang cắm. */
    List<String> listUsbDevices();

    /** Các network card đang có. */
    List<String> listNetworkCardNames();

    /** Các địa chỉ IP đích đang có kết nối. */
    List<String> listRemoteAddresses();
}
