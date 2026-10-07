// Owner: Nguoi1

package exam.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Cấu hình client, đọc từ config/client.properties. Thiếu file hoặc thiếu khóa thì dùng giá trị mặc định. */
public class ClientConfig {

    public final String serverHost;
    public final int serverPort;
    /** Chu kỳ HEARTBEAT trước khi nhận RULES_CONFIG (sau đó Server quy định). */
    public final int heartbeatIntervalMs;
    /** Chờ bao lâu giữa hai lần thử nối lại. */
    public final int reconnectDelayMs;
    /** Số lần thử nối lại tối đa trước khi bỏ cuộc. */
    public final int reconnectMaxAttempts;

    private ClientConfig(Properties p) {
        serverHost = p.getProperty("server.host", "localhost").trim();
        serverPort = readInt(p, "server.port", 5000);
        heartbeatIntervalMs = readInt(p, "heartbeat.interval.seconds", 10) * 1000;
        reconnectDelayMs = readInt(p, "reconnect.delay.ms", 2000);
        reconnectMaxAttempts = readInt(p, "reconnect.max.attempts", 60);
    }

    public static ClientConfig load(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                properties.load(inputStream);
            }
        }
        return new ClientConfig(properties);
    }

    /** Dùng trong test. */
    public static ClientConfig fromProperties(Properties properties) {
        return new ClientConfig(properties);
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
}
