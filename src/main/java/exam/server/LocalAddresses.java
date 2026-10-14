// IP của máy chủ để sinh viên biết nhập địa chỉ nào (Người 2).
package exam.server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LocalAddresses {
    private LocalAddresses() {}

    /** IPv4 không phải loopback của mọi card đang bật, để sinh viên biết nhập IP nào. */
    static String list() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface card : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!card.isUp()) { continue; }
                for (InetAddress a : Collections.list(card.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) { ips.add(a.getHostAddress()); }
                }
            }
        } catch (IOException e) {
            return "(không đọc được)";
        }
        return ips.isEmpty() ? "(không có)" : String.join(", ", ips);
    }
}
