// Owner: Nguoi2

package exam.server.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.common.model.ExamShift;
import exam.common.model.MlMode;
import exam.common.model.MonitoringRules;
import exam.common.model.Question;
import exam.common.model.QuestionView;
import exam.common.model.Role;
import exam.common.model.UserAccount;
import exam.common.protocol.ExamStartMessage;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import exam.server.db.Database;
import exam.server.session.InMemorySessionRegistry;
import exam.server.testutil.FakeMessageSender;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test nghiệp vụ thi với database SQLite thật (file tạm) và ExamServiceImpl thật.
 * Giáo viên được mô phỏng bằng cách gọi handleTeacherRequest (đúng đường đi của REQUEST).
 */
class ExamServiceTest {

    @TempDir
    Path tempDirectory;

    private ExamServiceImpl examService;
    private FakeMessageSender messageSender;
    private int teacherId;
    private int studentId;

    @BeforeEach
    void setUp() throws Exception {
        Database database = new Database(tempDirectory.resolve("exam-test.db").toString());
        database.createTables();
        database.loadSampleDataIfEmpty();

        messageSender = new FakeMessageSender();
        examService = new ExamServiceImpl(database, messageSender, new InMemorySessionRegistry(),
                MlMode.NONE, MonitoringRules.createDefault());

        teacherId = examService.login("gv01", "teacher123", Role.TEACHER).id;
        studentId = examService.login("SV001", "123456", Role.STUDENT).id;
    }

    // ------------------------------------------------------------------
    // Hàm giúp: gọi REQUEST của giáo viên
    // ------------------------------------------------------------------

    private ResponseMessage call(String action, JsonObject data) {
        return examService.handleTeacherRequest(teacherId, new RequestMessage("r-" + action, action, data));
    }

    private JsonObject callOk(String action, JsonObject data) {
        ResponseMessage response = call(action, data);
        assertTrue(response.ok, action + " thất bại: " + response.error);
        return response.data;
    }

    private JsonObject questionJson(String content, String a, String b, String c, String d, int correctIndex) {
        JsonObject json = new JsonObject();
        json.addProperty("content", content);
        JsonArray options = new JsonArray();
        options.add(a);
        options.add(b);
        options.add(c);
        options.add(d);
        json.add("options", options);
        json.addProperty("correctIndex", correctIndex);
        return json;
    }

    private JsonObject json(String key, String value) {
        JsonObject json = new JsonObject();
        json.addProperty(key, value);
        return json;
    }

    private int createExamWithRandomQuestions(int count) {
        JsonObject data = new JsonObject();
        data.addProperty("title", "De thu");
        data.addProperty("durationMinutes", 30);
        data.addProperty("randomCount", count);
        return callOk("CREATE_EXAM", data).get("examId").getAsInt();
    }

    /** Tạo ca với một danh sách thí sinh, thời lượng tính bằng giây. */
    private String createShift(int examId, String candidatesCsv, int durationSeconds) {
        JsonObject data = new JsonObject();
        data.addProperty("examId", examId);
        data.addProperty("durationSeconds", durationSeconds);
        data.addProperty("candidatesCsv", candidatesCsv);
        return callOk("CREATE_SHIFT", data).get("code").getAsString();
    }

    private void startShift(String code) {
        callOk("START_SHIFT", json("code", code));
    }

    /**
     * Với mỗi câu trong đề đã trộn của sinh viên, tìm vị trí (đã trộn) của đáp án đúng:
     * đáp án đúng gốc là options[correctIndex] trong ngân hàng, rồi tìm nó trong options đã trộn.
     */
    private Map<Integer, Integer> correctChoicesFor(int student, String code) {
        Map<Integer, String> correctTextById = new HashMap<>();
        for (var element : callOk("LIST_QUESTIONS", new JsonObject()).getAsJsonArray("questions")) {
            JsonObject q = element.getAsJsonObject();
            int correctIndex = q.get("correctIndex").getAsInt();
            correctTextById.put(q.get("id").getAsInt(), q.getAsJsonArray("options").get(correctIndex).getAsString());
        }

        Map<Integer, Integer> choices = new HashMap<>();
        for (QuestionView view : examService.prepareQuestionsForStudent(student, code)) {
            choices.put(view.questionId, view.options.indexOf(correctTextById.get(view.questionId)));
        }
        return choices;
    }

