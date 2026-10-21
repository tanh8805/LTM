package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class ExamFile {
    private static final String RESULTS_HEADER =
        "﻿studentId,name,score,correct,total,violations,maxAnomaly,status,samples,flaggedCount\n";

    private ExamFile() {}

    public static JsonObject load(Path file) throws IOException {
        JsonObject exam;
        try {
            exam = Protocol.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("File đề không phải JSON hợp lệ");
        }
        validate(exam);
        return exam;
    }

    // Trả null nếu không phải số nguyên (chuỗi, số lẻ, thiếu)
    static Integer asInt(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double d = e.getAsDouble();
        if (d != Math.rint(d)) {
            return null;
        }
        return (int) d;
    }

    private static IllegalArgumentException bad(String message) {
        return new IllegalArgumentException(message);
    }

    private static void validate(JsonObject exam) {
        if (Protocol.str(exam, "title").isEmpty()) {
            throw bad("Đề thiếu tiêu đề (title)");
        }
        Integer minutes = asInt(exam.get("durationMinutes"));
        if (minutes == null || minutes <= 0) {
            throw bad("durationMinutes phải là số nguyên > 0");
        }
        JsonElement questions = exam.get("questions");
        if (questions == null || !questions.isJsonArray() || questions.getAsJsonArray().isEmpty()) {
            throw bad("Đề phải có ít nhất một câu hỏi (questions)");
        }
        JsonArray list = questions.getAsJsonArray();
        Set<Integer> usedIds = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            validateQuestion(i + 1, list.get(i), usedIds);
        }
    }

    private static void validateQuestion(int number, JsonElement element, Set<Integer> usedIds) {
        String at = "Câu thứ " + number + ": ";
        if (!element.isJsonObject()) {
            throw bad(at + "không phải object");
        }
        JsonObject q = element.getAsJsonObject();

        Integer id = asInt(q.get("id"));
        if (id == null || !usedIds.add(id)) {
            throw bad(at + "id phải là số nguyên và không được trùng");
        }
        if (Protocol.str(q, "text").isEmpty()) {
            throw bad(at + "thiếu nội dung (text)");
        }
        JsonElement options = q.get("options");
        if (options == null || !options.isJsonArray() || options.getAsJsonArray().size() < 2) {
            throw bad(at + "cần ít nhất 2 lựa chọn (options)");
        }
        int optionCount = options.getAsJsonArray().size();
        Integer answer = asInt(q.get("answer"));
        if (answer == null || answer < 0 || answer >= optionCount) {
            throw bad(at + "answer phải là chỉ số từ 0 đến " + (optionCount - 1));
        }
    }

    public static JsonArray questionsForClient(JsonObject exam) {
        // đáp án chỉ nằm ở server nên bản gửi cho sinh viên không có "answer"
        JsonArray result = new JsonArray();
        for (JsonElement element : exam.getAsJsonArray("questions")) {
            JsonObject q = element.getAsJsonObject();
            JsonObject copy = new JsonObject();
            copy.add("id", q.get("id"));
            copy.add("text", q.get("text"));
            copy.add("options", q.get("options"));
            result.add(copy);
        }
        return result;
    }

    public static JsonObject grade(JsonObject exam, JsonObject answers) {
        JsonArray questions = exam.getAsJsonArray("questions");
        int correct = 0;
        for (JsonElement element : questions) {
            JsonObject q = element.getAsJsonObject();
            String questionId = String.valueOf(q.get("id").getAsInt());
            // câu bỏ trống hoặc đáp án không phải số nguyên thì asInt trả null, tính là sai
            Integer given = asInt(answers.get(questionId));
            if (given != null && given == q.get("answer").getAsInt()) {
                correct++;
            }
        }
        int total = questions.size();
        double score = 0.0;
        if (total > 0) {
            // thang 10, làm tròn 2 chữ số thập phân
            score = Math.round(correct * 1000.0 / total) / 100.0;
        }
        JsonObject result = new JsonObject();
        result.addProperty("score", score);
        result.addProperty("correct", correct);
        result.addProperty("total", total);
        return result;
    }

    public static void writeResults(Path file, Collection<Session> sessions, boolean examStarted) throws IOException {
        StringBuilder sb = new StringBuilder(RESULTS_HEADER);
        for (Session s : sessions) {
            sb.append(resultsRow(s, examStarted));
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String resultsRow(Session s, boolean examStarted) {
        // máy chưa nộp thì để trống ba cột điểm
        String score = "";
        String correct = "";
        String total = "";
        if (s.result != null) {
            score = s.result.get("score").getAsString();
            correct = s.result.get("correct").getAsString();
            total = s.result.get("total").getAsString();
        }
        String maxAnomaly = "";
        if (s.maxAnomaly != null) {
            maxAnomaly = s.maxAnomaly.toString();
        }
        return csv(s.id) + "," + csv(s.name) + "," + score + "," + correct + "," + total + ","
            + s.violations + "," + maxAnomaly + "," + csv(s.status(examStarted)) + ","
            + s.samples.size() + "," + s.flaggedCount + "\n";
    }

    static String csv(String value) {
        // có dấu phẩy, nháy kép hoặc xuống dòng thì bọc nháy kép và nhân đôi nháy kép bên trong
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
