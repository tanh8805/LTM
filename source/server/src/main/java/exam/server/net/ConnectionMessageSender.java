// Owner: Nguoi1

package exam.server.net;

import exam.common.protocol.Message;
import exam.server.api.MessageSender;
import exam.server.api.SessionRegistry;
import exam.server.session.ClientSession;

/** Gửi message tới client qua connection lưu trong session. */
public class ConnectionMessageSender implements MessageSender {

    private final SessionRegistry sessionRegistry;

    public ConnectionMessageSender(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public boolean sendToMachine(String machineId, Message message) {
        ClientSession session = sessionRegistry.findByMachineId(machineId);
        if (session == null || !session.online) {
            return false;
        }
        return session.connection.send(message);
    }

    @Override
    public int sendToAllStudents(Message message) {
        int sentCount = 0;
        for (ClientSession session : sessionRegistry.getOnlineStudents()) {
            if (session.connection.send(message)) {
                sentCount++;
            }
        }
        return sentCount;
    }

    @Override
    public int sendToAllTeachers(Message message) {
        int sentCount = 0;
        for (ClientSession session : sessionRegistry.getOnlineTeachers()) {
            if (session.connection.send(message)) {
                sentCount++;
            }
        }
        return sentCount;
    }
}
