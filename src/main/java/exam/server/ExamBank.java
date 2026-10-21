package exam.server;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Soạn đề: dựng và ghi file đề JSON đúng định dạng của ExamFile, nhập câu hỏi từ CSV. */
public final class ExamBank {
    public record Question(String text, List<String> options, int answer) {}

    public static final Path DIR = Path.of("exams");
    private static final String CSV_HEADER = "question,optionA,optionB,optionC,optionD,answer";
    private static final int MAX_ERRORS = 10;

    private ExamBank() {}

    /** Dựng object đề; id các câu tự đánh số lại từ 1 theo thứ tự. */
    public static JsonObject build(String title, int minutes, List<Question> questions) {
        JsonObject exam = new JsonObject();
        exam.addProperty("title", title.trim());
        exam.addProperty("durationMinutes", minutes);
        JsonArray array = new JsonArray();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            JsonObject item = new JsonObject();
            item.addProperty("id", i + 1);
            item.addProperty("text", q.text().trim());
            JsonArray options = new JsonArray();
            q.options().forEach(options::add);
            item.add("options", options);
            item.addProperty("answer", q.answer());
            array.add(item);
        }
        exam.add("questions", array);
        return exam;
    }

    public static List<Question> questionsOf(JsonObject exam) {
        List<Question> list = new ArrayList<>();
        for (JsonElement e : exam.getAsJsonArray("questions")) {
            JsonObject q = e.getAsJsonObject();
            List<String> options = new ArrayList<>();
            q.getAsJsonArray("options").forEach(o -> options.add(o.getAsString()));
            list.add(new Question(q.get("text").getAsString(), options, q.get("answer").getAsInt()));
        }
        return list;
    }

    /** Ghi ra file tạm, kiểm tra bằng ExamFile.load rồi mới thay file thật; đề sai thì file cũ còn nguyên. */
    public static void save(Path file, JsonObject exam) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(exam);
        Files.writeString(temp, json + "\n", StandardCharsets.UTF_8);
        try {
            ExamFile.load(temp);
        } catch (IllegalArgumentException e) {
            Files.delete(temp);
            throw e;
        }
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Tên file từ tiêu đề: bỏ dấu, chữ thường, nối bằng gạch ngang. */
    public static String fileName(String title) {
        String plain = Normalizer.normalize(title.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return (plain.isEmpty() ? "de-moi" : plain) + ".json";
    }

    public static List<Question> loadCsv(Path file) throws IOException {
        try {
            return parseCsv(Files.readString(file, StandardCharsets.UTF_8));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("File phải lưu dạng UTF-8");
        }
    }

    /** CSV question,optionA,optionB,optionC,optionD,answer (answer là A-D); lỗi thì liệt kê "Dòng n: lý do". */
    public static List<Question> parseCsv(String text) {
        if (text.startsWith("﻿")) { text = text.substring(1); }
        String[] lines = text.split("\\r?\\n", -1);
        List<String> errors = new ArrayList<>();
        List<Question> questions = new ArrayList<>();
        if (!lines[0].replace(" ", "").equalsIgnoreCase(CSV_HEADER)) {
            errors.add("Dòng 1: dòng đầu phải là " + CSV_HEADER);
        }
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) { continue; }
            try {
                questions.add(csvQuestion(splitCsv(lines[i])));
            } catch (IllegalArgumentException e) {
                errors.add("Dòng " + (i + 1) + ": " + e.getMessage());
            }
        }
        if (errors.isEmpty() && questions.isEmpty()) { errors.add("File không có câu hỏi nào"); }
        if (errors.size() > MAX_ERRORS) {
            int more = errors.size() - MAX_ERRORS;
            errors = new ArrayList<>(errors.subList(0, MAX_ERRORS));
            errors.add("... và " + more + " lỗi nữa");
        }
        if (!errors.isEmpty()) { throw new IllegalArgumentException(String.join("\n", errors)); }
        return questions;
    }

    private static Question csvQuestion(List<String> cols) {
        if (cols.size() != 6) { throw new IllegalArgumentException("phải có đúng 6 cột"); }
        String text = cols.get(0).trim();
        if (text.isEmpty()) { throw new IllegalArgumentException("thiếu nội dung câu hỏi"); }
        List<String> options = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String option = cols.get(i).trim();
            if (option.isEmpty()) { continue; }
            if (options.size() != i - 1) { throw new IllegalArgumentException("các lựa chọn phải liền nhau, không bỏ trống ở giữa"); }
            options.add(option);
        }
        if (options.size() < 2) { throw new IllegalArgumentException("cần ít nhất 2 lựa chọn"); }
        String letter = cols.get(5).trim().toUpperCase(Locale.ROOT);
        int answer = "ABCD".indexOf(letter);
        if (letter.length() != 1 || answer < 0 || answer >= options.size()) {
            throw new IllegalArgumentException("đáp án phải là A-" + (char) ('A' + options.size() - 1) + " (có lựa chọn tương ứng)");
        }
        return new Question(text, options, answer);
    }

    /** Tách một dòng CSV, hỗ trợ ô bọc nháy kép (có dấu phẩy, "" là một nháy kép). */
    static List<String> splitCsv(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted && c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                cell.append('"');
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cols.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cols.add(cell.toString());
        return cols;
    }
}
