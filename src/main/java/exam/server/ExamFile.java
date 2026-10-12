// Mọi thứ liên quan đến file: nạp và kiểm tra đề, bỏ đáp án khi gửi cho sinh viên, chấm điểm, ghi results.csv (Người 3).
package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class ExamFile {
    private ExamFile() {}

    /** Nạp và kiểm tra đề; sai thì ném IllegalArgumentException với thông báo tiếng Việt nói rõ câu nào sai. */
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

    private static IllegalArgumentException bad(String msg) {
        return new IllegalArgumentException(msg);
    }

    /** Trả null nếu không phải số nguyên (chuỗi, số lẻ, thiếu). */
    static Integer asInt(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double d = e.getAsDouble();
        return d == Math.rint(d) ? (int) d : null;
    }

    private static void validate(JsonObject e) {
        Integer minutes = asInt(e.get("durationMinutes"));
        if (Protocol.str(e, "title").isEmpty()) {
            throw bad("Đề thiếu tiêu đề (title)");
        }
        if (minutes == null || minutes <= 0) {
            throw bad("durationMinutes phải là số nguyên > 0");
        }
        if (!(e.get("questions") instanceof JsonArray qs) || qs.isEmpty()) {
            throw bad("Đề phải có ít nhất một câu hỏi (questions)");
        }
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < qs.size(); i++) {
            String at = "Câu thứ " + (i + 1) + ": ";
            if (!(qs.get(i) instanceof JsonObject q)) {
                throw bad(at + "không phải object");
            }
            Integer id = asInt(q.get("id"));
            if (id == null || !ids.add(id)) {
                throw bad(at + "id phải là số nguyên và không được trùng");
            }
            if (Protocol.str(q, "text").isEmpty()) {
                throw bad(at + "thiếu nội dung (text)");
            }
            if (!(q.get("options") instanceof JsonArray opts) || opts.size() < 2) {
                throw bad(at + "cần ít nhất 2 lựa chọn (options)");
            }
            Integer ans = asInt(q.get("answer"));
            if (ans == null || ans < 0 || ans >= opts.size()) {
                throw bad(at + "answer phải là chỉ số từ 0 đến " + (opts.size() - 1));
            }
        }
    }

    /** Bản sao câu hỏi không có "answer": đáp án chỉ nằm ở server. */
    public static JsonArray questionsForClient(JsonObject exam) {
        JsonArray result = new JsonArray();
        for (JsonElement el : exam.getAsJsonArray("questions")) {
            JsonObject q = el.getAsJsonObject();
            JsonObject copy = new JsonObject();
            copy.add("id", q.get("id"));
            copy.add("text", q.get("text"));
            copy.add("options", q.get("options"));
            result.add(copy);
        }
        return result;
    }

    /** Chấm điểm: câu bỏ trống hoặc đáp án không phải số nguyên tính là sai. */
    public static JsonObject grade(JsonObject exam, JsonObject answers) {
        JsonArray qs = exam.getAsJsonArray("questions");
        int correct = 0;
        for (JsonElement el : qs) {
            JsonObject q = el.getAsJsonObject();
            Integer given = asInt(answers.get(String.valueOf(q.get("id").getAsInt())));
            if (given != null && given == q.get("answer").getAsInt()) {
                correct++;
            }
        }
        JsonObject r = new JsonObject();
        r.addProperty("score", qs.isEmpty() ? 0.0 : Math.round(correct * 1000.0 / qs.size()) / 100.0);
        r.addProperty("correct", correct);
        r.addProperty("total", qs.size());
        return r;
    }

    /** Ghi toàn bộ results.csv (UTF-8 có BOM để Excel đọc đúng tiếng Việt), kể cả máy chưa nộp. */
    public static void writeResults(Path file, List<Session> sessions, Function<Session, String> statusOf)
            throws IOException {
        StringBuilder sb = new StringBuilder("﻿studentId,name,score,correct,total,violations,maxAnomaly,status\n");
        for (Session s : sessions) {
            JsonObject r = s.result;
            sb.append(csv(s.id)).append(',').append(csv(s.name)).append(',')
              .append(r == null ? "" : r.get("score").getAsString()).append(',')
              .append(r == null ? "" : r.get("correct").getAsString()).append(',')
              .append(r == null ? "" : r.get("total").getAsString()).append(',')
              .append(s.violations).append(',')
              .append(s.maxAnomaly == null ? "" : s.maxAnomaly.toString()).append(',')
              .append(csv(statusOf.apply(s))).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    /** Giá trị có dấu phẩy, nháy kép hoặc xuống dòng thì bọc nháy kép và nhân đôi nháy kép bên trong. */
    private static String csv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
