// Owner: Nguoi1

package exam.e2e;

import exam.client.StudentClient;
import exam.client.api.AnomalyScorer;
import exam.client.api.MetricsSource;
import exam.client.monitor.MonitoringLoop;
import exam.client.monitor.RuleEngineImpl;
import exam.client.net.TcpServerLink;
import exam.common.model.QuestionView;
import java.util.List;
import java.util.Map;

/** Một "máy sinh viên" đầy đủ nhưng không có cửa sổ: TcpServerLink + MonitoringLoop + StudentClient + view ghi lại. */
public class StudentHarness implements AutoCloseable {

    public final String studentCode;
    public final RecordingStudentView view = new RecordingStudentView();
    public final StudentClient client;
    public final TcpServerLink link;
    public final RuleEngineImpl ruleEngine;
    private final String examCode;

    public StudentHarness(String studentCode, String examCode, String host, int port, MetricsSource metricsSource,
                          AnomalyScorer anomalyScorer, int heartbeatIntervalMs) {
        this.studentCode = studentCode;
        this.examCode = examCode;
        this.link = new TcpServerLink(300, 100);
        this.ruleEngine = new RuleEngineImpl(metricsSource);
        MonitoringLoop monitoringLoop = new MonitoringLoop(link, metricsSource, ruleEngine, anomalyScorer);
        this.client = new StudentClient(link, monitoringLoop, ruleEngine, host, port, heartbeatIntervalMs);
        this.client.setView(view);
    }

    public void login(String password) {
        client.login(studentCode, password, examCode);
    }

    public boolean isLoggedIn() {
        return view.logins.contains("OK:OK");
    }

    /** Chọn đáp án ĐÚNG cho câu thứ index trong đề đã trộn, dựa vào bảng đáp án gốc (questionId -> nội dung đáp án đúng). */
    public boolean answerCorrectly(int index, Map<Integer, String> correctTextById) {
        QuestionView question = view.questions.get(index);
        return client.selectAnswer(question.questionId, question.options.indexOf(correctTextById.get(question.questionId)));
    }

    /** Chọn một đáp án SAI cho câu thứ index. */
    public boolean answerWrongly(int index, Map<Integer, String> correctTextById) {
        QuestionView question = view.questions.get(index);
        int correct = question.options.indexOf(correctTextById.get(question.questionId));
        return client.selectAnswer(question.questionId, (correct + 1) % 4);
    }

    public List<QuestionView> questions() {
        return view.questions;
    }

    @Override
    public void close() {
        client.close();
    }
}
