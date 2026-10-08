// Owner: Nguoi4

package exam.tools;

import exam.common.model.Metrics;

/**
 * Một dòng trong file trace: số liệu của một máy tại một thời điểm.
 *
 * label/scenario/category là CHÚ THÍCH để đánh giá, KHÔNG phải một phần của vector số liệu (Metrics).
 * Mô hình ML chỉ được nhìn thấy metrics.toVector().
 */
public class TraceRow {
    public static final String CATEGORY_NORMAL = "normal";
    /** Đoạn có kịch bản bất thường hệ thống được cố ý tạo ra (không có nghĩa là gian lận). */
    public static final String CATEGORY_ANOMALY = "anomaly";
    /** Đoạn có hoạt động bình thường nhưng khác baseline (mở ứng dụng thường dùng): không phải bất thường. */
    public static final String CATEGORY_BENIGN = "benign";

    /** Khóa nhóm các dòng của cùng một trace. Với trace v2 đây là sessionId. */
    public final String machineId;
    /** Epoch milli giây. */
    public final long time;
    /** 1 nếu dòng này nằm trong đoạn bất thường đã biết trước, 0 nếu không. */
    public final int label;
    /** Tên kịch bản ("normal", "CPU_LOAD", "upload-burst", ...). */
    public final String scenario;
    /** normal, anomaly hoặc benign. */
    public final String category;
    public final Metrics metrics;

    public TraceRow(String machineId, long time, int label, String scenario, Metrics metrics) {
        this(machineId, time, label, scenario, label == 1 ? CATEGORY_ANOMALY : CATEGORY_NORMAL, metrics);
    }

    public TraceRow(String machineId, long time, int label, String scenario, String category, Metrics metrics) {
        this.machineId = machineId;
        this.time = time;
        this.label = label;
        this.scenario = scenario;
        this.category = category;
        this.metrics = metrics;
    }
}
