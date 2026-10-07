// Owner: Nguoi1

package exam.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import exam.client.api.AnomalyScorer;
import exam.client.api.ConnectionStatus;
import exam.client.monitor.MonitoringLoop;
import exam.client.monitor.RuleEngineImpl;
import exam.client.student.StudentView;
import exam.client.testutil.FakeMetricsSource;
import exam.client.testutil.FakeServerLink;
import exam.client.testutil.Wait;
import exam.common.model.MlMode;
import exam.common.model.MonitoringRules;
import exam.common.model.QuestionView;
import exam.common.model.RateMode;
import exam.common.model.Role;
import exam.common.model.ViolationType;
import exam.common.protocol.AnswerAckMessage;
import exam.common.protocol.AnswerMessage;
import exam.common.protocol.ErrorMessage;
import exam.common.protocol.ExamEndMessage;
import exam.common.protocol.ExamStartMessage;
import exam.common.protocol.HeartbeatAckMessage;
import exam.common.protocol.HeartbeatMessage;
import exam.common.protocol.LoginFailMessage;
import exam.common.protocol.LoginMessage;
import exam.common.protocol.LoginOkMessage;
import exam.common.protocol.MetricsDetailMessage;
import exam.common.protocol.NoticeMessage;
import exam.common.protocol.ReconnectMessage;
import exam.common.protocol.ReconnectOkMessage;
import exam.common.protocol.RulesConfigMessage;
import exam.common.protocol.SetRateMessage;
import exam.common.protocol.SubmitMessage;
import exam.common.protocol.SubmitOkMessage;
import exam.common.protocol.ViolationMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Test logic ứng dụng sinh viên (đăng nhập, làm bài, reconnect, đồng hồ Server, giám sát) không cần màn hình hay Server thật. */
class StudentClientTest {

    /** View giả ghi lại những gì StudentClient yêu cầu hiển thị. */
    private static class RecordingView implements StudentView {
        final List<String> statuses = Collections.synchronizedList(new ArrayList<>());
        final List<String> logins = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> heartbeats = Collections.synchronizedList(new ArrayList<>());
        final List<String> exams = Collections.synchronizedList(new ArrayList<>());
        volatile Map<Integer, Integer> examSavedAnswers;
        volatile Map<Integer, Integer> restoredAnswers;
        final List<Integer> savedQuestions = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> rejectedQuestions = Collections.synchronizedList(new ArrayList<>());
        final List<String> ended = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> submitted = Collections.synchronizedList(new ArrayList<>());
        final List<String> notices = Collections.synchronizedList(new ArrayList<>());
        final List<String> logs = Collections.synchronizedList(new ArrayList<>());

        public void showStatus(String text) {
            statuses.add(text);
        }

        public void showLogin(boolean ok, String message) {
            logins.add((ok ? "OK:" : "FAIL:") + message);
        }

        public void showHeartbeat(int seq) {
            heartbeats.add(seq);
        }

        public void showExam(String title, List<QuestionView> questions, Map<Integer, Integer> savedAnswers) {
            exams.add(title + ":" + questions.size());
            examSavedAnswers = savedAnswers;
        }

        public void showRestoredAnswers(Map<Integer, Integer> answers) {
            restoredAnswers = answers;
        }

        public void showAnswerSaved(int questionId) {
            savedQuestions.add(questionId);
        }

        public void showAnswerRejected(int questionId) {
            rejectedQuestions.add(questionId);
        }

        public void showSubmitted(int answeredCount) {
            submitted.add(answeredCount);
        }

        public void showExamEnded(String reason) {
            ended.add(reason);
        }

        public void showNotice(String text) {
            notices.add(text);
        }

        public void showRoomStats(String summary) {
        }

        public void appendLog(String text) {
            logs.add(text);
        }
    }

    private static class SimpleScorer implements AnomalyScorer {
        public void fit(List<double[]> samples) {
        }

        public double score(double[] sample) {
            return 0.4;
        }
    }

    private FakeServerLink link;
    private FakeMetricsSource source;
    private RuleEngineImpl ruleEngine;
    private MonitoringLoop monitoringLoop;
    private RecordingView view;
    private StudentClient client;

