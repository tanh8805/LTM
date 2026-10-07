// Owner: Nguoi2

package exam.server.exam;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import exam.common.model.ExamShift;
import exam.common.model.MonitoringRules;
import exam.common.model.Question;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import exam.server.db.AttemptDao;
import exam.server.db.CandidateRecord;
import exam.server.db.ExamDao;
import exam.server.db.ShiftDao;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chuyển REQUEST của giáo viên thành lời gọi các service và đóng gói kết quả thành RESPONSE.
 *
 * Mọi action nhận tham số trong request.data (JSON). Thiếu hoặc sai tham số ném IllegalArgumentException,
 * và lỗi đó trở thành RESPONSE { ok: false, error: "..." } (không làm chết connection).
 * Danh sách action và tham số: docs/PROTOCOL.md mục 6.
 */
public class TeacherRequestHandler {

    private final QuestionBankService questionBank;
    private final ExamCreationService examCreation;
    private final ShiftService shiftService;
    private final ExamDao examDao;
    private final ShiftDao shiftDao;
    private final Gson gson = new Gson();

    public TeacherRequestHandler(QuestionBankService questionBank, ExamCreationService examCreation,
                                 ShiftService shiftService, ExamDao examDao, ShiftDao shiftDao) {
        this.questionBank = questionBank;
        this.examCreation = examCreation;
        this.shiftService = shiftService;
        this.examDao = examDao;
        this.shiftDao = shiftDao;
    }

    public ResponseMessage handle(int teacherId, RequestMessage request) {
        if (request.action == null || request.requestId == null) {
            return new ResponseMessage(request.requestId, false, null, "Thiếu requestId hoặc action");
        }
        JsonObject data = request.data != null ? request.data : new JsonObject();

        try {
            JsonObject result = dispatch(teacherId, request.action, data);
            return new ResponseMessage(request.requestId, true, result, null);
        } catch (IllegalArgumentException e) {
            return new ResponseMessage(request.requestId, false, null, e.getMessage());
        } catch (IllegalStateException | SQLException e) {
            // Lỗi của Server (ví dụ database): ghi log đầy đủ, chỉ báo chung cho client.
            e.printStackTrace();
            return new ResponseMessage(request.requestId, false, null, "Lỗi Server khi xử lý " + request.action);
        }
    }

    private JsonObject dispatch(int teacherId, String action, JsonObject data) throws SQLException {
        switch (action) {
            case "LIST_QUESTIONS":
                return listQuestions();
            case "ADD_QUESTION":
                return addQuestion(data);
            case "UPDATE_QUESTION":
                return updateQuestion(data);
            case "DELETE_QUESTION":
                questionBank.deleteQuestion(requireInt(data, "id"));
                return new JsonObject();
            case "IMPORT_QUESTIONS_CSV":
                return importQuestions(data);
            case "LIST_EXAMS":
                return listExams();
            case "CREATE_EXAM":
                return createExam(teacherId, data);
            case "LIST_SHIFTS":
                return listShifts();
            case "GET_SHIFT":
                return getShift(requireString(data, "code"));
            case "CREATE_SHIFT":
                return createShift(data);
            case "START_SHIFT":
                shiftService.startShift(requireString(data, "code").trim().toUpperCase());
                return new JsonObject();
            case "END_SHIFT":
                shiftService.endShift(requireString(data, "code").trim().toUpperCase(), "TEACHER_ENDED");
                return new JsonObject();
            case "LIST_RESULTS":
                return listResults(requireString(data, "code").trim().toUpperCase());
            case "EXPORT_RESULTS_CSV":
                return exportResults(requireString(data, "code").trim().toUpperCase());
            default:
                throw new IllegalArgumentException("Không hỗ trợ action: " + action);
        }
    }

    // ------------------------------------------------------------------
    // Câu hỏi
    // ------------------------------------------------------------------

    private JsonObject listQuestions() {
        JsonArray array = new JsonArray();
        for (Question question : questionBank.listAll()) {
            array.add(questionToJson(question));
        }
        JsonObject result = new JsonObject();
        result.add("questions", array);
        return result;
    }

