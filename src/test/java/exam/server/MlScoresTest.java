// Test ghép chuỗi lý do bất thường tiếng Việt từ trường reasons của service ML.
package exam.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class MlScoresTest {
    private static JsonObject reason(int feature, double value, double room) {
        JsonObject r = new JsonObject();
        r.addProperty("feature", feature);
        r.addProperty("value", value);
        r.addProperty("room", room);
        return r;
    }

    @Test
    void describesReasonsInVietnamese() {
        JsonArray reasons = new JsonArray();
        reasons.add(reason(1, 6.0, 0.3));
        reasons.add(reason(2, 20.0, 0.5));
        reasons.add(reason(5, 8.0, 1.5));
        assertEquals("1.00 – Rời cửa sổ 6 lần (phòng 0.3), Thời gian rời cửa sổ 20 giây (phòng 0.5), "
            + "Số lần trả lời 8 (phòng 1.5)", MlScores.describe(1.0, reasons));
    }

    @Test
    void roundsToOneDecimalAndNamesAllFeatures() {
        JsonArray reasons = new JsonArray();
        reasons.add(reason(0, 2.04, 0.0));
        reasons.add(reason(3, 1.26, 0.0));
        reasons.add(reason(4, 3.0, 0.0));
        assertEquals("0.85 – Tiến trình mới 2 (phòng 0), Địa chỉ mạng mới 1.3 (phòng 0), Vi phạm luật 3 (phòng 0)",
            MlScores.describe(0.85, reasons));
    }

    @Test
    void withoutReasonsOnlyTheScoreRemains() {
        assertEquals("0.90", MlScores.describe(0.9, new JsonArray()));
    }

    @Test
    void unknownFeatureIsSkipped() {
        JsonArray reasons = new JsonArray();
        reasons.add(reason(9, 1.0, 0.0));
        assertEquals("0.50", MlScores.describe(0.5, reasons));
    }
}
