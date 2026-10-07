// Owner: Nguoi1

package exam.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.client.api.AnomalyScorer;
import exam.client.api.MetricsSource;
import exam.common.model.AlertLevel;
import exam.common.model.QuestionView;
import exam.common.protocol.AlertMessage;
import exam.common.protocol.ResponseMessage;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Kịch bản end-to-end 20 bước, chạy trên một Server THẬT qua TCP:
 *
 *   1  Server chạy          2  Teacher đăng nhập     3  Student đăng nhập
 *   4  Teacher tạo đề, ca    5  Student nhận đề (trộn riêng)   6  Student trả lời (ANSWER/ACK)
 *   7  Heartbeat chạy        8  Monitoring chạy       9  Student nộp bài
 *   10 Server chấm điểm      11 Teacher xem điểm + CSV
 *   12 Student mất mạng      13 Server phát hiện offline   14 Student nối lại   15 Trạng thái được khôi phục
 *   16 Vi phạm giám sát      17 ALERT tới Teacher     18 Server gọi ML (ml-service)
 *   19 ML timeout, Server vẫn chạy                      20 Ca thi kết thúc (Server tự chốt bài)
 *
 * Mọi thứ phụ thuộc môi trường (ml-service giả hay thật, nguồn số liệu giả hay OSHI, cách gây vi phạm) nằm trong
 * Environment, để cùng một kịch bản chạy trong JUnit (in-process) và trong EndToEndRunner (tiến trình thật).
 */
public class ExamScenario {

    /** Phần phụ thuộc môi trường của kịch bản. */
    public interface Environment {
        String serverHost();

        int serverPort();

        int heartbeatIntervalMs();

        /** Nguồn số liệu cho máy của sinh viên này (JUnit: giả điều khiển được; runner: OSHI thật). */
        MetricsSource metricsSourceFor(String studentCode);

        /**
         * Mô hình bất thường phía client. Runner thật dùng Isolation Forest thật. JUnit dùng scorer theo kịch bản để kiểm tra
         * ĐƯỜNG ỐNG (client -> HEARTBEAT -> Server -> ALERT) mà không phụ thuộc may rủi của dữ liệu ngẫu nhiên:
         * độ nhạy của Isolation Forest thật được đo riêng bằng ExperimentRunner.
         */
        AnomalyScorer scorerFor(String studentCode);

        /** Gây một vi phạm thật trên máy sinh viên. Trả về đoạn text sẽ xuất hiện trong lý do ALERT. */
        String triggerViolation(String studentCode) throws Exception;

        /** Có ép Isolation Forest báo bất thường được không (chỉ khi nguồn số liệu là giả). */
        boolean canForceIsolationForestAnomaly();

        void forceIsolationForestAnomaly(String studentCode);

        /** Làm ml-service trả lời chậm hơn timeout (hoặc treo hẳn). */
        void makeMlServiceSlow() throws Exception;

        void restoreMlService() throws Exception;

        /** Số request /score ml-service đã nhận thành công. */
        int mlRequestCount() throws Exception;

        /** Thời gian chờ tối đa để Server phát hiện mất heartbeat (giây) theo cấu hình của môi trường. */
        int heartbeatTimeoutSeconds();
    }

    private final Environment env;
    private final StepReport report = new StepReport();

    public ExamScenario(Environment env) {
        this.env = env;
    }