    private JsonObject addQuestion(JsonObject data) {
        int id = questionBank.addQuestion(requireString(data, "content"), readOptions(data), requireInt(data, "correctIndex"));
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        return result;
    }

    private JsonObject updateQuestion(JsonObject data) {
        questionBank.updateQuestion(requireInt(data, "id"), requireString(data, "content"),
                readOptions(data), requireInt(data, "correctIndex"));
        return new JsonObject();
    }

    private JsonObject importQuestions(JsonObject data) {
        QuestionBankService.CsvImportResult importResult = questionBank.importCsv(requireString(data, "csv"));

        JsonObject result = new JsonObject();
        result.addProperty("added", importResult.added);
        result.addProperty("skipped", importResult.skipped);
        result.add("errors", gson.toJsonTree(importResult.errors));
        return result;
    }

    private JsonObject questionToJson(Question question) {
        JsonObject json = new JsonObject();
        json.addProperty("id", question.id);
        json.addProperty("content", question.content);
        json.add("options", gson.toJsonTree(question.options));
        json.addProperty("correctIndex", question.correctIndex);
        return json;
    }

    // ------------------------------------------------------------------
    // Đề thi
    // ------------------------------------------------------------------

    private JsonObject listExams() throws SQLException {
        JsonArray array = new JsonArray();
        for (ExamDao.ExamRecord exam : examDao.findAll()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", exam.id);
            json.addProperty("title", exam.title);
            json.addProperty("durationMinutes", exam.durationMinutes);
            json.addProperty("questionCount", exam.questionCount);
            array.add(json);
        }
        JsonObject result = new JsonObject();
        result.add("exams", array);
        return result;
    }

    /** Tham số: title, durationMinutes, và một trong hai: questionIds (chọn tay) hoặc randomCount (chọn ngẫu nhiên). */
    private JsonObject createExam(int teacherId, JsonObject data) throws SQLException {
        String title = requireString(data, "title");
        int durationMinutes = requireInt(data, "durationMinutes");

        int examId;
        if (data.has("questionIds")) {
            examId = examCreation.createManual(title, durationMinutes, teacherId, readIntList(data, "questionIds"));
        } else if (data.has("randomCount")) {
            examId = examCreation.createRandom(title, durationMinutes, teacherId, requireInt(data, "randomCount"));
        } else {
            throw new IllegalArgumentException("Cần questionIds (chọn tay) hoặc randomCount (chọn ngẫu nhiên)");
        }

        JsonObject result = new JsonObject();
        result.addProperty("examId", examId);
        result.add("questionIds", gson.toJsonTree(examDao.findQuestionIds(examId)));
        return result;
    }

    // ------------------------------------------------------------------
    // Ca thi
    // ------------------------------------------------------------------

    private JsonObject listShifts() throws SQLException {
        JsonArray array = new JsonArray();
        for (ExamShift shift : shiftDao.findAll()) {
            array.add(shiftToJson(shift));
        }
        JsonObject result = new JsonObject();
        result.add("shifts", array);
        return result;
    }

    private JsonObject getShift(String code) throws SQLException {
        ExamShift shift = shiftDao.findByCode(code.trim().toUpperCase());
        if (shift == null) {
            throw new IllegalArgumentException("Không có ca thi " + code);
        }
        JsonObject result = shiftToJson(shift);

        JsonArray candidates = new JsonArray();
        for (CandidateRecord candidate : shiftDao.findCandidates(shift.id)) {
            JsonObject json = new JsonObject();
            json.addProperty("studentCode", candidate.username);
            json.addProperty("fullName", candidate.fullName);
            candidates.add(json);
        }
        result.add("candidates", candidates);
        result.add("rules", gson.toJsonTree(shift.rules));
        return result;
    }