    // ------------------------------------------------------------------
    // Question bank CRUD
    // ------------------------------------------------------------------

    @Test
    void questionCrudCreateReadUpdateDelete() {
        JsonObject created = callOk("ADD_QUESTION", questionJson("Hai cộng hai?", "3", "4", "5", "6", 1));
        int id = created.get("id").getAsInt();

        JsonArray all = callOk("LIST_QUESTIONS", new JsonObject()).getAsJsonArray("questions");
        assertEquals(31, all.size()); // 30 mẫu + 1 mới

        JsonObject update = questionJson("Hai nhân hai?", "3", "4", "5", "6", 1);
        update.addProperty("id", id);
        callOk("UPDATE_QUESTION", update);
        boolean found = false;
        for (var element : callOk("LIST_QUESTIONS", new JsonObject()).getAsJsonArray("questions")) {
            if (element.getAsJsonObject().get("id").getAsInt() == id) {
                assertEquals("Hai nhân hai?", element.getAsJsonObject().get("content").getAsString());
                found = true;
            }
        }
        assertTrue(found);

        JsonObject delete = new JsonObject();
        delete.addProperty("id", id);
        callOk("DELETE_QUESTION", delete);
        assertEquals(30, callOk("LIST_QUESTIONS", new JsonObject()).getAsJsonArray("questions").size());
    }

    @Test
    void addQuestionRejectsInvalidData() {
        assertFalse(call("ADD_QUESTION", questionJson("", "a", "b", "c", "d", 0)).ok);
        assertFalse(call("ADD_QUESTION", questionJson("Q?", "a", "b", "c", "d", 4)).ok);
        assertFalse(call("ADD_QUESTION", questionJson("Q?", "a", "a", "c", "d", 0)).ok); // trùng đáp án
        assertFalse(call("ADD_QUESTION", questionJson("Q?", "a", "", "c", "d", 0)).ok);

        JsonObject threeOptions = questionJson("Q?", "a", "b", "c", "d", 0);
        threeOptions.getAsJsonArray("options").remove(3);
        assertFalse(call("ADD_QUESTION", threeOptions).ok);
    }

    @Test
    void updateOrDeleteMissingQuestionFails() {
        JsonObject update = questionJson("Q?", "a", "b", "c", "d", 0);
        update.addProperty("id", 99999);
        assertFalse(call("UPDATE_QUESTION", update).ok);

        JsonObject delete = new JsonObject();
        delete.addProperty("id", 99999);
        assertFalse(call("DELETE_QUESTION", delete).ok);
    }

    @Test
    void cannotDeleteQuestionUsedInAnExam() {
        int examId = createExamWithRandomQuestions(5);
        JsonObject exam = new JsonObject();
        int usedQuestionId = -1;
        for (var element : callOk("LIST_EXAMS", exam).getAsJsonArray("exams")) {
            assertEquals(examId, element.getAsJsonObject().get("id").getAsInt());
            assertEquals(5, element.getAsJsonObject().get("questionCount").getAsInt());
        }
        // Đề dùng 5 trong 30 câu: thử xóa từng câu cho tới khi gặp một câu thuộc đề.
        for (var element : callOk("LIST_QUESTIONS", new JsonObject()).getAsJsonArray("questions")) {
            JsonObject delete = new JsonObject();
            delete.addProperty("id", element.getAsJsonObject().get("id").getAsInt());
            ResponseMessage response = call("DELETE_QUESTION", delete);
            if (!response.ok) {
                usedQuestionId = delete.get("id").getAsInt();
                assertTrue(response.error.contains("đề thi"));
                break;
            }
        }
        assertNotEquals(-1, usedQuestionId, "phải có ít nhất một câu bị chặn xóa vì nằm trong đề");
    }