    public StepReport run() throws Exception {
        String host = env.serverHost();
        int port = env.serverPort();

        try (ChaosProxy proxy = new ChaosProxy(host, port);
             TeacherHarness teacher = new TeacherHarness(host, port)) {

            // ---- 1, 2: Server chạy, Teacher đăng nhập ----
            report.check("1. Server nhận kết nối TCP", canConnect(host, port));
            teacher.login("gv01", "teacher123");
            await("2. Teacher đăng nhập (LOGIN_OK)", teacher::isLoggedIn, 10_000);
            if (!teacher.isLoggedIn()) {
                return report; // không đăng nhập được thì các bước sau vô nghĩa
            }

            // ---- 4a: Teacher tạo đề và ca thi (ca phải có trước thì sinh viên mới đăng nhập được bằng mã ca) ----
            Map<Integer, String> correctTextById = loadAnswerKey(teacher);
            int examId = createRandomExam(teacher, 8);
            String shiftCode = createShift(teacher, examId, "CA-E2E", 600, "SV001\nSV002\nSV003");
            report.check("4. Teacher tạo đề 8 câu và ca thi " + shiftCode, shiftCode != null);
            if (shiftCode == null) {
                return report;
            }

            // ---- 3: Student đăng nhập (SV002 đi qua proxy gây lỗi mạng) ----
            try (StudentHarness s1 = new StudentHarness("SV001", shiftCode, host, port, env.metricsSourceFor("SV001"), env.scorerFor("SV001"), env.heartbeatIntervalMs());
                 StudentHarness s2 = new StudentHarness("SV002", shiftCode, host, proxy.getPort(), env.metricsSourceFor("SV002"), env.scorerFor("SV002"), env.heartbeatIntervalMs());
                 StudentHarness s3 = new StudentHarness("SV003", shiftCode, host, port, env.metricsSourceFor("SV003"), env.scorerFor("SV003"), env.heartbeatIntervalMs())) {

                checkRejectedLogins(host, port, shiftCode);

                s1.login("123456");
                s2.login("123456");
                s3.login("123456");
                await("3. Ba Student đăng nhập (LOGIN_OK)", () -> s1.isLoggedIn() && s2.isLoggedIn() && s3.isLoggedIn(), 10_000);
                report.check("3. Student thấy trạng thái Connected", s1.view.hasStatus("Connected"));

                // ---- 4b, 5: bắt đầu ca, Student nhận đề ----
                teacher.call("START_SHIFT", json("code", shiftCode));
                await("5. Cả ba Student nhận đề (EXAM_START)",
                        () -> s1.questions() != null && s2.questions() != null && s3.questions() != null, 10_000);
                if (s1.questions() == null || s3.questions() == null || s2.questions() == null) {
                    return report;
                }
                report.check("5. Đề có 8 câu, mỗi câu 4 đáp án", s1.questions().size() == 8 && s1.questions().get(0).options.size() == 4);
                report.check("5. Mỗi sinh viên nhận đề trộn riêng (thứ tự khác nhau)", papersDiffer(s1.questions(), s3.questions()));

                // ---- 6: Student trả lời ----
                for (int i = 0; i < 5; i++) {
                    s1.answerCorrectly(i, correctTextById);
                }
                s1.answerWrongly(5, correctTextById);
                for (int i = 0; i < 3; i++) {
                    s2.answerCorrectly(i, correctTextById);
                }
                s3.answerCorrectly(0, correctTextById);
                await("6. Server xác nhận (ANSWER_ACK) cả 6 đáp án của SV001", () -> s1.view.savedQuestions.size() >= 6, 10_000);
                await("6. Server xác nhận 3 đáp án của SV002", () -> s2.view.savedQuestions.size() >= 3, 10_000);

                // ---- 7: Heartbeat ----
                await("7. Heartbeat chạy (SV001 nhận >= 3 HEARTBEAT_ACK)", () -> s1.view.heartbeats.size() >= 3, 15_000);
                checkMachinesOnline(teacher, shiftCode, "7. Teacher thấy cả 3 máy online", true, "SV001", "SV002", "SV003");

                // ---- 8: Monitoring ----
                await("8. Student nhận RULES_CONFIG và bắt đầu giám sát", () -> hasLog(s1, "RULES_CONFIG"), 10_000);
                await("8. Server lưu số liệu giám sát của SV001 (summary)", () -> metricsRows(teacher, "SV001") >= 3, 20_000);
                ResponseMessage detail = teacher.call("MACHINE_DETAIL", json("machineId", "SV001"));
                report.check("8. MACHINE_DETAIL có số liệu 8 chiều", detail.ok && detail.data.has("metrics")
                        && detail.data.getAsJsonObject("metrics").has("cpuPercent"));
                await("8. Student nhận ROOM_STATS (median + MAD của phòng)", () -> s1.view.roomStatsCount >= 1, 15_000);
                await("8. Isolation Forest học xong và Server nhận điểm IF trong HEARTBEAT của SV001",
                        () -> ifScoreOf(teacher, "SV001") > 0, 60_000);

                // ---- 12-15: mất mạng, Server phát hiện offline, nối lại, khôi phục ----
                Map<Integer, Integer> chosenBeforeDrop = chosenAnswers(s2, correctTextById, 3);
                proxy.silenceAll();
                report.info("Mạng của SV002 'chết lặng' (không còn byte nào đi qua)");
                await("12/13. Server phát hiện SV002 offline (Teacher thấy offline)",
                        () -> Boolean.FALSE.equals(machineOnline(teacher, shiftCode, "SV002")), (env.heartbeatTimeoutSeconds() + 8) * 1000L);
                await("13. Teacher nhận ALERT 'Mất kết nối' của SV002", () -> teacher.findAlert("SV002", "Mất kết nối") != null, 10_000);
                await("14. SV002 tự nối lại và nhận RECONNECT_OK", () -> s2.view.hasStatus("Reconnected"), 30_000);
                await("14. Server đánh dấu SV002 online trở lại", () -> Boolean.TRUE.equals(machineOnline(teacher, shiftCode, "SV002")), 10_000);
                report.check("15. Đáp án đã chọn được khôi phục đúng sau khi nối lại",
                        s2.view.restoredAnswers != null && s2.view.restoredAnswers.equals(chosenBeforeDrop),
                        "mong đợi " + chosenBeforeDrop + ", thực tế " + s2.view.restoredAnswers);
                int heartbeatsAfterReconnect = s2.view.heartbeats.size();
                await("15. Heartbeat của SV002 chạy lại sau khi nối lại", () -> s2.view.heartbeats.size() > heartbeatsAfterReconnect + 1, 15_000);
                s2.answerCorrectly(3, correctTextById);
                await("15. SV002 làm bài tiếp được sau khi nối lại", () -> s2.view.savedQuestions.size() >= 4, 10_000);

                int reconnectsBefore = countStatus(s2, "Reconnected");
                proxy.cutAll();
                report.info("Cắt hẳn kết nối của SV002 (như rút dây mạng)");
                await("12-14. Cắt hẳn kết nối: SV002 nối lại lần hai", () -> countStatus(s2, "Reconnected") > reconnectsBefore, 30_000);

                // ---- 16, 17: vi phạm giám sát và ALERT ----
                String evidence = env.triggerViolation("SV003");
                report.info("Đã gây vi phạm trên máy SV003: " + evidence);
                await("16/17. Teacher nhận ALERT ĐỎ về vi phạm của SV003 (" + evidence + ")",
                        () -> {
                            AlertMessage alert = teacher.findAlert("SV003", evidence);
                            return alert != null && alert.level == AlertLevel.RED;
                        }, 30_000);
                await("17. Máy bị nghi ngờ chuyển sang chế độ HIGH", () -> "HIGH".equals(rateModeOf(teacher, shiftCode, "SV003")), 10_000);
                await("17. Ở chế độ HIGH Server nhận METRICS_DETAIL (danh sách process của SV003)", () -> detailProcessCount(teacher, "SV003") > 0, 15_000);

                if (env.canForceIsolationForestAnomaly()) {
                    env.forceIsolationForestAnomaly("SV001");
                    await("18. Isolation Forest (client) báo bất thường -> ALERT 'ML' tại Teacher",
                            () -> teacher.findAlert("SV001", "Isolation Forest") != null, 40_000);
                } else {
                    report.info("Bỏ qua ép Isolation Forest báo bất thường (dùng OSHI thật, không giả được lưu lượng)");
                }

                // ---- 18, 19: ML ----
                await("18. Server gọi ml-service (POST /score) ít nhất một lần", () -> mlRequests() >= 1, 45_000);
                int requestsBeforeSlow = mlRequests();
                env.makeMlServiceSlow();
                report.info("ml-service bị làm chậm hơn timeout");
                int heartbeatsBeforeSlow = s1.view.heartbeats.size();
                sleep(4000);
                await("19. Trong lúc ML timeout, Server vẫn nhận ANSWER", () -> {
                    s3.answerCorrectly(1, correctTextById);
                    return s3.view.savedQuestions.size() >= 2;
                }, 10_000);
                report.check("19. Trong lúc ML timeout, HEARTBEAT vẫn được trả lời", s1.view.heartbeats.size() > heartbeatsBeforeSlow + 1);
                report.check("19. Trong lúc ML timeout, Teacher vẫn xem được danh sách máy", teacher.call("LIST_MACHINES", json("code", shiftCode)).ok);
                env.restoreMlService();
                await("19. ml-service hoạt động trở lại thì Server tự dùng lại ML", () -> mlRequests() > requestsBeforeSlow, 45_000);
                report.info("Số request /score: " + requestsBeforeSlow + " -> " + mlRequests());

                // NOTICE
                teacher.sendNotice("SV003", "Chỉ riêng SV003");
                teacher.sendNotice("ALL", "Còn 5 phút");
                await("NOTICE: gửi một máy và cả phòng", () -> s3.view.notices.size() >= 2 && s2.view.notices.contains("Còn 5 phút")
                        && !s2.view.notices.contains("Chỉ riêng SV003"), 10_000);

                // ---- 9, 10, 11: nộp bài, chấm điểm, xem điểm ----
                report.check("9. SV001 gửi SUBMIT", s1.client.submit());
                await("9. Server trả SUBMIT_OK (6 câu đã trả lời)", () -> s1.view.submitted.contains(6), 10_000);
                JsonObject sv001 = resultRowOf(teacher, shiftCode, "SV001");
                report.check("10. Server chấm điểm: SV001 đúng 5/8 câu, điểm 6.25",
                        sv001 != null && sv001.get("submitted").getAsBoolean() && sv001.get("correctCount").getAsInt() == 5
                                && sv001.get("totalQuestions").getAsInt() == 8 && Math.abs(sv001.get("score").getAsDouble() - 6.25) < 0.001,
                        String.valueOf(sv001));
                report.check("10. SV001 tự nộp (không phải Server tự chốt)", sv001 != null && !sv001.get("autoSubmitted").getAsBoolean());
                boolean lateAnswerAccepted = s1.client.selectAnswer(s1.questions().get(7).questionId, 0);
                report.check("10. Sau SUBMIT không thể sửa đáp án", !lateAnswerAccepted);
                ResponseMessage csv = teacher.call("EXPORT_RESULTS_CSV", json("code", shiftCode));
                report.check("11. Teacher xuất CSV điểm có dòng SV001", csv.ok
                        && csv.data.get("csv").getAsString().contains("SV001,Nguyễn Văn An,SUBMITTED,6.25,5,8,6"),
                        csv.ok ? csv.data.get("csv").getAsString() : csv.error);

                // ---- 20: kết thúc ca ----
                teacher.call("END_SHIFT", json("code", shiftCode));
                await("20. SV002 và SV003 nhận EXAM_END (giáo viên kết thúc)",
                        () -> s2.view.ended.contains("TEACHER_ENDED") && s3.view.ended.contains("TEACHER_ENDED"), 10_000);
                JsonObject sv002 = resultRowOf(teacher, shiftCode, "SV002");
                JsonObject sv003 = resultRowOf(teacher, shiftCode, "SV003");
                report.check("20. Server tự chốt bài SV002 (4 đúng, 5.0 điểm)", sv002 != null && sv002.get("autoSubmitted").getAsBoolean()
                        && sv002.get("correctCount").getAsInt() == 4 && Math.abs(sv002.get("score").getAsDouble() - 5.0) < 0.001, String.valueOf(sv002));
                report.check("20. Server tự chốt bài SV003 (2 đúng, 2.5 điểm)", sv003 != null && sv003.get("autoSubmitted").getAsBoolean()
                        && sv003.get("correctCount").getAsInt() == 2 && Math.abs(sv003.get("score").getAsDouble() - 2.5) < 0.001, String.valueOf(sv003));
            }

            // ---- 20 (tiếp): ca ngắn tự hết giờ theo đồng hồ Server ----
            checkServerTimerEndsShift(teacher, host, port, examId);

            report.check("Server vẫn hoạt động bình thường đến cuối kịch bản", teacher.call("LIST_EXAMS").ok);
        }
        return report;
    }

