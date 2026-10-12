// Test đọc kết quả tasklist/netstat của WindowsProbe: chạy được trên Linux vì chỉ phân tích chuỗi mẫu.
package exam;

import static org.junit.jupiter.api.Assertions.*;

import exam.client.WindowsProbe;
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
        assertEquals(Set.of("142.250.4.100", "93.184.216.34", "2001:db8::1"), WindowsProbe.parseNetstat(out));
    }
}
