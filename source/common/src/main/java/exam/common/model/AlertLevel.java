// Owner: Nguoi1

package exam.common.model;

/**
 * Mức cảnh báo hiển thị cho giáo viên (trong message ALERT).
 *
 * YELLOW : cảnh báo nghi ngờ (WARNING) - lệch trung vị phòng từ room.warning.multiplier, ML báo bất thường,
 *          mất focus quá ngưỡng, mất kết nối.
 * RED    : vi phạm rõ ràng - luật 1-4 (process cấm/lạ, kết nối tới IP bị chặn, USB mới, network card mới),
 *          hoặc lệch trung vị phòng từ room.critical.multiplier.
 *
 * Bảng đầy đủ: docs/SPEC.md mục 7 và 10.
 */
public enum AlertLevel {
    YELLOW,
    RED
}
