// Tìm server trong LAN bằng UDP broadcast: gửi EXAM_DISCOVER, nhận EXAM_SERVER <cổng TCP> (Người 1).
package exam.client;

import exam.common.Protocol;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ServerFinder {
    private ServerFinder() {}

    /** Trả địa chỉ TCP của server, hoặc null nếu hết timeoutMs mà không có trả lời hợp lệ. */
    public static InetSocketAddress find(int udpPort, int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] ask = Protocol.DISCOVER.getBytes(StandardCharsets.UTF_8);
            for (InetAddress target : targets()) {
                try {
                    socket.send(new DatagramPacket(ask, ask.length, target, udpPort));
                } catch (IOException e) {
                    // card mạng này không gửi được, các địa chỉ khác vẫn thử tiếp
                }
            }
            return waitForReply(socket, timeoutMs);
        } catch (IOException e) {
            System.err.println("Không mở được socket UDP: " + e.getMessage());
            return null;
        }
    }

    private static InetSocketAddress waitForReply(DatagramSocket socket, int timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        byte[] buf = new byte[256];
        while (true) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return null;
            }
            socket.setSoTimeout((int) left);
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(reply);
            } catch (SocketTimeoutException e) {
                return null;
            }
            int port = parseReply(new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8));
            if (port > 0) {
                return new InetSocketAddress(reply.getAddress(), port);
            }
        }
    }

    /** Trả cổng TCP, hoặc -1 nếu gói trả lời sai định dạng. */
    private static int parseReply(String text) {
        String[] parts = text.trim().split(" ");
        if (parts.length != 2 || !parts[0].equals(Protocol.SERVER_REPLY)) {
            return -1;
        }
        try {
            int port = Integer.parseInt(parts[1]);
            return port > 0 && port <= 65535 ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Máy cài ZeroTier có nhiều card mạng; chỉ gửi 255.255.255.255 thì Java có thể đẩy gói ra nhầm card,
     * nên gửi thêm tới địa chỉ broadcast của từng card, và 127.0.0.1 để chạy client với server cùng máy.
     */
    public static List<InetAddress> targets() {
        Set<InetAddress> result = new LinkedHashSet<>();
        try {
            result.add(InetAddress.getByName("255.255.255.255"));
            Enumeration<NetworkInterface> cards = NetworkInterface.getNetworkInterfaces();
            while (cards != null && cards.hasMoreElements()) {
                NetworkInterface card = cards.nextElement();
                if (!card.isUp() || card.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress addr : card.getInterfaceAddresses()) {
                    if (addr.getBroadcast() != null) {
                        result.add(addr.getBroadcast());
                    }
                }
            }
            result.add(InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            System.err.println("Không liệt kê được card mạng: " + e.getMessage());
        }
        return new ArrayList<>(result);
    }
}
