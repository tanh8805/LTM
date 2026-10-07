// Owner: Nguoi1

package exam.common.model;

/** Loại vi phạm client phát hiện (field violationType trong message VIOLATION). */
public enum ViolationType {
    /** Rule 1: process mới xuất hiện, không nằm trong allowlist. */
    PROCESS_NOT_ALLOWED,
    /** Rule 1: process thuộc denylist (Zalo, Telegram, TeamViewer, ...). */
    PROCESS_DENYLIST,
    /** Rule 2: kết nối tới IP của domain bị chặn (chatgpt.com, ...). */
    BLOCKED_IP,
    /** Rule 3: có thiết bị USB mới. */
    USB_DEVICE,
    /** Rule 4: có network card mới (hotspot điện thoại, VPN, ...). */
    NETWORK_CARD,
    /** Rule 5: số lần mất focus cửa sổ thi vượt ngưỡng. */
    FOCUS_LOSS
}
