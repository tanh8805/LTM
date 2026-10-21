// Test đọc kết quả tasklist/netstat của WindowsProbe: chạy được trên Linux vì chỉ phân tích chuỗi mẫu.
package exam;

import static org.junit.jupiter.api.Assertions.*;

import exam.client.WindowsProbe;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WindowsProbeTest {

    @Test
    void parseTasklistReadsFirstColumnLowercase() {
        String out = "\"Chrome.exe\",\"1234\",\"Console\",\"1\",\"100,000 K\"\r\n"
            + "\"System Idle Process\",\"0\",\"Services\",\"0\",\"8 K\"\r\n"
            + "\"Zalo.EXE\",\"55\",\"Console\",\"1\",\"9,000 K\"\r\n";
        assertEquals(Set.of("chrome.exe", "system idle process", "zalo.exe"), WindowsProbe.parseTasklist(out));
    }

    @Test
    void parseNetstatKeepsEstablishedRemoteIps() {
        String out = "Active Connections\r\n\r\n  Proto  Local Address          Foreign Address        State           PID\r\n"
            + "  TCP    192.168.1.5:50123      142.250.4.100:443      ESTABLISHED     1234\r\n"
            + "  TCP    192.168.1.5:50124      93.184.216.34:443      SYN_SENT        1234\r\n"
            + "  TCP    0.0.0.0:135            0.0.0.0:0              LISTENING       4\r\n"
            + "  TCP    192.168.1.5:50125      127.0.0.1:5000         ESTABLISHED     77\r\n"
            + "  TCP    192.168.1.5:50126      0.0.0.0:0              ESTABLISHED     78\r\n"
            + "  TCP    192.168.1.5:50127      8.8.8.8:443            TIME_WAIT       0\r\n"
            + "  TCP    [2001:db8::2]:50128    [2001:db8::1]:443      ESTABLISHED     5\r\n"
            + "  TCP    [::1]:50129            [::1]:5000             ESTABLISHED     6\r\n"
            + "  UDP    0.0.0.0:5353           *:*                                    9\r\n";
        // Quy tắc mới không đọc cột trạng thái: dòng TIME_WAIT tới 8.8.8.8 cũng được lấy (trước đây bị loại)
        assertEquals(Set.of("142.250.4.100", "93.184.216.34", "8.8.8.8", "2001:db8::1"), WindowsProbe.parseNetstat(out));
    }

    @Test
    void parseNetstatIgnoresStateLanguage() {
        String out = "  TCP    192.168.1.5:50123      142.250.4.100:443      HERGESTELLT     1234\r\n"
            + "  TCP    192.168.1.5:50124      93.184.216.34:443      ĐÃ THIẾT LẬP    1234\r\n";
        assertEquals(Set.of("142.250.4.100", "93.184.216.34"), WindowsProbe.parseNetstat(out));
    }

    @Test
    void parseNetstatSkipsListeningByPortZero() {
        String out = "  TCP    0.0.0.0:135            0.0.0.0:0              LISTENING       1000\r\n"
            + "  TCP    [::]:445               [::]:0                 LISTENING       4\r\n";
        assertTrue(WindowsProbe.parseNetstat(out).isEmpty());
    }

    @Test
    void parseNetstatConnectionsReadsPidColumn() {
        String out = "  TCP    192.168.1.5:50123      142.250.4.100:443      ESTABLISHED     1234\r\n"
            + "  TCP    0.0.0.0:135            0.0.0.0:0              LISTENING       4\r\n"
            + "  TCP    192.168.1.5:50127      8.8.8.8:443            TIME_WAIT       0\r\n"
            + "  TCP    192.168.1.5:50128      1.1.1.1:443            ESTABLISHED     abc\r\n";
        assertEquals(List.of(new WindowsProbe.Conn("142.250.4.100", 1234), new WindowsProbe.Conn("8.8.8.8", 0),
            new WindowsProbe.Conn("1.1.1.1", -1)), WindowsProbe.parseNetstatConnections(out));
    }

    @Test
    void parseTasklistProcsReadsNameAndPid() {
        String out = "\"Chrome.exe\",\"1234\",\"Console\",\"1\",\"100,000 K\"\r\n"
            + "\"System Idle Process\",\"0\",\"Services\",\"0\",\"8 K\"\r\n"
            + "\"loi\"\r\n\"abc.exe\",\"khong-phai-so\",\"x\"\r\n";
        assertEquals(List.of(new WindowsProbe.Proc("chrome.exe", 1234), new WindowsProbe.Proc("system idle process", 0)),
            WindowsProbe.parseTasklistProcs(out));
    }

    @Test
    void parseWindowsReadsNamePidTitle() {
        String out = "\uFEFFAntigravity|4321|Welcome | Antigravity\r\nexplorer|88|Tệp\r\nrac\r\nx|abc|y\r\nnotitle|7|\r\n";
        assertEquals(List.of(new WindowsProbe.Win("antigravity", 4321, "Welcome | Antigravity"),
            new WindowsProbe.Win("explorer", 88, "Tệp"), new WindowsProbe.Win("notitle", 7, "")),
            WindowsProbe.parseWindows(out));
    }
}
