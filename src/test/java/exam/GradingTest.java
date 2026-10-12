// Test chấm điểm: score = đúng / tổng * 10, làm tròn 2 chữ số.
package exam;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.server.ExamFile;
import org.junit.jupiter.api.Test;

class GradingTest {

    /** Đề n câu, câu nào cũng 4 lựa chọn và đáp án đúng là lựa chọn số 1. */
    private static JsonObject exam(int n) {
        JsonArray qs = new JsonArray();
        for (int i = 1; i <= n; i++) {
            JsonObject q = new JsonObject();
            q.addProperty("id", i);
            q.addProperty("answer", 1);
            qs.add(q);
        }
        JsonObject exam = new JsonObject();
        exam.add("questions", qs);
        return exam;
    }

    private static JsonObject answers(Object... pairs) {
        JsonObject a = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] instanceof Number n) {
                a.addProperty((String) pairs[i], n);
            } else {
                a.addProperty((String) pairs[i], (String) pairs[i + 1]);
            }
        }
        return a;
    }

    @Test
    void allCorrectGivesTen() {
        JsonObject r = ExamFile.grade(exam(4), answers("1", 1, "2", 1, "3", 1, "4", 1));
        assertEquals(10.0, r.get("score").getAsDouble());
        assertEquals(4, r.get("correct").getAsInt());
        assertEquals(4, r.get("total").getAsInt());
    }

    @Test
    void allWrongGivesZero() {
        JsonObject r = ExamFile.grade(exam(4), answers("1", 0, "2", 2, "3", 3, "4", 0));
        assertEquals(0.0, r.get("score").getAsDouble());
        assertEquals(0, r.get("correct").getAsInt());
    }

    @Test
    void blankAnswersCountAsWrong() {
        JsonObject r = ExamFile.grade(exam(4), answers("1", 1));
        assertEquals(2.5, r.get("score").getAsDouble());
        assertEquals(1, r.get("correct").getAsInt());
    }

    @Test
    void nonNumericAnswerIsWrong() {
        JsonObject r = ExamFile.grade(exam(4), answers("1", "abc", "2", 1));
        assertEquals(1, r.get("correct").getAsInt());
    }

    @Test
    void roundsToTwoDecimals() {
        JsonObject r = ExamFile.grade(exam(3), answers("1", 1));
        assertEquals(3.33, r.get("score").getAsDouble());
    }
}
