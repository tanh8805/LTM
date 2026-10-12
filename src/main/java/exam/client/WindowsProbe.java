// Nói chuyện với Windows: chạy tasklist/netstat/PowerShell và đọc kết quả. Không biết gì về luật giám sát (Người 2).
package exam.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class WindowsProbe {
    private WindowsProbe() {}

    /** Client chỉ giám sát được trên Windows; máy khác vẫn chạy được để dev. */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Tên các tiến trình đang chạy (chữ thường). */
    public static Set<String> processes() {
        return parseTasklist(run("tasklist", "/fo", "csv", "/nh"));
    }

    /** IP đích của các kết nối TCP đang có. */
    public static Set<String> remoteIps() {
        return parseNetstat(run("netstat", "-no"));
    }

    /** Ổ đĩa rời (USB) đang cắm, ví dụ "E:". Dùng PowerShell vì wmic đã bị gỡ trên Windows 11 bản mới. */
    public static Set<String> removableDrives() {
        String out = run("powershell", "-NoProfile", "-Command",
            "Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=2' | ForEach-Object { $_.DeviceID }");
        Set<String> drives = new LinkedHashSet<>();
        for (String line : out.split("\\R")) {
            if (!line.isBlank()) {
                drives.add(line.trim());
            }
        }
        return drives;
    }

    /** Gốc các ổ đĩa hiện có, ví dụ "C:\" và "E:\". */
    public static Set<String> roots() {
        Set<String> roots = new HashSet<>();
        for (File f : File.listRoots()) {
            roots.add(f.getPath());
        }
        return roots;
    }

    /** Bảng IP -> tên miền của các tên miền bị cấm; tên miền không phân giải được thì bỏ qua. */
    public static Map<String, String> resolve(Set<String> domains) {
        Map<String, String> table = new HashMap<>();
        for (String domain : domains) {
            try {
                for (InetAddress a : InetAddress.getAllByName(domain)) {
                    table.put(a.getHostAddress(), domain);
                }
            } catch (IOException e) {
                // không phân giải được tên miền này thì bỏ qua, các tên miền khác vẫn dùng
            }
        }
        return table;
    }

    /** Tên tiến trình (chữ thường) từ cột đầu của `tasklist /fo csv /nh`. */
    public static Set<String> parseTasklist(String output) {
        Set<String> names = new HashSet<>();
        for (String line : output.split("\\R")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String name = line.startsWith("\"") ? line.substring(1, Math.max(1, line.indexOf('"', 1)))
                                                : line.split(",")[0];
            names.add(name.toLowerCase());
        }
        return names;
    }

    /**
     * IP đích của mọi dòng TCP. Không đọc cột trạng thái vì Windows ngôn ngữ khác có thể dịch chữ ESTABLISHED;
     * dòng LISTENING có đích dạng 0.0.0.0:0 hoặc [::]:0 nên bị loại nhờ cổng 0.
     */
    public static Set<String> parseNetstat(String output) {
        Set<String> ips = new HashSet<>();
        for (String line : output.split("\\R")) {
            String[] cols = line.trim().split("\\s+");
            if (cols.length < 3 || !cols[0].equals("TCP") || cols[2].lastIndexOf(':') < 0) {
                continue;
            }
            int colon = cols[2].lastIndexOf(':');
            String ip = cols[2].substring(0, colon);
            String port = cols[2].substring(colon + 1);
            if (ip.startsWith("[") && ip.endsWith("]")) {
                ip = ip.substring(1, ip.length() - 1);
            }
            boolean ignored = ip.isEmpty() || ip.equals("0.0.0.0") || ip.equals("::") || ip.equals("*")
                || ip.startsWith("127.") || ip.equals("::1") || port.equals("0");
            if (!ignored) {
                ips.add(ip);
            }
        }
        return ips;
    }

    /** Chạy lệnh, chờ tối đa 10 giây; lỗi thì trả chuỗi rỗng để việc quét không ném exception ra ngoài. */
    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    p.getInputStream().transferTo(buf);
                } catch (IOException e) {
                    // tiến trình bị kill giữa chừng, lấy phần đã đọc được
                }
            });
            reader.setDaemon(true);
            reader.start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
            reader.join(1000);
            return buf.toString(Charset.defaultCharset());
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
