// Owner: Nguoi2

package exam.server.exam;

import exam.common.model.Question;
import exam.common.model.QuestionView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Đề thi của MỘT sinh viên sau khi trộn thứ tự câu hỏi và thứ tự đáp án.
 *
 * Việc trộn chỉ phụ thuộc vào (danh sách câu hỏi của đề, seed). Cùng seed luôn ra cùng một đề,
 * nên Server không cần lưu đề đã trộn: chỉ lưu seed (bảng shift_students), khi sinh viên
 * đăng nhập lại hoặc nối lại thì dựng lại đúng đề cũ.
 *
 * Hai sinh viên khác seed thường nhận thứ tự khác nhau (không đảm bảo khác hoàn toàn).
 */
public class ExamPaper {

    private final List<Question> questions = new ArrayList<>();
    // optionOrders[i][vị trí hiển thị] = vị trí đáp án đó trong câu hỏi gốc
    private final List<int[]> optionOrders = new ArrayList<>();

    private ExamPaper() {
    }

    public static ExamPaper build(List<Question> examQuestions, long seed) {
        // Sắp theo id trước để kết quả không phụ thuộc thứ tự danh sách đầu vào.
        List<Question> ordered = new ArrayList<>(examQuestions);
        ordered.sort(Comparator.comparingInt(question -> question.id));

        Random random = new Random(seed);
        Collections.shuffle(ordered, random);

        ExamPaper paper = new ExamPaper();
        for (Question question : ordered) {
            List<Integer> order = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
            Collections.shuffle(order, random);
            paper.questions.add(question);
            paper.optionOrders.add(new int[] {order.get(0), order.get(1), order.get(2), order.get(3)});
        }
        return paper;
    }

    public int size() {
        return questions.size();
    }

    /** Đề gửi cho sinh viên: KHÔNG có đáp án đúng. */
    public List<QuestionView> toViews() {
        List<QuestionView> views = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            int[] order = optionOrders.get(i);

            List<String> shuffledOptions = new ArrayList<>();
            for (int displayIndex = 0; displayIndex < 4; displayIndex++) {
                shuffledOptions.add(question.options.get(order[displayIndex]));
            }
            views.add(new QuestionView(question.id, question.content, shuffledOptions));
        }
        return views;
    }

    /** true nếu questionId thuộc đề này. */
    public boolean containsQuestion(int questionId) {
        for (Question question : questions) {
            if (question.id == questionId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Đếm số câu đúng. answers: questionId -> choice (vị trí trong thứ tự ĐÃ TRỘN).
     * Mỗi choice được quy đổi về vị trí trong câu hỏi gốc rồi so với đáp án đúng.
     */
    public int countCorrect(Map<Integer, Integer> answers) {
        int correctCount = 0;
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            Integer choice = answers.get(question.id);
            if (choice == null || choice < 0 || choice > 3) {
                continue;
            }
            int originalIndex = optionOrders.get(i)[choice];
            if (originalIndex == question.correctIndex) {
                correctCount++;
            }
        }
        return correctCount;
    }
}
