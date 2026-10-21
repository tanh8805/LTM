package exam.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExamBankTest {
    @TempDir Path tmp;

    @Test
    void saveExamRoundTrip() throws IOException {
        List<ExamBank.Question> questions = List.of(
            new ExamBank.Question("TCP là gì?", List.of("Không kết nối", "Hướng kết nối"), 1),
            new ExamBank.Question("Cổng mặc định của HTTP?", List.of("21", "80", "443", "25", "53", "110"), 1));
        Path file = tmp.resolve("de.json");
        ExamBank.save(file, ExamBank.build("Đề thử", 20, questions));

        JsonObject loaded = ExamFile.load(file);
        assertEquals("Đề thử", loaded.get("title").getAsString());
        assertEquals(20, loaded.get("durationMinutes").getAsInt());
        assertEquals(questions, ExamBank.questionsOf(loaded));
        assertEquals(2, loaded.getAsJsonArray("questions").get(1).getAsJsonObject().get("id").getAsInt(), "id tự đánh số lại từ 1");
        assertFalse(Files.exists(tmp.resolve("de.json.tmp")));
        assertEquals("de-thu.json", ExamBank.fileName("Đề thử!"));
    }

    @Test
    void csvImportBuildsQuestions() {
        String csv = "﻿question,optionA,optionB,optionC,optionD,answer\n"
            + "\"Giao thức nào, ở tầng giao vận, đảm bảo thứ tự?\",UDP,TCP,IP,ARP,b\n"
            + "\n"
            + "HTTP chạy trên cổng?,80,443,,,A\n";
        List<ExamBank.Question> questions = ExamBank.parseCsv(csv);
        assertEquals(2, questions.size());
        assertEquals("Giao thức nào, ở tầng giao vận, đảm bảo thứ tự?", questions.get(0).text());
        assertEquals(1, questions.get(0).answer());
        assertEquals(List.of("80", "443"), questions.get(1).options());
        assertEquals(0, questions.get(1).answer());
    }

    @Test
    void invalidAnswerRejected() throws IOException {
        String csv = "question,optionA,optionB,optionC,optionD,answer\nCâu 1,a,b,c,d,E\nCâu 2,a,b,,,C\nCâu 3,a,b,c,d,B\n";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ExamBank.parseCsv(csv));
        assertTrue(e.getMessage().contains("Dòng 2:") && e.getMessage().contains("Dòng 3:"), e.getMessage());
        assertFalse(e.getMessage().contains("Dòng 4:"));

        // đáp án ngoài khoảng cũng bị ExamFile.load chặn khi lưu, và file cũ giữ nguyên
        Path file = tmp.resolve("de.json");
        ExamBank.save(file, ExamBank.build("Đề", 5, List.of(new ExamBank.Question("Q", List.of("a", "b"), 0))));
        String before = Files.readString(file);
        assertThrows(IllegalArgumentException.class,
            () -> ExamBank.save(file, ExamBank.build("Đề", 5, List.of(new ExamBank.Question("Q", List.of("a", "b"), 5)))));
        assertEquals(before, Files.readString(file));
        assertFalse(Files.exists(tmp.resolve("de.json.tmp")));
    }
}
