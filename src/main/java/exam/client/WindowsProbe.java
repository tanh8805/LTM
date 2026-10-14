// Nói chuyện với Windows: chạy tasklist/netstat/PowerShell và đọc kết quả. Không biết gì về luật giám sát (Người 2).
package exam.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

public final class WindowsProbe {
    private WindowsProbe() {}

    /** Một tiến trình trong `tasklist`: tên chữ thường (có .exe) và PID. */
    public record Proc(String name, int pid) {}

    /** Một tiến trình có cửa sổ nhìn thấy được (PowerShell Get-Process): tên không có .exe, PID, tiêu đề cửa sổ. */
    public record Win(String name, int pid, String title) {}

    /** Mỗi dòng in ra dạng `tên|pid|tiêu đề`; dùng nối chuỗi bằng '+' để không phải gõ dấu nháy kép qua ProcessBuilder. */
    private static final String WIN_FORMAT = "ForEach-Object { $_.ProcessName + '|' + $_.Id + '|' + $_.MainWindowTitle }";
    private static final String UTF8 = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;";

    /** Client chỉ giám sát được trên Windows; máy khác vẫn chạy được để dev. */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Mọi tiến trình đang chạy kèm PID (trùng tên thì nhiều dòng). */
    public static List<Proc> processList() {
        return parseTasklistProcs(run("tasklist", "/fo", "csv", "/nh"));
    }

    /** Các app có cửa sổ nhìn thấy được. Tên thuộc tính của Get-Process không đổi theo ngôn ngữ Windows. */
    public static List<Win> windowedApps() {
        return parseWindows(runUtf8(UTF8 + "Get-Process | Where-Object { $_.MainWindowHandle -ne 0 } | " + WIN_FORMAT));
    }

    /** Như windowedApps() nhưng chỉ xét các PID cho trước (rẻ hơn khi chỉ có vài tiến trình mới). */
    public static List<Win> windowsOf(Collection<Integer> pids) {
        if (pids.isEmpty()) { return List.of(); }
        String ids = pids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return parseWindows(runUtf8(UTF8 + "Get-Process -Id " + ids
            + " -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | " + WIN_FORMAT));
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

    /** Tên (chữ thường) và PID từ hai cột đầu của `tasklist /fo csv /nh`; dòng hỏng thì bỏ qua. */
    public static List<Proc> parseTasklistProcs(String output) {
        List<Proc> list = new ArrayList<>();
        for (String line : output.split("\\R")) {
            line = line.trim();
            if (!line.startsWith("\"")) { continue; }
            String[] cols = line.substring(1, line.endsWith("\"") ? line.length() - 1 : line.length()).split("\",\"");
            if (cols.length < 2) { continue; }
            try {
                list.add(new Proc(cols[0].toLowerCase(), Integer.parseInt(cols[1].trim())));
            } catch (NumberFormatException e) {
                // dòng không đúng định dạng thì bỏ qua
            }
        }
        return list;
    }

    /** Đọc các dòng `tên|pid|tiêu đề` (tiêu đề có thể chứa '|'); có thể có BOM đầu output. */
    public static List<Win> parseWindows(String output) {
        List<Win> list = new ArrayList<>();
        for (String line : output.replace("\uFEFF", "").split("\\R")) {
            String[] cols = line.split("\\|", 3);
            if (cols.length < 3) { continue; }
            try {
                list.add(new Win(cols[0].trim().toLowerCase(), Integer.parseInt(cols[1].trim()), cols[2].trim()));
            } catch (NumberFormatException e) {
                // dòng không đúng định dạng thì bỏ qua
            }
        }
        return list;
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

    /** PowerShell đã được ép xuất UTF-8 (UTF8 ở trên) nên tiêu đề cửa sổ tiếng Việt không bị lỗi font. */
    private static String runUtf8(String script) {
        return run(java.nio.charset.StandardCharsets.UTF_8, "powershell", "-NoProfile", "-Command", script);
    }

    private static String run(String... cmd) { return run(Charset.defaultCharset(), cmd); }

    /** Chạy lệnh, chờ tối đa 10 giây; lỗi thì trả chuỗi rỗng để việc quét không ném exception ra ngoài. */
    private static String run(Charset charset, String... cmd) {
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
            return buf.toString(charset);
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
