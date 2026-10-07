// Owner: Nguoi1

package exam.server;

import exam.server.ml.MlConfig;
import java.nio.file.Path;

/**
 * Điểm khởi động của Server. Chạy từ thư mục gốc của repo (để đường dẫn config/ và data/ đúng):
 *
 *   java -cp "source/server/target/server.jar:source/server/target/lib/*" exam.server.ServerMain
 *
 * Việc làm: đọc config, rồi ServerApp mở SQLite + nạp dữ liệu mẫu, nối các thành phần và mở cổng TCP.
 */
public class ServerMain {

    private static final String SERVER_CONFIG_FILE = "config/server.properties";
    private static final String ML_CONFIG_FILE = "config/ml.properties";

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.load(Path.of(SERVER_CONFIG_FILE));
        MlConfig mlConfig = MlConfig.load(Path.of(ML_CONFIG_FILE));

        ServerApp app = ServerApp.start(config, mlConfig);
        app.waitUntilStopped();
    }
}
