// Owner: Nguoi1

package exam.common.model;

/**
 * Mức cảnh báo hiển thị cho giáo viên (trong message ALERT).
 *
 * YELLOW : cảnh báo nghi ngờ (WARNING) - ví dụ lệch trung vị phòng, ML báo bất thường, mất focus quá ngưỡng.
 * RED    : vi phạm rõ ràng - ví dụ mở process cấm, kết nối tới IP bị chặn, cắm USB.
 *
 * TODO(Nguoi3): Chốt chính xác luật nào cho YELLOW, luật nào cho RED và ghi vào docs/SPEC.md.
 */
public enum AlertLevel {
    YELLOW,
    RED
}
