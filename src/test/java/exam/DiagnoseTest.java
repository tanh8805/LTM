// Test phần báo cáo của công cụ Diagnose bằng dữ liệu mẫu (không cần Windows).
package exam;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import exam.client.Diagnose;
import exam.client.WindowsProbe.Proc;
import exam.client.WindowsProbe.Win;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiagnoseTest {
    static final String RULES = "{\"blacklistApps\":[\"chrome.exe\"],\"allowedWindowApps\":[\"explorer\",\"java\"],"
        + "\"ignoreProcesses\":[\"onedrive\"]}";

    private String report() {
        List<Proc> procs = List.of(new Proc("explorer.exe", 4), new Proc("java.exe", 5), new Proc("chrome.exe", 6),
            new Proc("OneDrive.exe", 7), new Proc("MicService.exe", 8), new Proc("figma.exe", 9),
            new Proc("tasklist.exe", 10), new Proc("MicService.exe", 11), new Proc("AudioHelper.exe", 12));
        List<Win> wins = List.of(new Win("explorer", 4, "Tệp"), new Win("figma", 9, "Figma"), new Win("java", 5, "Thi"));
        return Diagnose.processSections(procs, wins, JsonParser.parseString(RULES).getAsJsonObject());
    }

    @Test
    void listsWindowedApps() {
        assertTrue(report().contains("figma | Figma"), report());
    }

    @Test
    void listsBackgroundCandidatesWithoutWindowSystemIgnoredOrBlacklisted() {
        String r = report();
        String block = r.substring(r.indexOf("=== Tiến trình KHÔNG có cửa sổ"), r.indexOf("=== Làm PRECHECK"));
        for (String not : List.of("explorer", "figma", "tasklist", "onedrive", "chrome")) {
            assertFalse(block.contains(not), not + " không được nằm trong danh sách ứng viên: " + block);
        }
        assertTrue(block.contains("[\"audiohelper\", \"micservice\"]"), "có sẵn dòng JSON để chép: " + block);
    }

    @Test
    void listsPrecheckIssues() {
        String r = report();
        assertTrue(r.substring(r.indexOf("=== Làm PRECHECK")).contains("Hãy đóng ứng dụng: figma (Figma)"), r);
        assertTrue(r.contains("Đang mở ứng dụng bị cấm: chrome.exe"), r);
    }
}