    // ------------------------------------------------------------------
    // CSV import
    // ------------------------------------------------------------------

    private JsonObject importCsv(String csv) {
        return callOk("IMPORT_QUESTIONS_CSV", json("csv", csv));
    }

    @Test
    void csvImportAcceptsValidRowsWithHeaderAndLetterOrNumberAnswers() {
        String csv = "question,A,B,C,D,correct\n"
                + "Màu của lá cây?,Đỏ,Xanh,Vàng,Tím,B\n"
                + "Một cộng một?,1,2,3,4,2\n";

        JsonObject result = importCsv(csv);

        assertEquals(2, result.get("added").getAsInt());
        assertEquals(0, result.get("skipped").getAsInt());
        assertEquals(32, callOk("LIST_QUESTIONS", new JsonObject()).getAsJsonArray("questions").size());
    }

    @Test
    void csvImportSupportsQuotedFieldsWithCommas() {
        String csv = "\"Thủ đô, của nước Pháp?\",\"Lyon, Pháp\",Paris,Nice,Lille,b\n";

        assertEquals(1, importCsv(csv).get("added").getAsInt());
    }

    @Test
    void csvImportSkipsBadRowsButKeepsGoodRows() {
        String csv = "Câu tốt 1?,a,b,c,d,A\n"
                + "Thiếu cột,a,b\n"                      // thiếu field
                + "Sai đáp án?,a,b,c,d,E\n"               // đáp án không hợp lệ
                + "Đáp án trống?,a,,c,d,A\n"              // option rỗng
                + "Câu tốt 2?,e,f,g,h,D\n"
                + "\"Thiếu nháy đóng,a,b,c,d,A\n";        // dấu nháy mở không đóng

        JsonObject result = importCsv(csv);

        assertEquals(2, result.get("added").getAsInt());
        assertEquals(4, result.get("skipped").getAsInt());
        JsonArray errors = result.getAsJsonArray("errors");
        assertEquals(4, errors.size());
        assertTrue(errors.get(0).getAsString().startsWith("dòng 2"));
    }

    @Test
    void csvImportSkipsDuplicatesInFileAndInDatabase() {
        String csv = "Câu duy nhất?,a,b,c,d,A\n"
                + "câu duy nhất?,a,b,c,d,A\n"               // trùng trong cùng file (khác hoa thường)
                + "Thủ đô của Việt Nam là thành phố nào?,Hà Nội,Huế,Đà Nẵng,Cần Thơ,A\n"; // trùng với câu mẫu trong DB

        JsonObject result = importCsv(csv);

        assertEquals(1, result.get("added").getAsInt());
        assertEquals(2, result.get("skipped").getAsInt());
    }

    @Test
    void csvImportHandlesBomAndWindowsLineEndings() {
        String csv = "﻿question,A,B,C,D,correct\r\nCâu BOM?,a,b,c,d,C\r\n";

        assertEquals(1, importCsv(csv).get("added").getAsInt());
    }

    @Test
    void csvImportRejectsWrongEncodingAndEmptyFile() {
        // Ký tự thay thế U+FFFD xuất hiện khi đọc file không phải UTF-8 bằng UTF-8.
        assertFalse(call("IMPORT_QUESTIONS_CSV", json("csv", "C�u h�i?,a,b,c,d,A")).ok);
        assertFalse(call("IMPORT_QUESTIONS_CSV", json("csv", "   ")).ok);
    }

    // ------------------------------------------------------------------
    // Tạo đề
    // ------------------------------------------------------------------

