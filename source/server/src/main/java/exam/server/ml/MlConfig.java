// Owner: Nguoi4

package exam.server.ml;

import exam.common.model.MlMode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Đọc config/ml.properties. Thiếu file hoặc thiếu khóa thì dùng giá trị mặc định (ml.mode = NONE).
 */
public class MlConfig {

    public final MlMode mode;
    public final String serviceUrl;
    /** Timeout mỗi lần gọi ml-service (mili giây). Quy định: 3 giây. */
    public final int serviceTimeoutMs;
    /** Isolation Forest (client) học "bình thường" trong bấy nhiêu giây đầu. Mặc định 5 phút. */
    public final int clientTrainingSeconds;
    /** Threshold = max(điểm lúc học) + margin. */
    public final double ifThresholdMargin;
    /** Số lần liên tiếp vượt threshold (Isolation Forest). */
    public final int ifConsecutive;
    /** Số điểm gần nhất của mỗi máy gửi cho Chronos (60). */
    public final int chronosWindow;
    /** Máy chưa có đủ số điểm này thì chưa gửi cho Chronos. */
    public final int chronosMinPoints;
    /** Số lần liên tiếp ngoài q0.1-q0.9 (Chronos). */
    public final int chronosConsecutive;
    /** Kết quả Chronos cũ hơn số giây này thì coi như không còn hiệu lực. */
    public final int chronosValidSeconds;

    private MlConfig(Properties p) {
        String modeText = p.getProperty("ml.mode", "NONE").trim();
        try {
            mode = MlMode.valueOf(modeText);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "ml.mode = \"" + modeText + "\" không hợp lệ. Chọn một trong: NONE, IF, CHRONOS, BOTH_OR, BOTH_AND", e);
        }
        serviceUrl = p.getProperty("ml.service.url", "http://localhost:8000").trim();
        serviceTimeoutMs = readInt(p, "ml.service.timeout.ms", 3000);
        clientTrainingSeconds = readInt(p, "ml.client.training.seconds", 300);
        ifThresholdMargin = Double.parseDouble(p.getProperty("ml.if.threshold.margin", "0.05").trim());
        ifConsecutive = readInt(p, "ml.if.consecutive", 3);
        chronosWindow = readInt(p, "ml.chronos.window", 60);
        chronosMinPoints = readInt(p, "ml.chronos.min.points", 12);
        chronosConsecutive = readInt(p, "ml.chronos.consecutive", 3);
        chronosValidSeconds = readInt(p, "ml.chronos.valid.seconds", 60);
    }

    public static MlConfig load(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream inputStream = Files.newInputStream(file)) {
                properties.load(inputStream);
            }
        } else {
            System.out.println("[ML] Không thấy " + file + ", dùng cấu hình mặc định");
        }
        return new MlConfig(properties);
    }

    /** Dùng trong test. */
    public static MlConfig fromProperties(Properties properties) {
        return new MlConfig(properties);
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
