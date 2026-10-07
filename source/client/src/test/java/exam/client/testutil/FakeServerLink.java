// Owner: Nguoi1

package exam.client.testutil;

import exam.client.api.ConnectionStatus;
import exam.client.api.ServerLink;
import exam.client.api.ServerLinkListener;
import exam.common.protocol.Message;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** ServerLink giả: ghi lại mọi message "đã gửi", và cho test tự "nhận" message hoặc đổi trạng thái kết nối. */
public class FakeServerLink implements ServerLink {

    public final List<Message> sent = Collections.synchronizedList(new ArrayList<>());
    private final List<ServerLinkListener> listeners = new CopyOnWriteArrayList<>();
    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    public volatile boolean connectShouldFail = false;
    public int connectCalls = 0;

    @Override
    public void connect(String host, int port) throws IOException {
        connectCalls++;
        if (connectShouldFail) {
            throw new IOException("Connection refused (giả lập)");
        }
        setStatus(ConnectionStatus.CONNECTED);
    }

    @Override
    public boolean send(Message message) {
        if (status != ConnectionStatus.CONNECTED) {
            return false;
        }
        sent.add(message);
        return true;
    }

    @Override
    public void addListener(ServerLinkListener listener) {
        listeners.add(listener);
    }

    @Override
    public ConnectionStatus getStatus() {
        return status;
    }

    public int resetCalls = 0;

    @Override
    public void resetConnection() {
        resetCalls++;
        setStatus(ConnectionStatus.RECONNECTING);
    }

    @Override
    public void close() {
        setStatus(ConnectionStatus.DISCONNECTED);
    }

    /** Giả lập Server gửi một message xuống client. */
    public void deliver(Message message) {
        for (ServerLinkListener listener : listeners) {
            listener.onMessage(message);
        }
    }

    /** Giả lập đổi trạng thái kết nối (mất mạng, nối lại). */
    public void setStatus(ConnectionStatus newStatus) {
        status = newStatus;
        for (ServerLinkListener listener : listeners) {
            listener.onStatusChanged(newStatus);
        }
    }

    public <T extends Message> List<T> sentOfType(Class<T> type) {
        List<T> result = new ArrayList<>();
        synchronized (sent) {
            for (Message message : sent) {
                if (type.isInstance(message)) {
                    result.add(type.cast(message));
                }
            }
        }
        return result;
    }
}
