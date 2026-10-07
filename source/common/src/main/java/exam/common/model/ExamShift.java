// Owner: Nguoi1

package exam.common.model;

/** Một ca thi: đề thi nào, bắt đầu lúc nào, kéo dài bao lâu. */
public class ExamShift {
    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_ENDED = "ENDED";

    public int id;
    /** Mã ca thi mà sinh viên nhập khi đăng nhập, ví dụ CA001. */
    public String code;
    public int examId;
    /** Giờ bắt đầu, tính bằng epoch milli giây theo đồng hồ Server. */
    public long startTimeServer;
    public int durationMinutes;
    /** CREATED, RUNNING hoặc ENDED. */
    public String status;

    public ExamShift() {
    }
}
