// Công cụ chẩn đoán chạy trên máy Windows của sinh viên: java -cp exam.jar exam.client.Diagnose [rules.json] (Người 2).
package exam.client;

import com.google.gson.JsonObject;
import exam.client.WindowsProbe.Proc;
import exam.client.WindowsProbe.Win;
import exam.common.Protocol;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Diagnose {
    private static final int RUNS = 5;

    private Diagnose() {}

    public static void main(String[] args) throws IOException {
        JsonObject rules = loadRules(args.length > 0 ? Path.of(args[0]) : null);
        StringBuilder out = new StringBuilder("=== Hệ thống ===\n");
        out.append("Hệ điều hành: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
            .append("\nJava: ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor"))
            .append(")\nLuật: ").append(args.length > 0 ? args[0] : "rules.json đóng gói trong jar").append("\n\n");
        List<Proc> procs = List.of();
        List<Win> wins = List.of();
        if (WindowsProbe.isWindows()) {
            out.append("=== Thời gian chạy lệnh (").append(RUNS).append(" lần, ms) ===\n");
            procs = measure(out, "tasklist", WindowsProbe::processList);
            measure(out, "netstat", WindowsProbe::remoteIps);
            wins = measure(out, "PowerShell lấy app có cửa sổ", WindowsProbe::windowedApps);
            out.append('\n');
        } else {
            out.append("Không phải Windows: bỏ qua phần đo và danh sách tiến trình.\n\n");
        }
        out.append(processSections(procs, wins, rules));
        String host = host();
        Path file = Path.of("chan-doan-" + host + ".txt");
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        System.out.println("Đã ghi " + file.toAbsolutePath() + " - hãy gửi file này cho người phụ trách.");
    }

    /** Các mục chỉ phụ thuộc dữ liệu đã thu thập nên test được trên Linux. */
    public static String processSections(List<Proc> procs, List<Win> wins, JsonObject rules) {
        AppRules r = new AppRules(rules);
        StringBuilder out = new StringBuilder("=== App đang có cửa sổ (tên | tiêu đề) ===\n");
        Set<String> windowed = new TreeSet<>();
        for (Win w : wins) {
            out.append(w.name()).append(" | ").append(w.title()).append('\n');
            windowed.add(AppRules.norm(w.name()));
        }
        Set<String> background = new TreeSet<>();
        for (Proc p : procs) {
            String n = AppRules.norm(p.name());
            if (!windowed.contains(n) && !r.skip(n) && !r.blacklisted(n)) { background.add(n); }
        }
        out.append("\n=== Tiến trình KHÔNG có cửa sổ, không thuộc SYSTEM_PROCESSES/ignoreProcesses ===\n")
            .append("Các tiến trình nền này không gây vi phạm, chỉ được đếm vào newProcesses. Muốn bỏ qua hẳn thì chép tên cần\n")
            .append("bỏ vào \"ignoreProcesses\" trong rules.json ở server (chỉ chọn tên chắc chắn là phần mềm lành):\n")
            .append(background.stream().map(n -> "\"" + n + "\"").collect(Collectors.joining(", ", "[", "]"))).append("\n\n");
        out.append("=== Làm PRECHECK không đạt theo luật ===\n");
        List<String> issues = r.appIssues(procs.stream().map(Proc::name).toList(), wins);
        out.append(issues.isEmpty() ? "(không có)\n" : String.join("\n", issues) + "\n");
        out.append("\n=== Toàn bộ tiến trình (tên | pid) ===\n");
        procs.forEach(p -> out.append(p.name()).append(" | ").append(p.pid()).append('\n'));
        return out.toString();
    }

    private static <T> T measure(StringBuilder out, String what, Supplier<T> task) {
        List<Long> ms = new ArrayList<>();
        T last = null;
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            last = task.get();
            ms.add((System.nanoTime() - t0) / 1_000_000);
        }
        out.append(what).append(": ").append(ms).append('\n');
        return last;
    }

    private static JsonObject loadRules(Path file) throws IOException {
        if (file != null) { return Protocol.parse(Files.readString(file, StandardCharsets.UTF_8)); }
        try (InputStream in = Diagnose.class.getResourceAsStream("/rules.json")) {
            if (in == null) { return new JsonObject(); }
            return Protocol.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String host() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return System.getenv().getOrDefault("COMPUTERNAME", "may");
        }
    }
}
