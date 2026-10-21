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
    public record Event(LocalDateTime time, String studentId, String name, String type, String detail) {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String HEADER = "﻿time,studentId,name,type,detail\n";

    private final List<Event> events = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private BufferedWriter eventsOut;
    private BufferedWriter logOut;

    public static String folderName(LocalDateTime start, String title) {
        // bỏ dấu tiếng Việt (đ/Đ không tự tách dấu nên đổi tay), ký tự lạ thành gạch dưới
        String noDiacritics = Normalizer.normalize(title.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        String plain = noDiacritics.replaceAll("\\p{M}+", "");
        plain = plain.replaceAll("[^A-Za-z0-9]+", "_");
        plain = plain.replaceAll("^_+|_+$", "");
        if (plain.isEmpty()) {
            plain = "de_thi";
        }
        return start.format(FOLDER_TIME) + "_" + plain;
    }

    public synchronized void record(String studentId, String name, String type, String detail) {
        Event e = new Event(LocalDateTime.now(), studentId, name, type, detail);
        events.add(e);
        if (eventsOut != null) {
            write(eventsOut, eventLine(e));
        }
    }

    public synchronized void appendLog(String line) {
        lines.add(line);
        if (logOut != null) {
            write(logOut, line + "\n");
        }
    }

    public synchronized List<Event> history(String studentId) {
        List<Event> result = new ArrayList<>();
        for (Event e : events) {
            if (e.studentId().equals(studentId)) {
                result.add(e);
            }
        }
        return result;
    }

    public synchronized Path attach(Path root, String title) throws IOException {
        close();
        Path base = root.resolve(folderName(LocalDateTime.now(), title));
        Path dir = base;
        for (int n = 2; Files.exists(dir); n++) {
            // trùng giây với kỳ khác thì thêm số phía sau
            dir = root.resolve(base.getFileName() + "-" + n);
        }
        Files.createDirectories(dir);
        BufferedWriter newEventsOut = Files.newBufferedWriter(dir.resolve("events.csv"), StandardCharsets.UTF_8);
        BufferedWriter newLogOut = Files.newBufferedWriter(dir.resolve("server.log"), StandardCharsets.UTF_8);
        eventsOut = newEventsOut;
        logOut = newLogOut;

        // ghi nốt những gì đã xảy ra trước khi bấm Bắt đầu
        write(eventsOut, HEADER);
        for (Event e : events) {
            write(eventsOut, eventLine(e));
        }
        for (String line : lines) {
            write(logOut, line + "\n");
        }
        return dir;
    }

    public synchronized void reset() {
        close();
        events.clear();
        lines.clear();
    }

    public synchronized void close() {
        closeWriter(eventsOut);
        closeWriter(logOut);
        eventsOut = null;
        logOut = null;
    }

    private static void closeWriter(BufferedWriter w) {
        if (w == null) {
            return;
        }
        try {
            w.close();
        } catch (IOException e) {
            // đã đóng hoặc hỏng sẵn, không còn gì để làm
        }
    }

    private static String eventLine(Event e) {
        String time = e.time().format(TIME);
        return time + "," + ExamFile.csv(e.studentId()) + "," + ExamFile.csv(e.name()) + ","
            + e.type() + "," + ExamFile.csv(e.detail()) + "\n";
    }

    private void write(BufferedWriter w, String text) {
        try {
            w.write(text);
            w.flush();        // flush ngay để server có sập thì dòng đã ghi vẫn còn trên đĩa
        } catch (IOException ex) {
            System.err.println("Không ghi được hồ sơ kỳ thi: " + ex.getMessage());
        }
    }
}
