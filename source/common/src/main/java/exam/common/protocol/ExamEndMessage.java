// Owner: Nguoi1

package exam.common.protocol;

/**
 * EXAM_END (Server → Client): Ca thi kết thúc (hết giờ hoặc giáo viên kết thúc). Server tự chốt bài.
 */
public class ExamEndMessage extends Message {
    /** TIME_UP, TEACHER_ENDED hoặc SUBMITTED */
    public String reason;

    public ExamEndMessage() {
        super(MessageType.EXAM_END);
    }

    public ExamEndMessage(String reason) {
        this();
        this.reason = reason;
    }
}