    // ------------------------------------------------------------------
    // Các phần nhỏ của kịch bản
    // ------------------------------------------------------------------

    private void checkRejectedLogins(String host, int port, String shiftCode) throws Exception {
        try (StudentHarness wrongPassword = new StudentHarness("SV001", shiftCode, host, port, env.metricsSourceFor("SV001"), env.scorerFor("SV001"), env.heartbeatIntervalMs());
             StudentHarness notCandidate = new StudentHarness("SV009", shiftCode, host, port, env.metricsSourceFor("SV009"), env.scorerFor("SV009"), env.heartbeatIntervalMs())) {
            wrongPassword.login("sai-mat-khau");
            notCandidate.login("123456");
            await("3. Sai mật khẩu bị từ chối (LOGIN_FAIL)", () -> wrongPassword.view.logins.stream().anyMatch(s -> s.startsWith("FAIL:")), 10_000);
            await("3. Sinh viên ngoài danh sách bị từ chối", () -> notCandidate.view.logins.stream().anyMatch(s -> s.startsWith("FAIL:")), 10_000);
        }
    }

    /** Ca 4 giây: Server tự đến hạn thì chốt bài và gửi EXAM_END(TIME_UP). */
    private void checkServerTimerEndsShift(TeacherHarness teacher, String host, int port, int examId) throws Exception {
        String code = createShift(teacher, examId, "CA-TIMER", 4, "SV004");
        report.check("20. Tạo ca ngắn 4 giây để thử đồng hồ Server", code != null);
        if (code == null) {
            return;
        }
        try (StudentHarness s4 = new StudentHarness("SV004", code, host, port, env.metricsSourceFor("SV004"), env.scorerFor("SV004"), env.heartbeatIntervalMs())) {
            s4.login("123456");
            await("20. SV004 đăng nhập", s4::isLoggedIn, 10_000);
            teacher.call("START_SHIFT", json("code", code));
            await("20. SV004 nhận đề", () -> s4.questions() != null, 10_000);
            await("20. Hết giờ: Server tự gửi EXAM_END (TIME_UP) cho SV004", () -> s4.view.ended.contains("TIME_UP"), 20_000);
            JsonObject row = resultRowOf(teacher, code, "SV004");
            report.check("20. Server tự chốt bài khi hết giờ (autoSubmitted)", row != null && row.get("autoSubmitted").getAsBoolean(), String.valueOf(row));
            report.check("20. Sau khi hết giờ không còn nhận đáp án", !s4.client.selectAnswer(s4.questions().get(0).questionId, 0));
        }
    }