    @Test
    void randomExamPicksRequestedNumberOfDistinctQuestions() {
        int examId = createExamWithRandomQuestions(10);

        JsonObject data = new JsonObject();
        data.addProperty("title", "De 2");
        data.addProperty("durationMinutes", 15);
        data.addProperty("randomCount", 10);
        JsonArray ids = callOk("CREATE_EXAM", data).getAsJsonArray("questionIds");

        assertEquals(10, ids.size());
        java.util.Set<Integer> distinct = new java.util.HashSet<>();
        for (var id : ids) {
            distinct.add(id.getAsInt());
        }
        assertEquals(10, distinct.size());
        assertNotEquals(0, examId);
    }

    @Test
    void randomExamWithTooManyQuestionsFails() {
        JsonObject data = new JsonObject();
        data.addProperty("title", "Qua nhieu");
        data.addProperty("durationMinutes", 15);
        data.addProperty("randomCount", 31);
        ResponseMessage response = call("CREATE_EXAM", data);

        assertFalse(response.ok);
        assertTrue(response.error.contains("30"));
    }

    @Test
    void manualExamUsesChosenQuestionsAndRejectsBadIds() {
        JsonObject data = new JsonObject();
        data.addProperty("title", "De thu cong");
        data.addProperty("durationMinutes", 20);
        JsonArray ids = new JsonArray();
        ids.add(1);
        ids.add(2);
        ids.add(3);
        data.add("questionIds", ids);
        assertEquals(3, callOk("CREATE_EXAM", data).getAsJsonArray("questionIds").size());

        JsonArray badIds = new JsonArray();
        badIds.add(1);
        badIds.add(99999);
        data.add("questionIds", badIds);
        assertFalse(call("CREATE_EXAM", data).ok);

        JsonArray duplicateIds = new JsonArray();
        duplicateIds.add(1);
        duplicateIds.add(1);
        data.add("questionIds", duplicateIds);
        assertFalse(call("CREATE_EXAM", data).ok);
    }

    // ------------------------------------------------------------------
    // Trộn câu hỏi
    // ------------------------------------------------------------------

    private List<Question> sampleQuestions() {
        List<Question> questions = new ArrayList<>();
        for (int id = 1; id <= 10; id++) {
            questions.add(new Question(id, "Câu " + id, List.of("a" + id, "b" + id, "c" + id, "d" + id), id % 4));
        }
        return questions;
    }

