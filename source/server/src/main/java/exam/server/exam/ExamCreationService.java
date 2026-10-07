// Owner: Nguoi2

package exam.server.exam;

import exam.server.db.ExamDao;
import exam.server.db.QuestionDao;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Tạo đề thi: chọn câu thủ công hoặc chọn ngẫu nhiên N câu từ ngân hàng. */
public class ExamCreationService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DURATION_MINUTES = 600;

    private final ExamDao examDao;
    private final QuestionDao questionDao;
    private final Random random = new Random();

    public ExamCreationService(ExamDao examDao, QuestionDao questionDao) {
        this.examDao = examDao;
        this.questionDao = questionDao;
    }

    /**
     * Tạo đề từ danh sách câu chọn thủ công. Trả về id đề.
     * Ném IllegalArgumentException nếu danh sách rỗng, trùng câu, hoặc có id không tồn tại.
     */
    public int createManual(String title, int durationMinutes, int teacherId, List<Integer> questionIds) {
        validateTitleAndDuration(title, durationMinutes);
        if (questionIds == null || questionIds.isEmpty()) {
            throw new IllegalArgumentException("Đề phải có ít nhất 1 câu hỏi");
        }

        Set<Integer> distinctIds = new HashSet<>(questionIds);
        if (distinctIds.size() != questionIds.size()) {
            throw new IllegalArgumentException("Danh sách câu hỏi bị trùng");
        }
        try {
            if (questionDao.findByIds(questionIds).size() != questionIds.size()) {
                throw new IllegalArgumentException("Có câu hỏi không tồn tại trong ngân hàng");
            }
            return examDao.insertExam(title.trim(), durationMinutes, teacherId, questionIds);
        } catch (SQLException e) {
            throw new IllegalStateException("Không tạo được đề thi", e);
        }
    }

    /** Tạo đề gồm N câu chọn ngẫu nhiên, không trùng. N lớn hơn số câu trong ngân hàng thì báo lỗi. */
    public int createRandom(String title, int durationMinutes, int teacherId, int count) {
        validateTitleAndDuration(title, durationMinutes);
        if (count <= 0) {
            throw new IllegalArgumentException("Số câu ngẫu nhiên phải lớn hơn 0");
        }
        try {
            List<Integer> allIds = new ArrayList<>(questionDao.findAllIds());
            if (count > allIds.size()) {
                throw new IllegalArgumentException(
                        "Ngân hàng chỉ có " + allIds.size() + " câu, không chọn được " + count + " câu");
            }
            Collections.shuffle(allIds, random);
            List<Integer> chosenIds = new ArrayList<>(allIds.subList(0, count));
            return examDao.insertExam(title.trim(), durationMinutes, teacherId, chosenIds);
        } catch (SQLException e) {
            throw new IllegalStateException("Không tạo được đề thi", e);
        }
    }

    private void validateTitleAndDuration(String title, int durationMinutes) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Tên đề không được rỗng");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Tên đề quá dài (tối đa " + MAX_TITLE_LENGTH + " ký tự)");
        }
        if (durationMinutes <= 0 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("Thời gian làm bài phải từ 1 đến " + MAX_DURATION_MINUTES + " phút");
        }
    }
}
