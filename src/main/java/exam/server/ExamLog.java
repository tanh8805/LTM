// Hồ sơ của một kỳ thi: giữ sự kiện và dòng log trong bộ nhớ, ghi ngay vào events.csv và server.log khi đã có thư mục (Người 3).
package exam.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ExamLog {
    /** Một sự kiện của một thí sinh (studentId rỗng = sự kiện của cả phòng, ví dụ EXAM_END). */
    public record Event(LocalDateTime time, String studentId, String name, String type, String detail) {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String HEADER = "﻿time,studentId,name,type,detail\n";    // BOM để Excel đọc đúng tiếng Việt

    private final List<Event> events = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private BufferedWriter eventsOut, logOut;
    private Path dir;

    /** Tên thư mục: thời điểm bắt đầu + tên đề bỏ dấu, các từ nối bằng gạch dưới. */
    public static String folderName(LocalDateTime start, String title) {
        String plain = Normalizer.normalize(title.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return start.format(FOLDER_TIME) + "_" + (plain.isEmpty() ? "de_thi" : plain);
    }

    public synchronized Path dir() { return dir; }

    public synchronized void record(String studentId, String name, String type, String detail) {
        Event e = new Event(LocalDateTime.now(), studentId, name, type, detail);
        events.add(e);
        if (eventsOut != null) { write(eventsOut, line(e)); }
    }

    public synchronized void appendLog(String line) {
        lines.add(line);
        if (logOut != null) { write(logOut, line + "\n"); }
    }

    public synchronized List<Event> history(String studentId) {
        return events.stream().filter(e -> e.studentId().equals(studentId)).toList();
    }

    /** Tạo thư mục kỳ thi dưới root rồi ghi nốt những gì đã xảy ra trước khi bấm Bắt đầu. */
    public synchronized Path attach(Path root, String title) throws IOException {
        close();
        Path base = root.resolve(folderName(LocalDateTime.now(), title));
        Path d = base;
        for (int n = 2; Files.exists(d); n++) { d = root.resolve(base.getFileName() + "-" + n); }    // trùng giây thì thêm số
        Files.createDirectories(d);
        BufferedWriter ev = open(d.resolve("events.csv")), lg = open(d.resolve("server.log"));
        eventsOut = ev;
        logOut = lg;
        dir = d;
        write(eventsOut, HEADER);
        events.forEach(e -> write(eventsOut, line(e)));
        lines.forEach(l -> write(logOut, l + "\n"));
        return d;
    }

    /** Bắt đầu kỳ thi mới: đóng file, xoá hết sự kiện và log của kỳ trước. */
    public synchronized void reset() {
        close();
        events.clear();
        lines.clear();
        dir = null;
    }

    public synchronized void close() {
        for (BufferedWriter w : new BufferedWriter[] {eventsOut, logOut}) {
            try {
                if (w != null) { w.close(); }
            } catch (IOException e) {
                // đã đóng hoặc hỏng sẵn, không còn gì để làm
            }
        }
        eventsOut = null;
        logOut = null;
    }

    private static BufferedWriter open(Path file) throws IOException {
        return Files.newBufferedWriter(file, StandardCharsets.UTF_8);
    }

    private static String line(Event e) {
        return String.join(",", e.time().format(TIME), ExamFile.csv(e.studentId()), ExamFile.csv(e.name()),
            e.type(), ExamFile.csv(e.detail())) + "\n";
    }

    /** Ghi xong flush ngay để server có sập thì dòng đã ghi vẫn còn trên đĩa. */
    private void write(BufferedWriter w, String text) {
        try {
            w.write(text);
            w.flush();
        } catch (IOException ex) {
            System.err.println("Không ghi được hồ sơ kỳ thi: " + ex.getMessage());
        }
    }
}
