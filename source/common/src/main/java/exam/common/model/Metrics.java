// Owner: Nguoi1

package exam.common.model;

/**
 * Vector 8 chiều đo mỗi 10 giây trên máy sinh viên.
 *
 * Thứ tự trong toVector() cố định. 6 chiều đầu là các chiều Chronos-Bolt chấm điểm
 * (server ML); 2 chiều cuối (processCount, focusLostCount) chỉ dùng cho Isolation Forest ở client.
 */
public class Metrics {
    /** Tên 8 chiều, đúng thứ tự của toVector(). */
    public static final String[] VECTOR_NAMES = {
            "kbSent", "kbReceived", "connectionCount", "distinctDestinationCount",
            "cpuPercent", "ramPercent", "processCount", "focusLostCount"
    };
    /** Số chiều Chronos dùng (6 chiều đầu). */
    public static final int CHRONOS_DIMENSIONS = 6;

    public double kbSent;
    public double kbReceived;
    public int connectionCount;
    public int distinctDestinationCount;
    public double cpuPercent;
    public double ramPercent;
    public int processCount;
    public int focusLostCount;

    public Metrics() {
    }

    public Metrics(double kbSent, double kbReceived, int connectionCount, int distinctDestinationCount,
                   double cpuPercent, double ramPercent, int processCount, int focusLostCount) {
        this.kbSent = kbSent;
        this.kbReceived = kbReceived;
        this.connectionCount = connectionCount;
        this.distinctDestinationCount = distinctDestinationCount;
        this.cpuPercent = cpuPercent;
        this.ramPercent = ramPercent;
        this.processCount = processCount;
        this.focusLostCount = focusLostCount;
    }

    /** Đổi sang mảng 8 số để đưa vào Isolation Forest. */
    public double[] toVector() {
        return new double[] {
                kbSent, kbReceived, connectionCount, distinctDestinationCount,
                cpuPercent, ramPercent, processCount, focusLostCount
        };
    }
}
