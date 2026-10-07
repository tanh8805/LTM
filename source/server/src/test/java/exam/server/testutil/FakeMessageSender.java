// Owner: Nguoi1

package exam.server.testutil;

import exam.common.protocol.Message;
import exam.server.api.MessageSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** MessageSender giả cho test: ghi lại mọi message "đã gửi" thay vì gửi qua mạng. */
public class FakeMessageSender implements MessageSender {

    public final List<Message> sentToTeachers = Collections.synchronizedList(new ArrayList<>());
    public final List<Message> sentToStudents = Collections.synchronizedList(new ArrayList<>());
    public final List<String> sentToMachineLog = Collections.synchronizedList(new ArrayList<>());
    public final List<Message> sentToMachines = Collections.synchronizedList(new ArrayList<>());

    @Override
    public boolean sendToMachine(String machineId, Message message) {
        sentToMachineLog.add(machineId + ":" + message.type);
        sentToMachines.add(message);
        return true;
    }

    @Override
    public int sendToAllStudents(Message message) {
        sentToStudents.add(message);
        return 1;
    }

    @Override
    public int sendToAllTeachers(Message message) {
        sentToTeachers.add(message);
        return 1;
    }

    /** Các message loại T đã gửi cho giáo viên. */
    public <T extends Message> List<T> teacherMessagesOfType(Class<T> type) {
        List<T> result = new ArrayList<>();
        synchronized (sentToTeachers) {
            for (Message message : sentToTeachers) {
                if (type.isInstance(message)) {
                    result.add(type.cast(message));
                }
            }
        }
        return result;
    }

    public <T extends Message> List<T> machineMessagesOfType(Class<T> type) {
        List<T> result = new ArrayList<>();
        synchronized (sentToMachines) {
            for (Message message : sentToMachines) {
                if (type.isInstance(message)) {
                    result.add(type.cast(message));
                }
            }
        }
        return result;
    }
}
