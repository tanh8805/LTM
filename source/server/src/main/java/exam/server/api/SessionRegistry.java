// Owner: Nguoi1

package exam.server.api;

import exam.common.model.UserAccount;
import exam.server.net.ClientConnection;
import exam.server.session.ClientSession;
import java.util.List;

/**
 * Sổ ghi danh sách phiên (session) đăng nhập đang có trên Server.
 * Một session = một người dùng đã LOGIN, có token để RECONNECT.
 *
 * Ai cài đặt: Nguoi1 (exam.server.session.InMemorySessionRegistry)
 * Ai gọi: Nguoi1 (ClientHandler), Nguoi2 (biết ai đang thi), Nguoi3 (danh sách máy online/offline)
 */
public interface SessionRegistry {

    /** Tạo session mới sau khi LOGIN thành công. */
    ClientSession createSession(UserAccount user, String examCode, ClientConnection connection);

    /** Tìm theo token (dùng cho RECONNECT). Trả về null nếu không có. */
    ClientSession findByToken(String token);

    /** Tìm theo machineId (mã sinh viên). Trả về null nếu không có. */
    ClientSession findByMachineId(String machineId);

    /** Các sinh viên đang online. */
    List<ClientSession> getOnlineStudents();

    /** Các giáo viên đang online. */
    List<ClientSession> getOnlineTeachers();

    /** Đánh dấu offline khi mất kết nối. Session vẫn được giữ lại để RECONNECT. */
    void markOffline(ClientSession session);

    /** RECONNECT thành công: gắn connection mới vào session cũ và đánh dấu online lại. */
    void markOnline(ClientSession session, ClientConnection newConnection);
}
