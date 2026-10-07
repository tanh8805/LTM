// Owner: Nguoi4

package exam.tools;

import exam.common.model.Metrics;

/** Một dòng trong file trace: số liệu của một máy tại một thời điểm, kèm nhãn để đánh giá. */
public class TraceRow {
    public final String machineId;
    /** Epoch milli giây. */
    public final long time;
    /** 1 nếu dòng này nằm trong đoạn gian lận (đã biết trước khi mô phỏng), 0 nếu bình thường. */
    public final int label;
    /** Tên kịch bản ("normal", "upload-burst", "recorded", ...). */
    public final String scenario;
    public final Metrics metrics;

    public TraceRow(String machineId, long time, int label, String scenario, Metrics metrics) {
        this.machineId = machineId;
        this.time = time;
        this.label = label;
        this.scenario = scenario;
        this.metrics = metrics;
    }
}
