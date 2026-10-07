// Owner: Nguoi1

package exam.client.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import exam.client.api.ConnectionStatus;
import exam.client.api.ServerLinkListener;
import exam.client.testutil.Wait;
import exam.common.model.Metrics;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.HeartbeatAckMessage;
import exam.common.protocol.HeartbeatMessage;
import exam.common.protocol.Message;
import exam.common.protocol.MessageCodec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Test TcpServerLink với một "Server" thô (ServerSocket): gửi/nhận, bỏ qua dòng rác, mất kết nối và tự nối lại. */
class TcpServerLinkTest {

    private final MessageCodec codec = new MessageCodec();
    private ServerSocket serverSocket;
    private TcpServerLink link;
    private final List<Message> received = new CopyOnWriteArrayList<>();
    private final List<ConnectionStatus> statuses = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() throws IOException {
        if (link != null) {
            link.close();
        }
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    private TcpServerLink createLink(int reconnectDelayMs, int maxAttempts) {
        TcpServerLink newLink = new TcpServerLink(reconnectDelayMs, maxAttempts);
        newLink.addListener(new ServerLinkListener() {
            @Override
            public void onMessage(Message message) {
                received.add(message);
            }

            @Override
            public void onStatusChanged(ConnectionStatus newStatus) {
                statuses.add(newStatus);
            }
        });
        return newLink;
    }

    private BufferedReader readerOf(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private BufferedWriter writerOf(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Test
    void sendsOneJsonLinePerMessageAndReceivesMessages() throws Exception {
        serverSocket = new ServerSocket(0);
        link = createLink(100, 5);

        link.connect("localhost", serverSocket.getLocalPort());
        Socket accepted = serverSocket.accept();
        assertEquals(ConnectionStatus.CONNECTED, link.getStatus());

        assertTrue(link.send(new HeartbeatMessage(3, new Metrics())));
        String line = readerOf(accepted).readLine();
        assertTrue(line.startsWith("{\"type\":\"HEARTBEAT\""));
        assertEquals(3, ((HeartbeatMessage) codec.decode(line)).seq);

        BufferedWriter serverWriter = writerOf(accepted);
        serverWriter.write(codec.encode(new HeartbeatAckMessage(3, 1000L)) + "\n");
        serverWriter.flush();
        assertTrue(Wait.until(() -> received.size() == 1, 3000));
        assertEquals(3, ((HeartbeatAckMessage) received.get(0)).seq);
    }

    @Test
    void garbageLinesAreIgnoredAndLaterMessagesStillArrive() throws Exception {
        serverSocket = new ServerSocket(0);
        link = createLink(100, 5);
        link.connect("localhost", serverSocket.getLocalPort());
        Socket accepted = serverSocket.accept();

        BufferedWriter serverWriter = writerOf(accepted);
        serverWriter.write("not json\n");
        serverWriter.write(codec.encode(new ErrorMessage("X", "y")) + "\n");
        serverWriter.flush();

        assertTrue(Wait.until(() -> received.size() == 1, 3000));
        assertEquals("X", ((ErrorMessage) received.get(0)).code);
    }

    @Test
    void connectToClosedPortThrows() throws Exception {
        serverSocket = new ServerSocket(0);
        int closedPort = serverSocket.getLocalPort();
        serverSocket.close();
        link = createLink(100, 5);

        assertThrows(IOException.class, () -> link.connect("localhost", closedPort));
        assertEquals(ConnectionStatus.DISCONNECTED, link.getStatus());
    }

    @Test
    void sendWhileDisconnectedReturnsFalse() {
        link = createLink(100, 5);

        assertFalse(link.send(new HeartbeatMessage(1, new Metrics())));
    }

    @Test
    void reconnectsAutomaticallyWhenServerDropsTheConnection() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        link = createLink(100, 50);
        link.connect("localhost", port);
        Socket first = serverSocket.accept();

        first.close(); // Server cắt kết nối

        assertTrue(Wait.until(() -> statuses.contains(ConnectionStatus.RECONNECTING), 3000));
        Socket second = serverSocket.accept(); // client tự nối lại
        assertTrue(Wait.until(() -> link.getStatus() == ConnectionStatus.CONNECTED, 3000));
        assertEquals(List.of(ConnectionStatus.CONNECTED, ConnectionStatus.RECONNECTING, ConnectionStatus.CONNECTED), statuses);

        // Sau khi nối lại gửi/nhận bình thường
        assertTrue(link.send(new HeartbeatMessage(9, new Metrics())));
        assertEquals(9, ((HeartbeatMessage) codec.decode(readerOf(second).readLine())).seq);
    }

    @Test
    void keepsTryingWhileServerIsDownAndConnectsWhenItIsBack() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        link = createLink(100, 100);
        link.connect("localhost", port);
        Socket first = serverSocket.accept();

        serverSocket.close(); // Server sập hoàn toàn
        first.close();
        assertTrue(Wait.until(() -> statuses.contains(ConnectionStatus.RECONNECTING), 3000));
        Thread.sleep(500); // vài lần thử thất bại

        serverSocket = new ServerSocket(port); // Server chạy lại đúng cổng cũ
        Socket second = serverSocket.accept();

        assertTrue(Wait.until(() -> link.getStatus() == ConnectionStatus.CONNECTED, 3000));
        assertTrue(second.isConnected());
    }

    @Test
    void givesUpAfterMaxAttempts() throws Exception {
        serverSocket = new ServerSocket(0);
        link = createLink(50, 3);
        link.connect("localhost", serverSocket.getLocalPort());
        Socket first = serverSocket.accept();
        serverSocket.close();
        first.close();

        assertTrue(Wait.until(() -> link.getStatus() == ConnectionStatus.DISCONNECTED
                && statuses.contains(ConnectionStatus.RECONNECTING), 5000));
    }

    @Test
    void closeDoesNotTriggerReconnect() throws Exception {
        serverSocket = new ServerSocket(0);
        link = createLink(100, 5);
        link.connect("localhost", serverSocket.getLocalPort());
        serverSocket.accept();

        link.close();
        Thread.sleep(400);

        assertEquals(ConnectionStatus.DISCONNECTED, link.getStatus());
        assertFalse(statuses.contains(ConnectionStatus.RECONNECTING));
    }
}
