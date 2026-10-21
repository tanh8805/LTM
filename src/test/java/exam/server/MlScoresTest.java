// Test ghép câu giải thích bất thường tiếng Việt từ trường reasons của service ML.
package exam.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class MlScoresTest {
    private static JsonObject reason(int feature, double total, double roomTotal, int windows) {
        JsonObject r = new JsonObject();
        r.addProperty("feature", feature);
        r.addProperty("value", total / windows);
        r.addProperty("room", roomTotal / windows);
        r.addProperty("total", total);
        r.addProperty("room_total", roomTotal);
        r.addProperty("windows", windows);
        return r;
    }

    @Test
    void describesReasonsInPlainVietnamese() {
        JsonArray reasons = new JsonArray();
        reasons.add(reason(1, 18, 0.9, 3));
        reasons.add(reason(2, 60, 1.5, 3));
        reasons.add(reason(5, 24, 4.5, 3));
        assertEquals("Mức bất thường: Rất cao (1.00). Trong 1 phút 30 giây gần nhất, máy này: "
            + "rời khỏi bài thi 18 lần (các máy khác thường 1 lần)"
            + " · rời khỏi bài thi tổng cộng 60 giây (các máy khác thường 2 giây)"
            + " · chọn hoặc đổi đáp án 24 lần (các máy khác thường 5 lần)", MlScores.describe(1.0, reasons, 30));
    }

    @Test
    void sentenceForEveryFeature() {
        JsonArray reasons = new JsonArray();
        reasons.add(reason(0, 2, 0, 3));
        reasons.add(reason(3, 1.6, 0.2, 3));
        reasons.add(reason(4, 3, 0, 3));
        assertEquals("Mức bất thường: Cao (0.75). Trong 1 phút 30 giây gần nhất, máy này: "
            + "mở 2 chương trình chạy nền mới (các máy khác thường 0)"
            + " · kết nối tới 2 địa chỉ mạng mới (các máy khác thường 0)"
            + " · vi phạm luật 3 lần (các máy khác thường 0 lần)", MlScores.describe(0.75, reasons, 30));
    }

    @Test
    void levelsFollowTheScore() {
        assertEquals("Mức bất thường: Rất cao (0.90). Không có hành vi nào lệch rõ rệt.", MlScores.describe(0.9, new JsonArray(), 30));
        assertEquals("Mức bất thường: Cao (0.70). Không có hành vi nào lệch rõ rệt.", MlScores.describe(0.7, new JsonArray(), 30));
        assertEquals("Mức bất thường: Trung bình (0.69). Không có hành vi nào lệch rõ rệt.", MlScores.describe(0.69, new JsonArray(), 30));
    }

    @Test
    void durationIsWrittenInMinutesAndSeconds() {
        JsonArray one = new JsonArray();
        one.add(reason(4, 1, 0, 1));
        JsonArray two = new JsonArray();
        two.add(reason(4, 1, 0, 2));
        assertEquals("Mức bất thường: Cao (0.80). Trong 30 giây gần nhất, máy này: vi phạm luật 1 lần (các máy khác thường 0 lần)",
            MlScores.describe(0.8, one, 30));
        assertEquals("Mức bất thường: Cao (0.80). Trong 1 phút gần nhất, máy này: vi phạm luật 1 lần (các máy khác thường 0 lần)",
            MlScores.describe(0.8, two, 30));
    }

    @Test
    void unknownFeatureIsSkipped() {
        JsonArray reasons = new JsonArray();
        reasons.add(reason(9, 1, 0, 3));
        assertEquals("Mức bất thường: Trung bình (0.50). Không có hành vi nào lệch rõ rệt.", MlScores.describe(0.5, reasons, 30));
    }

    @Test
    void durationFollowsWindowLength() {
        JsonArray reasons = new JsonArray();
        reasons.add(reason(4, 1, 0, 3));
        assertEquals("Mức bất thường: Cao (0.80). Trong 1 phút gần nhất, máy này: vi phạm luật 1 lần (các máy khác thường 0 lần)",
            MlScores.describe(0.8, reasons, 20));
    }
}