    private boolean canConnect(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    private Map<Integer, String> loadAnswerKey(TeacherHarness teacher) {
        Map<Integer, String> correctText = new HashMap<>();
        ResponseMessage response = teacher.call("LIST_QUESTIONS");
        for (JsonElement element : response.data.getAsJsonArray("questions")) {
            JsonObject question = element.getAsJsonObject();
            int correctIndex = question.get("correctIndex").getAsInt();
            correctText.put(question.get("id").getAsInt(), question.getAsJsonArray("options").get(correctIndex).getAsString());
        }
        return correctText;
    }

    private int createRandomExam(TeacherHarness teacher, int count) {
        JsonObject data = new JsonObject();
        data.addProperty("title", "De end-to-end");
        data.addProperty("durationMinutes", 10);
        data.addProperty("randomCount", count);
        ResponseMessage response = teacher.call("CREATE_EXAM", data);
        return response.ok ? response.data.get("examId").getAsInt() : -1;
    }

    /** Trả về mã ca, hoặc null nếu tạo thất bại. */
    private String createShift(TeacherHarness teacher, int examId, String code, int durationSeconds, String candidatesCsv) {
        JsonObject data = new JsonObject();
        data.addProperty("examId", examId);
        data.addProperty("code", code);
        data.addProperty("durationSeconds", durationSeconds);
        data.addProperty("candidatesCsv", candidatesCsv);
        ResponseMessage response = teacher.call("CREATE_SHIFT", data);
        return response.ok ? response.data.get("code").getAsString() : null;
    }

    private boolean papersDiffer(List<QuestionView> first, List<QuestionView> second) {
        for (int i = 0; i < first.size(); i++) {
            if (first.get(i).questionId != second.get(i).questionId || !first.get(i).options.equals(second.get(i).options)) {
                return true;
            }
        }
        return false;
    }

    /** Các đáp án (questionId -> choice) mà sinh viên đã chọn đúng cho `count` câu đầu tiên. */
    private Map<Integer, Integer> chosenAnswers(StudentHarness student, Map<Integer, String> correctTextById, int count) {
        Map<Integer, Integer> chosen = new HashMap<>();
        for (int i = 0; i < count; i++) {
            QuestionView question = student.questions().get(i);
            chosen.put(question.questionId, question.options.indexOf(correctTextById.get(question.questionId)));
        }
        return chosen;
    }

    private boolean hasLog(StudentHarness student, String prefix) {
        synchronized (student.view.logs) {
            for (String line : student.view.logs) {
                if (line.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int countStatus(StudentHarness student, String status) {
        int count = 0;
        synchronized (student.view.statuses) {
            for (String text : student.view.statuses) {
                if (text.equals(status)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int mlRequests() {
        try {
            return env.mlRequestCount();
        } catch (Exception e) {
            return -1;
        }
    }

    private JsonObject json(String key, String value) {
        JsonObject json = new JsonObject();
        json.addProperty(key, value);
        return json;
    }

    private JsonObject machineRow(TeacherHarness teacher, String shiftCode, String machineId) {
        ResponseMessage response = teacher.call("LIST_MACHINES", json("code", shiftCode));
        if (!response.ok) {
            return null;
        }
        for (JsonElement element : response.data.getAsJsonArray("machines")) {
            if (element.getAsJsonObject().get("machineId").getAsString().equals(machineId)) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    /** true/false nếu biết trạng thái online của máy, null nếu không đọc được. */
    private Boolean machineOnline(TeacherHarness teacher, String shiftCode, String machineId) {
        JsonObject row = machineRow(teacher, shiftCode, machineId);
        return row == null ? null : row.get("online").getAsBoolean();
    }

    private String rateModeOf(TeacherHarness teacher, String shiftCode, String machineId) {
        JsonObject row = machineRow(teacher, shiftCode, machineId);
        return row == null ? null : row.get("rateMode").getAsString();
    }

    private void checkMachinesOnline(TeacherHarness teacher, String shiftCode, String step, boolean expectedOnline, String... machineIds) {
        boolean all = true;
        for (String machineId : machineIds) {
            all &= Boolean.valueOf(expectedOnline).equals(machineOnline(teacher, shiftCode, machineId));
        }
        report.check(step, all);
    }

    private int metricsRows(TeacherHarness teacher, String machineId) {
        ResponseMessage response = teacher.call("EXPORT_METRICS_CSV", json("machineId", machineId));
        if (!response.ok) {
            return 0;
        }
        return Math.max(0, response.data.get("csv").getAsString().split("\n").length - 1);
    }

    private double ifScoreOf(TeacherHarness teacher, String machineId) {
        ResponseMessage response = teacher.call("MACHINE_DETAIL", json("machineId", machineId));
        return response.ok ? response.data.get("ifScore").getAsDouble() : 0;
    }

    private int detailProcessCount(TeacherHarness teacher, String machineId) {
        ResponseMessage response = teacher.call("MACHINE_DETAIL", json("machineId", machineId));
        return response.ok ? response.data.getAsJsonArray("processNames").size() : 0;
    }

    private JsonObject resultRowOf(TeacherHarness teacher, String shiftCode, String studentCode) {
        ResponseMessage response = teacher.call("LIST_RESULTS", json("code", shiftCode));
        if (!response.ok) {
            return null;
        }
        JsonArray rows = response.data.getAsJsonArray("rows");
        for (JsonElement element : rows) {
            if (element.getAsJsonObject().get("studentCode").getAsString().equals(studentCode)) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    /** Chờ điều kiện đúng tối đa timeoutMillis rồi ghi kết quả bước. */
    private void await(String step, BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        boolean ok = false;
        while (System.currentTimeMillis() < deadline) {
            try {
                ok = condition.getAsBoolean();
            } catch (RuntimeException e) {
                ok = false; // lỗi tạm thời khi hỏi Server: thử lại cho tới hết hạn
            }
            if (ok) {
                break;
            }
            sleep(100);
        }
        report.check(step, ok, ok ? "" : "quá " + timeoutMillis + " ms");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kịch bản bị ngắt", e);
        }
    }
}
