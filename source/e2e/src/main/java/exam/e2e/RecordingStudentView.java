// Owner: Nguoi2

package exam.e2e;

import exam.client.student.StudentView;
import exam.common.model.QuestionView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** StudentView không có giao diện: ghi lại mọi thứ StudentClient yêu cầu hiển thị để kiểm tra. */
public class RecordingStudentView implements StudentView {

    public final List<String> statuses = Collections.synchronizedList(new ArrayList<>());
    public final List<String> logins = Collections.synchronizedList(new ArrayList<>());
    public final List<Integer> heartbeats = Collections.synchronizedList(new ArrayList<>());
    public final List<Integer> savedQuestions = Collections.synchronizedList(new ArrayList<>());
    public final List<Integer> rejectedQuestions = Collections.synchronizedList(new ArrayList<>());
    public final List<String> ended = Collections.synchronizedList(new ArrayList<>());
    public final List<Integer> submitted = Collections.synchronizedList(new ArrayList<>());
    public final List<String> notices = Collections.synchronizedList(new ArrayList<>());
    public final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    public volatile List<QuestionView> questions;
    public volatile Map<Integer, Integer> savedAnswersAtStart = new HashMap<>();
    public volatile Map<Integer, Integer> restoredAnswers;
    public volatile int roomStatsCount = 0;

    @Override
    public void showStatus(String text) {
        statuses.add(text);
    }

    @Override
    public void showLogin(boolean ok, String message) {
        logins.add((ok ? "OK:" : "FAIL:") + message);
    }

    @Override
    public void showHeartbeat(int seq) {
        heartbeats.add(seq);
    }

    @Override
    public void showExam(String title, List<QuestionView> newQuestions, Map<Integer, Integer> savedAnswers) {
        questions = new ArrayList<>(newQuestions);
        savedAnswersAtStart = savedAnswers;
    }

    @Override
    public void showRestoredAnswers(Map<Integer, Integer> answers) {
        restoredAnswers = answers;
    }

    @Override
    public void showAnswerSaved(int questionId) {
        savedQuestions.add(questionId);
    }

    @Override
    public void showAnswerRejected(int questionId) {
        rejectedQuestions.add(questionId);
    }

    @Override
    public void showSubmitted(int answeredCount) {
        submitted.add(answeredCount);
    }

    @Override
    public void showExamEnded(String reason) {
        ended.add(reason);
    }

    @Override
    public void showNotice(String text) {
        notices.add(text);
    }

    @Override
    public void showRoomStats(String summary) {
        roomStatsCount++;
    }

    @Override
    public void appendLog(String text) {
        logs.add(text);
    }

    public boolean hasStatus(String text) {
        synchronized (statuses) {
            return statuses.contains(text);
        }
    }
}
