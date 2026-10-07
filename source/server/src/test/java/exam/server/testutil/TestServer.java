// Owner: Nguoi1

package exam.server.testutil;

import exam.server.ServerApp;
import exam.server.ServerConfig;
import exam.server.ml.MlConfig;
import java.nio.file.Path;
import java.util.Properties;

/** Khởi động một Server thật (cổng ngẫu nhiên, database tạm) ngay trong JVM của test. */
public final class TestServer {

    private TestServer() {
    }

    /** Server với cấu hình mặc định, chỉ đổi cổng và database. */
    public static ServerApp start(Path tempDirectory) throws Exception {
        return start(tempDirectory, new Properties(), new Properties());
    }

    /** serverOverrides và mlOverrides ghi đè một vài khóa của server.properties và ml.properties. */
    public static ServerApp start(Path tempDirectory, Properties serverOverrides, Properties mlOverrides) throws Exception {
        Properties serverProperties = new Properties();
        serverProperties.setProperty("server.port", "0");
        serverProperties.setProperty("db.path", tempDirectory.resolve("test-exam.db").toString());
        serverProperties.putAll(serverOverrides);

        return ServerApp.start(ServerConfig.fromProperties(serverProperties), MlConfig.fromProperties(mlOverrides));
    }
}
