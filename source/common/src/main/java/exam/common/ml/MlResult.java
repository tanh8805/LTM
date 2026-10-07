// Owner: Nguoi4

package exam.common.ml;

/** Kết quả ML Server (Chronos) cho một máy sau một lần chấm. */
public class MlResult {
    /** Đáng ngờ khi giá trị thật nằm ngoài q0.1 - q0.9 đủ 3 lần liên tiếp. */
    public boolean suspicious;
    /** Số lần liên tiếp liền trước (kể cả lần này) nằm ngoài khoảng q0.1 - q0.9. */
    public int consecutiveOutOfRange;
    /** Lý do hiển thị cho giáo viên, ví dụ "kbSent ngoài q0.1-q0.9 3 lần liên tiếp". */
    public String reason;

    public MlResult() {
    }

    public MlResult(boolean suspicious, int consecutiveOutOfRange, String reason) {
        this.suspicious = suspicious;
        this.consecutiveOutOfRange = consecutiveOutOfRange;
        this.reason = reason;
    }
}
