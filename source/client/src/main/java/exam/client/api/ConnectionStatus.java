// Owner: Nguoi1

package exam.client.api;

/** Trạng thái kết nối của client tới Server. */
public enum ConnectionStatus {
    DISCONNECTED,
    CONNECTED,
    /** Đang tự nối lại bằng token sau khi mất kết nối. */
    RECONNECTING
}
