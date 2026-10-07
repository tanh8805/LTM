// Owner: Nguoi4

package exam.server.ml;

import exam.common.model.MlMode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Đọc config/ml.properties. Thiếu file thì dùng giá trị mặc định (ml.mode = NONE).
 */
public class MlConfig {

    public final MlMode mode;
    public final String serviceUrl;
    public final int serviceTimeoutMs;
    /** Thời gian client học "bình thường" của chính máy mình cho Isolation Forest. */
    public final int clientTrainingSeconds;

    private MlConfig(MlMode mode, String serviceUrl, int serviceTimeoutMs, int clientTrainingSeconds) {
        this.mode = mode;
        this.serviceUrl = serviceUrl;
        this.serviceTimeoutMs = serviceTimeoutMs;
        this.clientTrainingSeconds = clientTrainingSeconds;
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

        String modeText = properties.getProperty("ml.mode", "NONE").trim();
        MlMode mode;
        try {
            mode = MlMode.valueOf(modeText);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "ml.mode = \"" + modeText + "\" không hợp lệ. Chọn một trong: NONE, IF, CHRONOS, BOTH_OR, BOTH_AND", e);
        }

        String serviceUrl = properties.getProperty("ml.service.url", "http://localhost:8000").trim();
        int timeoutMs = Integer.parseInt(properties.getProperty("ml.service.timeout.ms", "3000").trim());
        int trainingSeconds = Integer.parseInt(properties.getProperty("ml.client.training.seconds", "300").trim());

        return new MlConfig(mode, serviceUrl, timeoutMs, trainingSeconds);
    }
}
