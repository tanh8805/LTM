// Owner: Nguoi2

package exam.server.exam;

import exam.common.model.ExamShift;
import exam.common.model.Question;
import exam.server.db.AttemptDao;
import exam.server.db.CandidateRecord;
import exam.server.db.ExamDao;
import exam.server.db.QuestionDao;
import exam.server.db.ShiftDao;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bài làm của sinh viên: lưu đáp án, nộp bài, chấm điểm.
 *
 * Mọi quyết định (còn giờ không, đã nộp chưa, đáp án nào đúng, bao nhiêu điểm) do Server đưa ra
 * dựa trên database; không bao giờ tin dữ liệu "điểm", "giờ" hay "đáp án đúng" từ client.
 *
 * Các method thay đổi dữ liệu đều synchronized trên cùng một lock với ShiftService,
 * để việc "sinh viên nộp bài" và "Server tự chốt bài khi hết giờ" không chạy chồng lên nhau.
 */
public class AttemptService {

    private final ShiftDao shiftDao;
    private final ExamDao examDao;
    private final QuestionDao questionDao;
    private final AttemptDao attemptDao;
    private final Object lock;

    public AttemptService(ShiftDao shiftDao, ExamDao examDao, QuestionDao questionDao,
                          AttemptDao attemptDao, Object lock) {
        this.shiftDao = shiftDao;
        this.examDao = examDao;
        this.questionDao = questionDao;
        this.attemptDao = attemptDao;
        this.lock = lock;
    }

    /** Dựng đề đã trộn của một thí sinh (từ seed đã lưu). */
    public ExamPaper buildPaper(ExamShift shift, CandidateRecord candidate) {
        try {
            List<Integer> questionIds = examDao.findQuestionIds(shift.examId);
            List<Question> questions = questionDao.findByIds(questionIds);
            return ExamPaper.build(questions, candidate.shuffleSeed);
        } catch (SQLException e) {
            throw new IllegalStateException("Không dựng được đề thi", e);
        }
    }

    public Map<Integer, Integer> getSavedAnswers(int studentId, String examCode) {
        try {
            ExamShift shift = shiftDao.findByCode(examCode);
            if (shift == null) {
                return new HashMap<>();
            }
            return attemptDao.findAnswers(shift.id, studentId);
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc được đáp án đã lưu", e);
        }
    }

    public boolean hasSubmitted(int studentId, String examCode) {
        try {
            ExamShift shift = shiftDao.findByCode(examCode);
            return shift != null && attemptDao.findResult(shift.id, studentId) != null;
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc được kết quả", e);
        }
    }

    /**
     * Lưu một đáp án. Trả về false (và ghi log lý do) nếu bị từ chối:
     * ca không chạy, hết giờ theo đồng hồ Server, không phải thí sinh, đã nộp, câu hỏi không thuộc đề, choice sai.
     */
    public boolean saveAnswer(int studentId, String examCode, int questionId, int choice) {
        synchronized (lock) {
            try {
                ExamShift shift = shiftDao.findByCode(examCode);
                String rejectReason = findAnswerRejectReason(shift, studentId, questionId, choice);
                if (rejectReason != null) {
                    System.out.println("[Exam] ANSWER bị từ chối (studentId=" + studentId + ", ca=" + examCode + "): " + rejectReason);
                    return false;
                }
                attemptDao.saveAnswer(shift.id, studentId, questionId, choice, System.currentTimeMillis());
                return true;
            } catch (SQLException e) {
                throw new IllegalStateException("Không lưu được đáp án", e);
            }
        }
    }

    /** Trả về lý do từ chối, hoặc null nếu đáp án hợp lệ. */
    private String findAnswerRejectReason(ExamShift shift, int studentId, int questionId, int choice) throws SQLException {
        if (shift == null) {
            return "ca thi không tồn tại";
        }
        if (!ExamShift.STATUS_RUNNING.equals(shift.status)) {
            return "ca thi không đang diễn ra";
        }
        if (System.currentTimeMillis() >= shift.getEndTimeServer()) {
            return "đã hết giờ";
        }
        if (shiftDao.findCandidate(shift.id, studentId) == null) {
            return "không nằm trong danh sách thí sinh";
        }
        if (attemptDao.findResult(shift.id, studentId) != null) {
            return "đã nộp bài";
        }
        if (choice < 0 || choice > 3) {
            return "choice phải từ 0 đến 3";
        }
        if (!examDao.findQuestionIds(shift.examId).contains(questionId)) {
            return "câu hỏi không thuộc đề";
        }
        return null;
    }

    /**
     * Sinh viên nộp bài. Trả về số câu đã trả lời.
     * Nộp lần hai (hoặc sau khi Server đã chốt) trả về số câu đã ghi nhận, kết quả không đổi.
     */
    public int submit(int studentId, String examCode) {
        synchronized (lock) {
            try {
                ExamShift shift = shiftDao.findByCode(examCode);
                if (shift == null) {
                    return 0;
                }
                AttemptDao.ResultRow existing = attemptDao.findResult(shift.id, studentId);
                if (existing != null) {
                    return existing.answeredCount;
                }
                CandidateRecord candidate = shiftDao.findCandidate(shift.id, studentId);
                if (candidate == null || !ExamShift.STATUS_RUNNING.equals(shift.status)) {
                    return 0;
                }
                return gradeAndStore(shift, candidate, false).answeredCount;
            } catch (SQLException e) {
                throw new IllegalStateException("Không nộp được bài", e);
            }
        }
    }

    /** Server tự chốt bài của mọi thí sinh chưa nộp (hết giờ hoặc giáo viên kết thúc ca). */
    public int autoSubmitAll(ExamShift shift) {
        synchronized (lock) {
            try {
                int autoSubmittedCount = 0;
                for (CandidateRecord candidate : shiftDao.findCandidates(shift.id)) {
                    if (attemptDao.findResult(shift.id, candidate.studentId) == null) {
                        gradeAndStore(shift, candidate, true);
                        autoSubmittedCount++;
                    }
                }
                return autoSubmittedCount;
            } catch (SQLException e) {
                throw new IllegalStateException("Không tự chốt được bài", e);
            }
        }
    }

    /** Chấm điểm (thang 10) rồi ghi vào bảng results. */
    private AttemptDao.ResultRow gradeAndStore(ExamShift shift, CandidateRecord candidate, boolean auto) throws SQLException {
        ExamPaper paper = buildPaper(shift, candidate);
        Map<Integer, Integer> answers = attemptDao.findAnswers(shift.id, candidate.studentId);

        int correctCount = paper.countCorrect(answers);
        int totalQuestions = paper.size();
        double score = 0;
        if (totalQuestions > 0) {
            score = Math.round(correctCount * 10.0 / totalQuestions * 100.0) / 100.0;
        }
        long now = System.currentTimeMillis();
        attemptDao.insertResult(shift.id, candidate.studentId, score, correctCount, totalQuestions,
                answers.size(), auto, now);

        AttemptDao.ResultRow row = new AttemptDao.ResultRow();
        row.studentId = candidate.studentId;
        row.submitted = true;
        row.score = score;
        row.correctCount = correctCount;
        row.totalQuestions = totalQuestions;
        row.answeredCount = answers.size();
        System.out.println("[Exam] " + candidate.username + " " + (auto ? "bị Server tự chốt" : "nộp bài")
                + ": " + correctCount + "/" + totalQuestions + " đúng, điểm " + score);
        return row;
    }
}
