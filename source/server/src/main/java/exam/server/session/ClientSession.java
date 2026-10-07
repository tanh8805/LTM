// Owner: Nguoi1

package exam.server.session;

import exam.common.model.Role;
import exam.server.net.ClientConnection;

/**
 * Một phiên đăng nhập. Sống lâu hơn một kết nối TCP: khi mất mạng, session vẫn còn
 * (online = false) để client RECONNECT bằng token và gắn vào một connection mới.
 *
 * Nhiều thread cùng đọc/ghi các field thay đổi (thread của client này, PeriodicJobs, ...)
 * nên các field đó khai báo volatile để thread khác luôn thấy giá trị mới nhất.
 */
public class ClientSession {

    public final String token;
    public final int userId;
    public final String username;
    public final String fullName;
    public final Role role;
    /** Mã máy dùng trong ALERT, NOTICE, ... Với sinh viên đó chính là mã sinh viên. */
    public final String machineId;
    /** Mã ca thi (chỉ sinh viên). */
    public final String examCode;

    public volatile ClientConnection connection;
    public volatile boolean online;
    public volatile int lastHeartbeatSeq;
    /** Giờ Server nhận HEARTBEAT gần nhất (epoch milli giây). */
    public volatile long lastHeartbeatTime;
    /** Giờ Server phát hiện session mất kết nối (epoch milli giây). Dùng để token hết hạn sau reconnect.window. */
    public volatile long offlineSince;

    public ClientSession(String token, int userId, String username, String fullName,
                         Role role, String examCode, ClientConnection connection) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.machineId = username;
        this.examCode = examCode;
        this.connection = connection;
        this.online = true;
    }
}
