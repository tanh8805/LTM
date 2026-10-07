// Owner: Nguoi1

package exam.common.model;

/** Một ca thi: đề thi nào, bắt đầu lúc nào, kéo dài bao lâu. */
public class ExamShift {
    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_ENDED = "ENDED";

    public int id;
    /** Mã ca thi mà sinh viên nhập khi đăng nhập (session code), ví dụ CA001. */
    public String code;
    public int examId;
    public String examTitle;
    /**
     * Giờ bắt đầu (epoch milli giây, đồng hồ Server).
     * Khi status = CREATED: giờ hẹn tự bắt đầu, 0 nghĩa là giáo viên bấm bắt đầu thủ công.
     * Khi status = RUNNING hoặc ENDED: giờ thực tế đã bắt đầu.
     */
    public long startTimeServer;
    public int durationSeconds;
    /** CREATED, RUNNING hoặc ENDED. */
    public String status;
    /** Luật giám sát của ca thi. */
    public MonitoringRules rules;

    public ExamShift() {
    }

    /** Giờ hết bài (epoch milli giây, đồng hồ Server). Chỉ có ý nghĩa khi ca đã bắt đầu. */
    public long getEndTimeServer() {
        return startTimeServer + durationSeconds * 1000L;
    }
}
