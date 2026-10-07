// Owner: Nguoi1

package exam.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Cấu hình Server, đọc từ config/server.properties. Thiếu khóa nào thì dùng giá trị mặc định ở đây.
 * Mọi ngưỡng quan trọng nằm trong file config (không hard-code rải rác trong code).
 */
public class ServerConfig {

    // --- Mạng ---
    public final int port;
    public final String databasePath;
    /** Sinh viên im lặng quá số giây này thì coi là mất kết nối. Nên >= 3 chu kỳ heartbeat. */
    public final int heartbeatTimeoutSeconds;
    /** Session offline quá số giây này thì token hết hạn, không RECONNECT được nữa. */
    public final int reconnectWindowSeconds;
    /** Độ dài tối đa của một dòng JSON (byte). Chống client gửi dòng khổng lồ làm tràn bộ nhớ. */
    public final int maxMessageBytes;
    /** Số lần LOGIN sai tối đa trên một connection trước khi Server ngắt kết nối. */
    public final int maxLoginFailures;

    // --- Đồng hồ thi ---
    public final int timeSyncIntervalSeconds;

    // --- Giám sát ---
    /** Chu kỳ chạy MonitorService.runPeriodicChecks (giây). Cũng là chu kỳ đo ở client. */
    public final int monitorIntervalSeconds;
    public final int focusThreshold;

    // --- Thống kê phòng ---
    /** Số máy tối thiểu trong phòng để tính cảnh báo lệch trung vị. */
    public final int roomMinMachines;
    /** Máy có giá trị >= X lần trung vị phòng thì cảnh báo VÀNG. */
    public final double roomWarningMultiplier;
    /** Máy có giá trị >= X lần trung vị phòng thì cảnh báo ĐỎ. */
    public final double roomCriticalMultiplier;
    /** Ngoài ra giá trị phải lệch khỏi trung vị quá bấy nhiêu lần MAD (đã chuẩn hóa). */
    public final double roomMadThreshold;
    /** Trung vị nhỏ hơn giá trị này thì dùng giá trị này làm mẫu số, tránh chia cho 0. */
    public final double roomMinMedian;

    // --- Rate control (chu kỳ gửi số liệu) ---
    public final int rateNormalIntervalMs;
    public final int rateHighIntervalMs;
    public final int rateBaselineIntervalMs;
    /** HIGH tự về NORMAL sau số giây yên này. */
    public final int rateHighQuietSeconds;

    private ServerConfig(Properties p) {
        port = readInt(p, "server.port", 5000);
        databasePath = p.getProperty("db.path", "data/exam.db").trim();
        heartbeatTimeoutSeconds = readInt(p, "heartbeat.timeout.seconds", 30);
        reconnectWindowSeconds = readInt(p, "reconnect.window.seconds", 600);
        maxMessageBytes = readInt(p, "message.max.bytes", 2_000_000);
        maxLoginFailures = readInt(p, "login.max.failures", 5);
        timeSyncIntervalSeconds = readInt(p, "time.sync.interval.seconds", 30);
        monitorIntervalSeconds = readInt(p, "monitor.interval.seconds", 10);
        focusThreshold = readInt(p, "focus.threshold", 3);
        roomMinMachines = readInt(p, "room.min.machines", 3);
        roomWarningMultiplier = readDouble(p, "room.warning.multiplier", 3.0);
        roomCriticalMultiplier = readDouble(p, "room.critical.multiplier", 6.0);
        roomMadThreshold = readDouble(p, "room.mad.threshold", 3.5);
        roomMinMedian = readDouble(p, "room.min.median", 1.0);
        rateNormalIntervalMs = readInt(p, "rate.normal.interval.ms", 10_000);
        rateHighIntervalMs = readInt(p, "rate.high.interval.ms", 2_000);
        rateBaselineIntervalMs = readInt(p, "rate.baseline.interval.ms", 1_000);
        rateHighQuietSeconds = readInt(p, "rate.high.quiet.seconds", 60);
    }

    /** Đọc file. Thiếu file thì dùng toàn bộ giá trị mặc định. */
    public static ServerConfig load(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                properties.load(inputStream);
            }
        } else {
            System.out.println("[Config] Không thấy " + file + ", dùng cấu hình mặc định");
        }
        return new ServerConfig(properties);
    }

    /** Dùng trong test: ghi đè vài khóa, còn lại mặc định. */
    public static ServerConfig fromProperties(Properties properties) {
        return new ServerConfig(properties);
    }

    private static int readInt(Properties p, String key, int defaultValue) {
        String text = p.getProperty(key);
        if (text == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Config " + key + " phải là số nguyên, đang là: " + text, e);
        }
    }

    private static double readDouble(Properties p, String key, double defaultValue) {
        String text = p.getProperty(key);
        if (text == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Config " + key + " phải là số, đang là: " + text, e);
        }
    }
}
