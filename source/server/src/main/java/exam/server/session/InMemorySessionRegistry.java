// Owner: Nguoi1

package exam.server.session;

import exam.common.model.Role;
import exam.common.model.UserAccount;
import exam.server.api.SessionRegistry;
import exam.server.net.ClientConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu session trong bộ nhớ. Dùng ConcurrentHashMap vì nhiều virtual thread
 * (mỗi client một thread) cùng đọc/ghi sổ này.
 *
 * Mất khi Server tắt. Skeleton chấp nhận điều đó (client phải LOGIN lại).
 */
public class InMemorySessionRegistry implements SessionRegistry {

    private final Map<String, ClientSession> sessionsByToken = new ConcurrentHashMap<>();
    // Nếu một mã sinh viên đăng nhập lại, session mới ghi đè session cũ trong map này.
    private final Map<String, ClientSession> sessionsByMachineId = new ConcurrentHashMap<>();

    @Override
    public ClientSession createSession(UserAccount user, String examCode, ClientConnection connection) {
        String token = UUID.randomUUID().toString();
        ClientSession session = new ClientSession(
                token, user.id, user.username, user.fullName, user.role, examCode, connection);

        sessionsByToken.put(token, session);
        sessionsByMachineId.put(session.machineId, session);
        return session;
    }

    @Override
    public ClientSession findByToken(String token) {
        return sessionsByToken.get(token);
    }

    @Override
    public ClientSession findByMachineId(String machineId) {
        return sessionsByMachineId.get(machineId);
    }

    @Override
    public List<ClientSession> getOnlineStudents() {
        return findOnlineByRole(Role.STUDENT);
    }

    @Override
    public List<ClientSession> getOnlineTeachers() {
        return findOnlineByRole(Role.TEACHER);
    }

    @Override
    public void markOffline(ClientSession session) {
        session.online = false;
    }

    private List<ClientSession> findOnlineByRole(Role role) {
        List<ClientSession> result = new ArrayList<>();
        for (ClientSession session : sessionsByToken.values()) {
            if (session.online && session.role == role) {
                result.add(session);
            }
        }
        return result;
    }
}