    /**
     * Tham số: examId, code (tùy chọn), durationMinutes hoặc durationSeconds (ưu tiên giây nếu có),
     * startTime (epoch ms, tùy chọn, 0 = bắt đầu thủ công), candidatesCsv, rules (tùy chọn).
     */
    private JsonObject createShift(JsonObject data) {
        int durationSeconds;
        if (data.has("durationSeconds")) {
            durationSeconds = requireInt(data, "durationSeconds");
        } else {
            durationSeconds = requireInt(data, "durationMinutes") * 60;
        }

        MonitoringRules rules = null;
        if (data.has("rules") && data.get("rules").isJsonObject()) {
            try {
                rules = gson.fromJson(data.get("rules"), MonitoringRules.class);
            } catch (JsonParseException e) {
                throw new IllegalArgumentException("Cấu hình luật giám sát sai định dạng");
            }
        }

        ShiftService.CreateShiftResult created = shiftService.createShift(
                requireInt(data, "examId"),
                optionalString(data, "code"),
                durationSeconds,
                data.has("startTime") ? data.get("startTime").getAsLong() : 0L,
                requireString(data, "candidatesCsv"),
                rules);

        JsonObject result = new JsonObject();
        result.addProperty("shiftId", created.shiftId);
        result.addProperty("code", created.code);
        result.addProperty("candidatesAdded", created.candidatesAdded);
        result.add("errors", gson.toJsonTree(created.errors));
        return result;
    }

    private JsonObject shiftToJson(ExamShift shift) {
        JsonObject json = new JsonObject();
        json.addProperty("id", shift.id);
        json.addProperty("code", shift.code);
        json.addProperty("examId", shift.examId);
        json.addProperty("examTitle", shift.examTitle);
        json.addProperty("status", shift.status);
        json.addProperty("startTime", shift.startTimeServer);
        json.addProperty("durationSeconds", shift.durationSeconds);
        return json;
    }

    // ------------------------------------------------------------------
    // Điểm
    // ------------------------------------------------------------------

    private JsonObject listResults(String code) {
        JsonArray rows = new JsonArray();
        for (AttemptDao.ResultRow row : shiftService.getResults(code)) {
            JsonObject json = new JsonObject();
            json.addProperty("studentCode", row.studentCode);
            json.addProperty("fullName", row.fullName);
            json.addProperty("submitted", row.submitted);
            json.addProperty("autoSubmitted", row.autoSubmitted);
            json.addProperty("score", row.score);
            json.addProperty("correctCount", row.correctCount);
            json.addProperty("totalQuestions", row.totalQuestions);
            json.addProperty("answeredCount", row.answeredCount);
            json.addProperty("submittedAt", row.submittedAt);
            rows.add(json);
        }
        JsonObject result = new JsonObject();
        result.add("rows", rows);
        return result;
    }

    private JsonObject exportResults(String code) {
        JsonObject result = new JsonObject();
        result.addProperty("csv", shiftService.exportResultsCsv(code));
        return result;
    }

    // ------------------------------------------------------------------
    // Đọc tham số JSON (báo lỗi rõ ràng khi thiếu hoặc sai kiểu)
    // ------------------------------------------------------------------

    private String requireString(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Thiếu tham số \"" + key + "\"");
        }
        return element.getAsString();
    }

    private String optionalString(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private int requireInt(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Thiếu tham số \"" + key + "\"");
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tham số \"" + key + "\" phải là số nguyên");
        }
    }

    private List<String> readOptions(JsonObject data) {
        JsonElement element = data.get("options");
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Thiếu tham số \"options\" (mảng 4 đáp án)");
        }
        List<String> options = new ArrayList<>();
        for (JsonElement option : element.getAsJsonArray()) {
            options.add(option.isJsonPrimitive() ? option.getAsString() : null);
        }
        return options;
    }

    private List<Integer> readIntList(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Tham số \"" + key + "\" phải là mảng số");
        }
        List<Integer> numbers = new ArrayList<>();
        try {
            for (JsonElement item : element.getAsJsonArray()) {
                numbers.add(item.getAsInt());
            }
        } catch (NumberFormatException | UnsupportedOperationException e) {
            throw new IllegalArgumentException("Tham số \"" + key + "\" phải là mảng số nguyên");
        }
        return numbers;
    }
}
