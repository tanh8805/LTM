// Owner: Nguoi3

package exam.server.monitor;

import exam.common.model.AlertLevel;
import exam.server.ServerConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thống kê của cả phòng: trung vị (median) và MAD (Median Absolute Deviation).
 *
 * Vì sao median và MAD thay vì trung bình và độ lệch chuẩn? Vì một máy gian lận có số liệu rất lớn
 * kéo trung bình và độ lệch chuẩn lên, làm chính nó trông "bình thường". Median và MAD gần như
 * không bị ảnh hưởng bởi vài giá trị ngoại lai.
 */
public final class RoomStats {

    /** Hệ số để MAD tương đương độ lệch chuẩn khi dữ liệu có phân phối chuẩn. */
    private static final double MAD_NORMALIZER = 1.4826;

    /** Một máy lệch khỏi phòng: bao nhiêu lần trung vị và ở mức cảnh báo nào. */
    public static class Deviation {
        public final double ratio;
        public final AlertLevel level;

        Deviation(double ratio, AlertLevel level) {
            this.ratio = ratio;
            this.level = level;
        }
    }

    private RoomStats() {
    }

    /** Trung vị. Danh sách rỗng trả về 0. */
    public static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    /** MAD = trung vị của |giá trị - trung vị|. */
    public static double mad(List<Double> values, double median) {
        List<Double> deviations = new ArrayList<>();
        for (double value : values) {
            deviations.add(Math.abs(value - median));
        }
        return median(deviations);
    }

    /**
     * Máy có giá trị `value` lệch khỏi phòng không?
     *
     * Chỉ xét lệch LÊN TRÊN (gửi/nhận nhiều hơn, CPU cao hơn...). Điều kiện:
     *   1. value / max(median, room.min.median) >= room.warning.multiplier  -> VÀNG
     *      value / max(median, room.min.median) >= room.critical.multiplier -> ĐỎ
     *   2. value cách median hơn room.mad.threshold lần MAD (đã chuẩn hóa), để bỏ qua dao động bình thường
     *      khi cả phòng vốn dĩ có độ phân tán lớn.
     *
     * Trả về null nếu không lệch.
     */
    public static Deviation check(double value, double median, double mad, ServerConfig config) {
        if (value <= median) {
            return null;
        }
        double scaledMad = MAD_NORMALIZER * mad;
        if (scaledMad > 0 && (value - median) / scaledMad < config.roomMadThreshold) {
            return null;
        }

        double denominator = Math.max(median, config.roomMinMedian);
        double ratio = value / denominator;
        if (ratio >= config.roomCriticalMultiplier) {
            return new Deviation(ratio, AlertLevel.RED);
        }
        if (ratio >= config.roomWarningMultiplier) {
            return new Deviation(ratio, AlertLevel.YELLOW);
        }
        return null;
    }
}
