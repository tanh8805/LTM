package exam.server;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Danh sách tài khoản sinh viên (CSV: studentId,name,password). Rỗng nghĩa là chưa nạp. */
public final class StudentList {
    public record Account(String id, String name, String password) {}

    public static final Path FILE = Path.of("config", "students.csv");
    private static final String HEADER = "studentId,name,password";
    private static final int MAX_ERRORS = 10;

    private final Map<String, Account> accounts = new LinkedHashMap<>();

    public static StudentList empty() { return new StudentList(); }

    public int size() { return accounts.size(); }

    /** Mã SV so khớp không phân biệt hoa thường và bỏ mọi khoảng trắng. */
    public static String key(String id) { return id.replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }

    public List<Account> accounts() { return new ArrayList<>(accounts.values()); }

    public Account find(String id) { return accounts.get(key(id)); }

    /** Đọc cả file; có dòng lỗi thì ném IllegalArgumentException liệt kê "Dòng n: lý do". */
    public static StudentList parse(String text) {
        if (text.startsWith("﻿")) { text = text.substring(1); }      // bỏ BOM nếu có
        StudentList list = new StudentList();
        List<String> errors = new ArrayList<>();
        String[] lines = text.split("\\r?\\n", -1);
        if (!lines[0].replace(" ", "").equalsIgnoreCase(HEADER)) {
            errors.add("Dòng 1: dòng đầu phải là " + HEADER);
        }
        Map<String, Integer> seenAt = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) { continue; }
            String error = list.addLine(lines[i], i + 1, seenAt);
            if (error != null) { errors.add("Dòng " + (i + 1) + ": " + error); }
        }
        if (errors.isEmpty() && list.size() == 0) { errors.add("Danh sách không có sinh viên nào"); }
        if (!errors.isEmpty()) {
            if (errors.size() > MAX_ERRORS) {
                int more = errors.size() - MAX_ERRORS;
                errors = new ArrayList<>(errors.subList(0, MAX_ERRORS));
                errors.add("... và " + more + " lỗi nữa");
            }
            throw new IllegalArgumentException(String.join("\n", errors));
        }
        return list;
    }

    /** Thêm một dòng dữ liệu; trả lý do lỗi hoặc null nếu hợp lệ. */
    private String addLine(String line, int lineNumber, Map<String, Integer> seenAt) {
        String[] cols = line.split(",", -1);
        if (cols.length != 3) { return "phải có đúng 3 cột (studentId,name,password)"; }
        String id = cols[0].trim(), name = cols[1].trim(), password = cols[2].trim();
        if (id.isEmpty()) { return "thiếu mã sinh viên"; }
        if (name.isEmpty()) { return "thiếu họ tên"; }
        if (password.isEmpty()) { return "thiếu mật khẩu"; }
        String key = key(id);
        if (seenAt.containsKey(key)) { return "trùng mã sinh viên với dòng " + seenAt.get(key); }
        seenAt.put(key, lineNumber);
        accounts.put(key, new Account(id, name, password));
        return null;
    }

    public static StudentList load(Path file) throws IOException {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("File phải lưu dạng UTF-8");
        }
    }

    /** Ghi lại thành CSV UTF-8 có BOM để Excel đọc được tiếng Việt. */
    public void save(Path file) throws IOException {
        StringBuilder text = new StringBuilder("﻿" + HEADER + "\n");
        for (Account a : accounts.values()) {
            text.append(a.id()).append(',').append(a.name()).append(',').append(a.password()).append('\n');
        }
        if (file.getParent() != null) { Files.createDirectories(file.getParent()); }
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