    @Test
    void sameSeedGivesSamePaper() {
        List<QuestionView> first = ExamPaper.build(sampleQuestions(), 123L).toViews();
        List<QuestionView> second = ExamPaper.build(sampleQuestions(), 123L).toViews();

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).questionId, second.get(i).questionId);
            assertEquals(first.get(i).options, second.get(i).options);
        }
    }

    @Test
    void differentSeedsGiveDifferentQuestionAndOptionOrder() {
        List<QuestionView> first = ExamPaper.build(sampleQuestions(), 1L).toViews();
        List<QuestionView> second = ExamPaper.build(sampleQuestions(), 2L).toViews();

        boolean questionOrderDiffers = false;
        boolean optionOrderDiffers = false;
        for (int i = 0; i < first.size(); i++) {
            if (first.get(i).questionId != second.get(i).questionId) {
                questionOrderDiffers = true;
            }
        }
        for (QuestionView view : first) {
            for (QuestionView other : second) {
                if (view.questionId == other.questionId && !view.options.equals(other.options)) {
                    optionOrderDiffers = true;
                }
            }
        }
        assertTrue(questionOrderDiffers, "thứ tự câu hỏi phải khác nhau");
        assertTrue(optionOrderDiffers, "thứ tự đáp án phải khác nhau");
    }

    @Test
    void shuffledPaperKeepsEveryQuestionAndEveryOption() {
        List<QuestionView> views = ExamPaper.build(sampleQuestions(), 99L).toViews();

        assertEquals(10, views.size());
        for (QuestionView view : views) {
            assertEquals(4, view.options.size());
            List<String> sortedOptions = new ArrayList<>(view.options);
            java.util.Collections.sort(sortedOptions);
            int id = view.questionId;
            assertEquals(List.of("a" + id, "b" + id, "c" + id, "d" + id), sortedOptions);
        }
    }

    @Test
    void gradingMapsShuffledChoiceBackToOriginalAnswer() {
        List<Question> questions = sampleQuestions();
        ExamPaper paper = ExamPaper.build(questions, 7L);

        // Với mỗi câu, tìm vị trí ĐÃ TRỘN của đáp án đúng gốc rồi chọn nó.
        Map<Integer, Integer> answers = new HashMap<>();
        for (QuestionView view : paper.toViews()) {
            Question original = questions.get(view.questionId - 1);
            answers.put(view.questionId, view.options.indexOf(original.options.get(original.correctIndex)));
        }
        assertEquals(10, paper.countCorrect(answers));

        // Chọn sai: lệch một vị trí so với đáp án đúng.
        Map<Integer, Integer> wrong = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : answers.entrySet()) {
            wrong.put(entry.getKey(), (entry.getValue() + 1) % 4);
        }
        assertEquals(0, paper.countCorrect(wrong));
    }

    // ------------------------------------------------------------------
    // Ca thi, answer, submit, grading
    // ------------------------------------------------------------------

    @Test
    void studentGetsDifferentPaperThanAnotherStudentInSameShift() {
        int examId = createExamWithRandomQuestions(10);
        String code = createShift(examId, "SV001\nSV002\n", 600);
        int otherStudentId = examService.login("SV002", "123456", Role.STUDENT).id;

        List<QuestionView> paper1 = examService.prepareQuestionsForStudent(studentId, code);
        List<QuestionView> paper2 = examService.prepareQuestionsForStudent(otherStudentId, code);

        assertEquals(10, paper1.size());
        assertEquals(10, paper2.size());
        boolean differs = false;
        for (int i = 0; i < 10; i++) {
            if (paper1.get(i).questionId != paper2.get(i).questionId
                    || !paper1.get(i).options.equals(paper2.get(i).options)) {
                differs = true;
            }
        }
        assertTrue(differs, "hai sinh viên khác nhau nên nhận đề trộn khác nhau");
        // Cùng một sinh viên gọi lại phải ra đúng đề cũ (cần cho reconnect).
        List<QuestionView> paper1Again = examService.prepareQuestionsForStudent(studentId, code);
        for (int i = 0; i < 10; i++) {
            assertEquals(paper1.get(i).questionId, paper1Again.get(i).questionId);
            assertEquals(paper1.get(i).options, paper1Again.get(i).options);
        }
    }

    @Test
    void createShiftReportsUnknownStudentsButKeepsValidOnes() {
        int examId = createExamWithRandomQuestions(5);
        JsonObject data = new JsonObject();
        data.addProperty("examId", examId);
        data.addProperty("durationMinutes", 10);
        data.addProperty("candidatesCsv", "student_code\nSV001\nSV999\nSV001\nSV002");

        JsonObject result = callOk("CREATE_SHIFT", data);

        assertEquals(2, result.get("candidatesAdded").getAsInt());
        assertEquals(2, result.getAsJsonArray("errors").size()); // SV999 không tồn tại, SV001 bị trùng
    }

    @Test
    void createShiftFailsWithoutAnyValidCandidate() {
        int examId = createExamWithRandomQuestions(5);
        JsonObject data = new JsonObject();
        data.addProperty("examId", examId);
        data.addProperty("durationMinutes", 10);
        data.addProperty("candidatesCsv", "SV999\nSV888");

        assertFalse(call("CREATE_SHIFT", data).ok);
    }

    @Test
    void duplicateShiftCodeIsRejected() {
        int examId = createExamWithRandomQuestions(5);
        JsonObject data = new JsonObject();
        data.addProperty("examId", examId);
        data.addProperty("durationMinutes", 10);
        data.addProperty("candidatesCsv", "SV001");
        data.addProperty("code", "CA777");

        assertTrue(call("CREATE_SHIFT", data).ok);
        assertFalse(call("CREATE_SHIFT", data).ok);
    }

    @Test
    void answersAreRejectedBeforeShiftStartsAndAcceptedAfter() {
        int examId = createExamWithRandomQuestions(5);
        String code = createShift(examId, "SV001", 600);
        int questionId = examService.prepareQuestionsForStudent(studentId, code).get(0).questionId;

        assertFalse(examService.saveAnswer(studentId, code, questionId, 0), "ca chưa bắt đầu");

        startShift(code);
        assertTrue(examService.saveAnswer(studentId, code, questionId, 2));
        assertEquals(2, examService.getSavedAnswers(studentId, code).get(questionId));

        // Chọn lại thì ghi đè
        assertTrue(examService.saveAnswer(studentId, code, questionId, 3));
        assertEquals(3, examService.getSavedAnswers(studentId, code).get(questionId));
    }

    @Test
    void invalidAnswersAreRejected() {
        int examId = createExamWithRandomQuestions(5);
        String code = createShift(examId, "SV001", 600);
        startShift(code);
        int questionId = examService.prepareQuestionsForStudent(studentId, code).get(0).questionId;

        assertFalse(examService.saveAnswer(studentId, code, questionId, 4), "choice ngoài 0..3");
        assertFalse(examService.saveAnswer(studentId, code, questionId, -1));
        assertFalse(examService.saveAnswer(studentId, code, 99999, 0), "câu hỏi không thuộc đề");
        int outsider = examService.login("SV005", "123456", Role.STUDENT).id;
        assertFalse(examService.saveAnswer(outsider, code, questionId, 0), "không nằm trong danh sách thí sinh");
        assertFalse(examService.saveAnswer(studentId, "KHONGCO", questionId, 0), "ca thi không tồn tại");
    }

    @Test
    void submitGradesCorrectlyAndLocksAnswers() {
        int examId = createExamWithRandomQuestions(10);
        String code = createShift(examId, "SV001\nSV002", 600);
        startShift(code);

        // SV001 trả lời đúng 7 câu đầu, sai 3 câu sau
        Map<Integer, Integer> correct = correctChoicesFor(studentId, code);
        List<QuestionView> paper = examService.prepareQuestionsForStudent(studentId, code);
        for (int i = 0; i < paper.size(); i++) {
            int questionId = paper.get(i).questionId;
            int choice = i < 7 ? correct.get(questionId) : (correct.get(questionId) + 1) % 4;
            assertTrue(examService.saveAnswer(studentId, code, questionId, choice));
        }

        int answeredCount = examService.submit(studentId, code);

        assertEquals(10, answeredCount);
        assertTrue(examService.hasSubmitted(studentId, code));

        JsonObject results = callOk("LIST_RESULTS", json("code", code));
        for (var element : results.getAsJsonArray("rows")) {
            JsonObject row = element.getAsJsonObject();
            if (row.get("studentCode").getAsString().equals("SV001")) {
                assertTrue(row.get("submitted").getAsBoolean());
                assertEquals(7, row.get("correctCount").getAsInt());
                assertEquals(10, row.get("totalQuestions").getAsInt());
                assertEquals(7.0, row.get("score").getAsDouble(), 0.001);
                assertFalse(row.get("autoSubmitted").getAsBoolean());
            } else {
                assertFalse(row.get("submitted").getAsBoolean());
            }
        }

        // Sau khi nộp: không sửa được đáp án, nộp lần hai không đổi điểm
        assertFalse(examService.saveAnswer(studentId, code, paper.get(0).questionId, 0));
        assertEquals(10, examService.submit(studentId, code));
        JsonObject again = callOk("LIST_RESULTS", json("code", code));
        assertEquals(7.0, again.getAsJsonArray("rows").get(0).getAsJsonObject().get("score").getAsDouble(), 0.001);
    }

    @Test
    void endShiftAutoSubmitsEveryoneWhoDidNotSubmit() {
        int examId = createExamWithRandomQuestions(10);
        String code = createShift(examId, "SV001\nSV002", 600);
        startShift(code);
        Map<Integer, Integer> correct = correctChoicesFor(studentId, code);
        for (QuestionView view : examService.prepareQuestionsForStudent(studentId, code)) {
            examService.saveAnswer(studentId, code, view.questionId, correct.get(view.questionId));
        }

        callOk("END_SHIFT", json("code", code));

        assertEquals(ExamShift.STATUS_ENDED, examService.findShiftByCode(code).status);
        JsonObject results = callOk("LIST_RESULTS", json("code", code));
        for (var element : results.getAsJsonArray("rows")) {
            JsonObject row = element.getAsJsonObject();
            assertTrue(row.get("submitted").getAsBoolean(), "mọi thí sinh phải được chốt bài");
            assertTrue(row.get("autoSubmitted").getAsBoolean());
            if (row.get("studentCode").getAsString().equals("SV001")) {
                assertEquals(10.0, row.get("score").getAsDouble(), 0.001);
            } else {
                assertEquals(0.0, row.get("score").getAsDouble(), 0.001); // SV002 không làm gì
            }
        }
        // Gọi lại END_SHIFT không lỗi và không đổi kết quả
        callOk("END_SHIFT", json("code", code));
    }

    @Test
    void serverTimerEndsShiftAndRejectsLateAnswers() throws Exception {
        int examId = createExamWithRandomQuestions(5);
        String code = createShift(examId, "SV001", 1); // ca dài 1 giây
        startShift(code);
        int questionId = examService.prepareQuestionsForStudent(studentId, code).get(0).questionId;
        assertTrue(examService.saveAnswer(studentId, code, questionId, 1));

        Thread.sleep(1300); // quá giờ theo đồng hồ Server

        // Dù timer chưa chạy, Server đã từ chối đáp án vì quá giờ
        assertFalse(examService.saveAnswer(studentId, code, questionId, 2), "Server không nhận đáp án sau khi hết giờ");

        examService.finishExpiredExams(); // việc PeriodicJobs làm mỗi giây

        assertEquals(ExamShift.STATUS_ENDED, examService.findShiftByCode(code).status);
        assertTrue(examService.hasSubmitted(studentId, code), "Server tự chốt bài");
        assertEquals(1, examService.getSavedAnswers(studentId, code).get(questionId), "đáp án muộn không được lưu");
    }

    @Test
    void scheduledShiftStartsAutomaticallyAtItsStartTime() throws Exception {
        int examId = createExamWithRandomQuestions(5);
        JsonObject data = new JsonObject();
        data.addProperty("examId", examId);
        data.addProperty("durationMinutes", 5);
        data.addProperty("candidatesCsv", "SV001");
        data.addProperty("startTime", System.currentTimeMillis() + 500);
        String code = callOk("CREATE_SHIFT", data).get("code").getAsString();

        examService.startDueShifts();
        assertEquals(ExamShift.STATUS_CREATED, examService.findShiftByCode(code).status, "chưa tới giờ hẹn");

        Thread.sleep(700);
        examService.startDueShifts();

        ExamShift shift = examService.findShiftByCode(code);
        assertEquals(ExamShift.STATUS_RUNNING, shift.status);
        assertTrue(shift.getEndTimeServer() > System.currentTimeMillis());
    }

    @Test
    void cannotStartTwiceOrStartUnknownShift() {
        int examId = createExamWithRandomQuestions(5);
        String code = createShift(examId, "SV001", 600);
        startShift(code);

        assertFalse(call("START_SHIFT", json("code", code)).ok);
        assertFalse(call("START_SHIFT", json("code", "KHONGCO")).ok);
    }

    @Test
    void endTimeIsComputedByServerFromStartAndDuration() {
        int examId = createExamWithRandomQuestions(5);
        String code = createShift(examId, "SV001", 600);
        assertEquals(0L, examService.getEndTimeServer(code), "chưa bắt đầu thì chưa có giờ hết bài");

        long before = System.currentTimeMillis();
        startShift(code);
        long end = examService.getEndTimeServer(code);

        assertTrue(end >= before + 600_000 && end <= System.currentTimeMillis() + 600_000);
    }

    @Test
    void exportResultsCsvListsEveryCandidate() {
        int examId = createExamWithRandomQuestions(5);
        String code = createShift(examId, "SV001\nSV002\nSV003", 600);
        startShift(code);
        examService.submit(studentId, code);

        String csv = examService.exportScoresCsv(code);
        String[] lines = csv.split("\n");

        assertEquals("student_code,full_name,status,score,correct,total,answered,submitted_at", lines[0]);
        assertEquals(4, lines.length);
        assertTrue(lines[1].startsWith("SV001,Nguyễn Văn An,SUBMITTED,"));
        assertTrue(lines[2].startsWith("SV002,Trần Thị Bình,NOT_SUBMITTED"));
    }

    @Test
    void startingShiftSendsExamStartToOnlineCandidatesOnly() throws Exception {
        // Dùng registry thật với session giả (connection null) để kiểm tra việc gửi EXAM_START.
        Database database = new Database(tempDirectory.resolve("notify.db").toString());
        database.createTables();
        database.loadSampleDataIfEmpty();
        InMemorySessionRegistry registry = new InMemorySessionRegistry();
        FakeMessageSender sender = new FakeMessageSender();
        ExamServiceImpl service = new ExamServiceImpl(database, sender, registry, MlMode.IF, MonitoringRules.createDefault());

        UserAccount teacher = service.login("gv01", "teacher123", Role.TEACHER);
        UserAccount student = service.login("SV001", "123456", Role.STUDENT);

        JsonObject examData = new JsonObject();
        examData.addProperty("title", "De");
        examData.addProperty("durationMinutes", 10);
        examData.addProperty("randomCount", 4);
        JsonObject exam = service.handleTeacherRequest(teacher.id, new RequestMessage("1", "CREATE_EXAM", examData)).data;
        JsonObject shiftData = new JsonObject();
        shiftData.addProperty("examId", exam.get("examId").getAsInt());
        shiftData.addProperty("code", "CA100");
        shiftData.addProperty("durationMinutes", 10);
        shiftData.addProperty("candidatesCsv", "SV001\nSV002");
        service.handleTeacherRequest(teacher.id, new RequestMessage("2", "CREATE_SHIFT", shiftData));

        // SV001 online với mã ca CA100, SV002 chưa đăng nhập
        registry.createSession(student, "CA100", null);
        service.handleTeacherRequest(teacher.id, new RequestMessage("3", "START_SHIFT", json("code", "CA100")));

        List<ExamStartMessage> starts = sender.machineMessagesOfType(ExamStartMessage.class);
        assertEquals(1, starts.size());
        assertEquals(4, starts.get(0).questions.size());
        assertNotNull(starts.get(0).title);
        assertTrue(sender.sentToMachineLog.contains("SV001:EXAM_START"));
        assertTrue(sender.sentToMachineLog.contains("SV001:RULES_CONFIG"));
        assertFalse(sender.sentToMachineLog.contains("SV002:EXAM_START"));
    }

    @Test
    void unknownActionReturnsErrorResponseInsteadOfCrashing() {
        ResponseMessage response = call("HACK_THE_PLANET", new JsonObject());

        assertFalse(response.ok);
        assertTrue(response.error.contains("HACK_THE_PLANET"));
    }

    @Test
    void missingParametersReturnClearErrors() {
        ResponseMessage response = call("ADD_QUESTION", new JsonObject());

        assertFalse(response.ok);
        assertTrue(response.error.contains("content"));
    }
}
