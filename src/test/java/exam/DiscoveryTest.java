// Test tìm server qua UDP: server trả lời EXAM_DISCOVER, client lấy IP/cổng từ gói trả lời.
package exam;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import exam.client.ServerFinder;
import exam.server.ExamServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class DiscoveryTest {

    /** Cổng ngẫu nhiên 20000–29999 để các lần chạy test không đụng nhau. */
    private static int randomUdpPort() {
        return ThreadLocalRandom.current().nextInt(20000, 30000);
    }

    @Test
    void findsLocalServer() throws Exception {
        ExamServer server = new ExamServer(new JsonObject());
        int udp = randomUdpPort();
        try {
            server.start(0);
            server.startDiscovery(udp);
            InetSocketAddress found = ServerFinder.find(udp, 2000);
            assertNotNull(found);
            assertEquals(server.tcpPort(), found.getPort());
        } finally {
            server.stop();
        }
    }

    @Test
    void returnsNullWhenNoServer() {
        long t0 = System.currentTimeMillis();
        assertNull(ServerFinder.find(randomUdpPort(), 500));
        assertTrue(System.currentTimeMillis() - t0 < 3000, "find bị treo");
    }

    @Test
    void targetsIncludeLoopbackAndGlobalBroadcast() throws Exception {
        List<InetAddress> targets = ServerFinder.targets();
        assertTrue(targets.contains(InetAddress.getByName("127.0.0.1")));
        assertTrue(targets.contains(InetAddress.getByName("255.255.255.255")));
    }
}
