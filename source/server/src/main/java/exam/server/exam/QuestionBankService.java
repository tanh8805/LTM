// Owner: Nguoi2

package exam.server.exam;

import exam.common.model.Question;
import exam.server.db.QuestionDao;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ngân hàng câu hỏi: thêm, sửa, xóa, đọc, và nhập từ CSV. Kiểm tra dữ liệu trước khi ghi vào database. */
public class QuestionBankService {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_OPTION_LENGTH = 300;
    private static final int MAX_CSV_ROWS = 5000;

    /** Kết quả nhập CSV: thêm được bao nhiêu câu, bỏ qua bao nhiêu dòng và vì sao. */
    public static class CsvImportResult {
        public int added;
        public int skipped;
        public final List<String> errors = new ArrayList<>();
    }

    private final QuestionDao questionDao;

    public QuestionBankService(QuestionDao questionDao) {
        this.questionDao = questionDao;
    }

    public List<Question> listAll() {
        try {
            return questionDao.findAll();
        } catch (SQLException e) {
            throw new IllegalStateException("Không đọc được ngân hàng câu hỏi", e);
        }
    }

    /** Trả về id câu hỏi mới. Ném IllegalArgumentException nếu dữ liệu không hợp lệ. */
    public int addQuestion(String content, List<String> options, int correctIndex) {
        Question question = validate(0, content, options, correctIndex);
        try {
            return questionDao.insert(question);
        } catch (SQLException e) {
            throw new IllegalStateException("Không thêm được câu hỏi", e);
        }
    }

    public void updateQuestion(int id, String content, List<String> options, int correctIndex) {
        Question question = validate(id, content, options, correctIndex);
        try {
            if (!questionDao.update(question)) {
                throw new IllegalArgumentException("Không có câu hỏi id=" + id);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không sửa được câu hỏi", e);
        }
    }

    public void deleteQuestion(int id) {
        try {
            if (questionDao.isUsedInExam(id)) {
                throw new IllegalArgumentException("Câu hỏi id=" + id + " đang nằm trong một đề thi nên không xóa được");
            }
            if (!questionDao.delete(id)) {
                throw new IllegalArgumentException("Không có câu hỏi id=" + id);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không xóa được câu hỏi", e);
        }
    }

    /**
     * Nhập câu hỏi từ nội dung file CSV. Cột: câu hỏi, A, B, C, D, đáp án đúng (A-D hoặc 1-4).
     * Dòng tiêu đề (nếu có) được tự nhận ra. MỖI dòng lỗi chỉ bị bỏ qua và ghi vào errors,
     * các dòng tốt vẫn được nhập: một dòng sai không làm hỏng cả file.
     */
    public CsvImportResult importCsv(String csvText) {
        CsvImportResult result = new CsvImportResult();

        if (csvText == null || csvText.isBlank()) {
            throw new IllegalArgumentException("File CSV rỗng");
        }
        if (csvText.indexOf('�') >= 0) {
            // Ký tự thay thế xuất hiện khi đọc file không phải UTF-8 bằng UTF-8.
            throw new IllegalArgumentException("File CSV không đúng mã hóa UTF-8, hãy lưu lại bằng UTF-8");
        }
        if (csvText.startsWith("﻿")) {
            csvText = csvText.substring(1); // bỏ BOM mà Excel thêm vào đầu file UTF-8
        }

        String[] lines = csvText.split("\\r?\\n");
        if (lines.length > MAX_CSV_ROWS + 1) {
            throw new IllegalArgumentException("File CSV quá lớn (tối đa " + MAX_CSV_ROWS + " dòng)");
        }

        Set<String> contentsInThisFile = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            try {
                importOneLine(line, i == 0, contentsInThisFile, result);
            } catch (IllegalArgumentException e) {
                result.skipped++;
                result.errors.add("dòng " + lineNumber + ": " + e.getMessage());
            } catch (SQLException e) {
                throw new IllegalStateException("Lỗi database khi nhập dòng " + lineNumber, e);
            }
        }
        return result;
    }

    private void importOneLine(String line, boolean isFirstLine, Set<String> contentsInThisFile,
                               CsvImportResult result) throws SQLException {
        List<String> fields = Csv.parseLine(line);

        if (isFirstLine && looksLikeHeader(fields)) {
            return;
        }
        if (fields.size() < 6) {
            throw new IllegalArgumentException("thiếu cột (cần 6 cột: câu hỏi, A, B, C, D, đáp án đúng; có " + fields.size() + ")");
        }

        int correctIndex = parseCorrectAnswer(fields.get(5));
        List<String> options = List.of(fields.get(1), fields.get(2), fields.get(3), fields.get(4));
        Question question = validate(0, fields.get(0), options, correctIndex);

        String normalizedContent = question.content.toLowerCase();
        if (contentsInThisFile.contains(normalizedContent) || questionDao.existsByContent(question.content)) {
            throw new IllegalArgumentException("câu hỏi bị trùng nội dung");
        }

        questionDao.insert(question);
        contentsInThisFile.add(normalizedContent);
        result.added++;
    }

    private boolean looksLikeHeader(List<String> fields) {
        String first = fields.get(0).toLowerCase();
        return first.equals("question") || first.equals("câu hỏi") || first.equals("content");
    }

    /** "A".."D" hoặc "1".."4" thành 0..3. */
    private int parseCorrectAnswer(String text) {
        String normalized = text.trim().toUpperCase();
        switch (normalized) {
            case "A":
            case "1":
                return 0;
            case "B":
            case "2":
                return 1;
            case "C":
            case "3":
                return 2;
            case "D":
            case "4":
                return 3;
            default:
                throw new IllegalArgumentException("đáp án đúng \"" + text + "\" không hợp lệ (chỉ nhận A, B, C, D hoặc 1-4)");
        }
    }

    private Question validate(int id, String content, List<String> options, int correctIndex) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Nội dung câu hỏi không được rỗng");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Nội dung câu hỏi quá dài (tối đa " + MAX_CONTENT_LENGTH + " ký tự)");
        }
        if (options == null || options.size() != 4) {
            throw new IllegalArgumentException("Câu hỏi phải có đúng 4 đáp án");
        }

        List<String> cleanOptions = new ArrayList<>();
        Set<String> seenOptions = new HashSet<>();
        for (String option : options) {
            if (option == null || option.isBlank()) {
                throw new IllegalArgumentException("Đáp án không được rỗng");
            }
            if (option.length() > MAX_OPTION_LENGTH) {
                throw new IllegalArgumentException("Đáp án quá dài (tối đa " + MAX_OPTION_LENGTH + " ký tự)");
            }
            if (!seenOptions.add(option.trim().toLowerCase())) {
                throw new IllegalArgumentException("Hai đáp án giống nhau: \"" + option.trim() + "\"");
            }
            cleanOptions.add(option.trim());
        }
        if (correctIndex < 0 || correctIndex > 3) {
            throw new IllegalArgumentException("Đáp án đúng phải là 0 (A), 1 (B), 2 (C) hoặc 3 (D)");
        }
        return new Question(id, content.trim(), cleanOptions, correctIndex);
    }
}
