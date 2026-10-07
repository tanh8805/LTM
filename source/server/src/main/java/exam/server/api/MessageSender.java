// Owner: Nguoi1

package exam.server.api;

import exam.common.protocol.Message;

/**
 * Gửi một message từ Server xuống client đang online.
 *
 * Ai cài đặt: Nguoi1 (exam.server.net.ConnectionMessageSender)
 * Ai gọi: Nguoi2 (gửi EXAM_START, EXAM_END, TIME_SYNC),
 *         Nguoi3 (gửi ALERT, ROOM_STATS, RULES_CONFIG),
 *         Nguoi4 (gửi ALERT khi ML báo đáng ngờ)
 *
 * machineId của sinh viên chính là mã sinh viên (ví dụ "SV001").
 */
public interface MessageSender {

    /** Gửi cho một máy. Trả về false nếu máy đó offline hoặc gửi lỗi. */
    boolean sendToMachine(String machineId, Message message);

    /** Gửi cho mọi sinh viên đang online. Trả về số máy đã gửi được. */
    int sendToAllStudents(Message message);

    /** Gửi cho mọi giáo viên đang online. Trả về số máy đã gửi được. */
    int sendToAllTeachers(Message message);
}