    @BeforeEach
    void setUp() {
        link = new FakeServerLink();
        source = new FakeMetricsSource();
        ruleEngine = new RuleEngineImpl(source, domain -> List.of());
        monitoringLoop = new MonitoringLoop(link, source, ruleEngine, new SimpleScorer());
        view = new RecordingView();
        client = new StudentClient(link, monitoringLoop, ruleEngine, "localhost", 5000, 100);
        client.setView(view);
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    private void loginSuccessfully() {
        client.login("SV001", "123456", "CA001");
        assertTrue(Wait.until(() -> !link.sentOfType(LoginMessage.class).isEmpty(), 3000));
        link.deliver(new LoginOkMessage("token-1", 1, "Nguyen Van An", Role.STUDENT, System.currentTimeMillis()));
    }

    private List<QuestionView> twoQuestions() {
        return List.of(new QuestionView(11, "Q1", List.of("a", "b", "c", "d")),
                new QuestionView(12, "Q2", List.of("a", "b", "c", "d")));
    }

    private void startExam() {
        link.deliver(new ExamStartMessage(twoQuestions(), System.currentTimeMillis() + 600_000, new HashMap<>(), "De thu"));
    }

    // ------------------------------------------------------------------
    // Đăng nhập, heartbeat
    // ------------------------------------------------------------------

    @Test
    void loginConnectsAndSendsLoginMessageWithStudentRoleAndExamCode() {
        client.login("SV001", "123456", "CA001");

        assertTrue(Wait.until(() -> !link.sentOfType(LoginMessage.class).isEmpty(), 3000));
        LoginMessage login = link.sentOfType(LoginMessage.class).get(0);
        assertEquals("SV001", login.username);
        assertEquals("123456", login.password);
        assertEquals(Role.STUDENT, login.role);
        assertEquals("CA001", login.examCode);
        assertEquals(1, link.connectCalls);
    }

    @Test
    void loginOkIsShownAndHeartbeatsStartWithIncreasingSeq() {
        loginSuccessfully();

        assertEquals(List.of("OK:OK"), view.logins);
        assertTrue(Wait.until(() -> link.sentOfType(HeartbeatMessage.class).size() >= 3, 3000));
        List<HeartbeatMessage> heartbeats = link.sentOfType(HeartbeatMessage.class);
        assertEquals(1, heartbeats.get(0).seq);
        assertEquals(2, heartbeats.get(1).seq);
        assertNotNull(heartbeats.get(0).summary);
    }

    @Test
    void heartbeatAckIsShown() {
        loginSuccessfully();

        link.deliver(new HeartbeatAckMessage(5, System.currentTimeMillis()));

        assertEquals(List.of(5), view.heartbeats);
    }

    @Test
    void loginFailIsShownAndNoHeartbeatIsSent() throws Exception {
        client.login("SV001", "sai", "CA001");
        assertTrue(Wait.until(() -> !link.sentOfType(LoginMessage.class).isEmpty(), 3000));

        link.deliver(new LoginFailMessage("Sai tài khoản hoặc mật khẩu"));
        Thread.sleep(400);

        assertEquals(List.of("FAIL:Sai tài khoản hoặc mật khẩu"), view.logins);
        assertTrue(link.sentOfType(HeartbeatMessage.class).isEmpty());
    }

    @Test
    void connectionFailureIsReportedToTheView() {
        link.connectShouldFail = true;

        client.login("SV001", "123456", "CA001");

        assertTrue(Wait.until(() -> !view.logins.isEmpty(), 3000));
        assertTrue(view.logins.get(0).startsWith("FAIL:Không kết nối được"));
        assertTrue(link.sentOfType(LoginMessage.class).isEmpty());
    }

    // ------------------------------------------------------------------
    // Làm bài
    // ------------------------------------------------------------------

    @Test
    void examStartShowsQuestionsAndAnswersAreSentImmediately() {
        loginSuccessfully();
        startExam();

        assertEquals(List.of("De thu:2"), view.exams);
        assertTrue(client.selectAnswer(11, 2));
        assertTrue(client.selectAnswer(12, 0));

        List<AnswerMessage> answers = link.sentOfType(AnswerMessage.class);
        assertEquals(2, answers.size());
        assertEquals(1, answers.get(0).seq);
        assertEquals(11, answers.get(0).questionId);
        assertEquals(2, answers.get(0).choice);
        assertEquals(2, answers.get(1).seq);
    }

    @Test
    void answerBeforeExamStartsIsIgnored() {
        loginSuccessfully();

        assertFalse(client.selectAnswer(11, 1));
        assertTrue(link.sentOfType(AnswerMessage.class).isEmpty());
    }

    @Test
    void answerAckIsShownAndRejectedAnswerIsReported() {
        loginSuccessfully();
        startExam();
        client.selectAnswer(11, 1);
        client.selectAnswer(12, 1);

        link.deliver(new AnswerAckMessage(1, 11, true));
        link.deliver(new AnswerAckMessage(2, 12, false));

        assertEquals(List.of(11), view.savedQuestions);
        assertEquals(List.of(12), view.rejectedQuestions);
    }

    @Test
    void examStartWithSavedAnswersRestoresThemInTheView() {
        loginSuccessfully();
        Map<Integer, Integer> saved = new HashMap<>();
        saved.put(11, 3);

        link.deliver(new ExamStartMessage(twoQuestions(), System.currentTimeMillis() + 600_000, saved, "De thu"));

        assertEquals(3, view.examSavedAnswers.get(11));
    }

    @Test
    void submitSendsSubmitAndSubmitOkLocksTheExam() {
        loginSuccessfully();
        startExam();

        assertTrue(client.submit());
        assertEquals(1, link.sentOfType(SubmitMessage.class).size());

        link.deliver(new SubmitOkMessage(2));

        assertEquals(List.of(2), view.submitted);
        assertFalse(client.selectAnswer(11, 1), "sau khi nộp không gửi ANSWER nữa");
        assertFalse(client.submit());
    }

    @Test
    void submitIsRefusedBeforeExamStarts() {
        loginSuccessfully();

        assertFalse(client.submit());
        assertTrue(link.sentOfType(SubmitMessage.class).isEmpty());
    }

    @Test
    void examEndLocksTheExamAndStopsMonitoring() {
        loginSuccessfully();
        startExam();

        link.deliver(new ExamEndMessage("TIME_UP"));

        assertEquals(List.of("TIME_UP"), view.ended);
        assertFalse(client.selectAnswer(11, 1));
    }

    @Test
    void noticeFromTeacherIsShown() {
        loginSuccessfully();

        link.deliver(new NoticeMessage("ALL", "Còn 5 phút"));

        assertEquals(List.of("Còn 5 phút"), view.notices);
    }

    // ------------------------------------------------------------------
    // Đồng hồ: chỉ tin đồng hồ Server
    // ------------------------------------------------------------------

    @Test
    void remainingTimeFollowsTheServerClockNotTheLocalClock() {
        // Đồng hồ máy sinh viên chậm hơn Server đúng 1 giờ
        long serverNow = System.currentTimeMillis() + 3_600_000;
        client.login("SV001", "123456", "CA001");
        assertTrue(Wait.until(() -> !link.sentOfType(LoginMessage.class).isEmpty(), 3000));
        link.deliver(new LoginOkMessage("t", 1, "A", Role.STUDENT, serverNow));

        link.deliver(new ExamStartMessage(twoQuestions(), serverNow + 120_000, new HashMap<>(), "De"));

        long remaining = client.getRemainingSeconds();
        assertTrue(remaining >= 115 && remaining <= 120, "còn lại " + remaining + " giây (theo giờ Server)");
    }

    @Test
    void timeSyncCorrectsTheClock() {
        loginSuccessfully();
        long serverNow = System.currentTimeMillis();
        link.deliver(new ExamStartMessage(twoQuestions(), serverNow + 300_000, new HashMap<>(), "De"));

        // Server báo đồng hồ Server đang nhanh hơn 100 giây so với đồng hồ máy
        link.deliver(new exam.common.protocol.TimeSyncMessage(System.currentTimeMillis() + 100_000, 200));

        long remaining = client.getRemainingSeconds();
        assertTrue(remaining >= 195 && remaining <= 200, "còn lại " + remaining);
    }

    @Test
    void remainingTimeIsUnknownBeforeExam() {
        assertEquals(-1, client.getRemainingSeconds());
    }

    @Test
    void clientDoesNotEndTheExamByItselfWhenLocalTimeRunsOut() {
        loginSuccessfully();
        link.deliver(new ExamStartMessage(twoQuestions(), System.currentTimeMillis() - 5000, new HashMap<>(), "De"));

        assertEquals(0, client.getRemainingSeconds());
        // Dù đồng hồ về 0, client vẫn chưa tự khóa bài: chỉ EXAM_END của Server mới khóa
        assertTrue(client.selectAnswer(11, 1));
        assertTrue(view.ended.isEmpty());
    }

    // ------------------------------------------------------------------
    // Reconnect
    // ------------------------------------------------------------------

    @Test
    void connectionLossStopsHeartbeatsAndReconnectSendsTokenThenResumes() throws Exception {
        loginSuccessfully();
        assertTrue(Wait.until(() -> !link.sentOfType(HeartbeatMessage.class).isEmpty(), 3000));

        link.setStatus(ConnectionStatus.RECONNECTING);
        assertTrue(view.statuses.get(view.statuses.size() - 1).contains("Disconnected"));
        int heartbeatsWhileDown = link.sentOfType(HeartbeatMessage.class).size();
        Thread.sleep(400);
        assertEquals(heartbeatsWhileDown, link.sentOfType(HeartbeatMessage.class).size(), "không gửi heartbeat khi mất kết nối");

        link.setStatus(ConnectionStatus.CONNECTED); // TCP đã nối lại
        List<ReconnectMessage> reconnects = link.sentOfType(ReconnectMessage.class);
        assertEquals(1, reconnects.size());
        assertEquals("token-1", reconnects.get(0).token);
        assertEquals(1, link.sentOfType(LoginMessage.class).size(), "nối lại bằng token, không đăng nhập mới");

        // Server chưa chấp nhận lại thì chưa được gửi heartbeat
        Thread.sleep(300);
        assertEquals(heartbeatsWhileDown, link.sentOfType(HeartbeatMessage.class).size());

        link.deliver(new ReconnectOkMessage(System.currentTimeMillis() + 600_000, new HashMap<>(), System.currentTimeMillis()));
        assertTrue(Wait.until(() -> link.sentOfType(HeartbeatMessage.class).size() > heartbeatsWhileDown, 3000));
        assertEquals("Reconnected", view.statuses.get(view.statuses.size() - 1));
    }

    @Test
    void reconnectRestoresAnswersAndResendsUnacknowledgedOnes() {
        loginSuccessfully();
        startExam();
        client.selectAnswer(11, 1);                                  // seq 1: sẽ được xác nhận
        link.deliver(new AnswerAckMessage(1, 11, true));
        client.selectAnswer(12, 2);                                  // seq 2: đã gửi nhưng chưa được xác nhận

        link.setStatus(ConnectionStatus.RECONNECTING);
        assertTrue(client.selectAnswer(11, 3));                      // seq 3: chọn lúc đang mất mạng, chưa gửi được
        assertEquals(2, link.sentOfType(AnswerMessage.class).size());

        link.setStatus(ConnectionStatus.CONNECTED);
        Map<Integer, Integer> serverAnswers = new HashMap<>();
        serverAnswers.put(11, 1);                                    // Server chỉ có đáp án seq 1
        link.deliver(new ReconnectOkMessage(System.currentTimeMillis() + 600_000, serverAnswers, System.currentTimeMillis()));

        assertEquals(1, view.restoredAnswers.get(11), "đáp án Server đang giữ được đưa lên giao diện");
        List<AnswerMessage> answers = link.sentOfType(AnswerMessage.class);
        assertEquals(4, answers.size(), "seq 2 và 3 được gửi lại sau khi nối lại");
        assertEquals(2, answers.get(2).seq);
        assertEquals(3, answers.get(3).seq);
        assertEquals(1, link.sentOfType(ReconnectMessage.class).get(0).lastAnswerSeq);
    }

    @Test
    void rejectedTokenFallsBackToLoginAgain() {
        loginSuccessfully();
        link.setStatus(ConnectionStatus.RECONNECTING);
        link.setStatus(ConnectionStatus.CONNECTED);
        assertEquals(1, link.sentOfType(ReconnectMessage.class).size());

        link.deliver(new ErrorMessage("INVALID_TOKEN", "Token không hợp lệ"));

        assertEquals(2, link.sentOfType(LoginMessage.class).size(), "phải đăng nhập lại bằng mật khẩu");
        link.deliver(new LoginOkMessage("token-2", 1, "A", Role.STUDENT, System.currentTimeMillis()));
        link.setStatus(ConnectionStatus.RECONNECTING);
        link.setStatus(ConnectionStatus.CONNECTED);
        assertEquals("token-2", link.sentOfType(ReconnectMessage.class).get(1).token);
    }

    @Test
    void reconnectAfterExamEndedLocksTheExam() {
        loginSuccessfully();
        startExam();
        link.setStatus(ConnectionStatus.RECONNECTING);
        link.setStatus(ConnectionStatus.CONNECTED);
        link.deliver(new ReconnectOkMessage(0, new HashMap<>(), System.currentTimeMillis()));

        link.deliver(new ExamEndMessage("TIME_UP"));

        assertEquals(List.of("TIME_UP"), view.ended);
        assertFalse(client.selectAnswer(11, 0));
    }

    // ------------------------------------------------------------------
    // Giám sát: RULES_CONFIG, SET_RATE, focus
    // ------------------------------------------------------------------

    private MonitoringRules fastRules() {
        MonitoringRules rules = MonitoringRules.createDefault();
        rules.metricsIntervalSeconds = 1;
        rules.focusLossThreshold = 2;
        return rules;
    }

    @Test
    void rulesConfigStartsMonitoringAndViolationsAreSentOnHeartbeatTick() {
        loginSuccessfully();
        link.deliver(new RulesConfigMessage(fastRules(), MlMode.NONE));
        assertTrue(Wait.until(() -> view.logs.stream().anyMatch(line -> line.startsWith("RULES_CONFIG")), 3000));
        // MonitoringLoop.start() chạy trên thread riêng: chờ nó chụp xong mốc ban đầu rồi mới cắm "USB mới".
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        source.usbDevices.add("SanDisk [x]");

        assertTrue(Wait.until(() -> !link.sentOfType(ViolationMessage.class).isEmpty(), 5000));
        ViolationMessage violation = link.sentOfType(ViolationMessage.class).get(0);
        assertEquals(ViolationType.USB_DEVICE, violation.violationType);
    }

    @Test
    void focusLostIsCountedAndReportedAfterThreshold() throws Exception {
        loginSuccessfully();
        link.deliver(new RulesConfigMessage(fastRules(), MlMode.NONE));
        Thread.sleep(500);

        client.onFocusLost();
        client.onFocusLost();

        assertTrue(Wait.until(() -> link.sentOfType(ViolationMessage.class).stream()
                .anyMatch(v -> v.violationType == ViolationType.FOCUS_LOSS), 5000));
        assertTrue(Wait.until(() -> link.sentOfType(HeartbeatMessage.class).stream()
                .anyMatch(h -> h.summary.focusLostCount == 2), 5000));
    }

    @Test
    void setRateHighStartsDetailMessagesAndNormalStopsThem() throws Exception {
        loginSuccessfully();

        link.deliver(new SetRateMessage(RateMode.HIGH, 50));
        assertTrue(Wait.until(() -> link.sentOfType(MetricsDetailMessage.class).size() >= 2, 3000));

        link.deliver(new SetRateMessage(RateMode.NORMAL, 10_000));
        Thread.sleep(200);
        int count = link.sentOfType(MetricsDetailMessage.class).size();
        Thread.sleep(300);
        assertEquals(count, link.sentOfType(MetricsDetailMessage.class).size());
    }

    @Test
    void closingTheClientStopsEverything() throws Exception {
        loginSuccessfully();
        assertTrue(Wait.until(() -> !link.sentOfType(HeartbeatMessage.class).isEmpty(), 3000));

        client.close();
        Thread.sleep(300);
        int count = link.sentOfType(HeartbeatMessage.class).size();
        Thread.sleep(400);

        assertEquals(count, link.sentOfType(HeartbeatMessage.class).size());
        assertEquals(ConnectionStatus.DISCONNECTED, link.getStatus());
    }
}
