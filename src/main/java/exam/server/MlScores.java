// Nối service ML với các Session: gom mẫu FEATURES gửi đi và áp điểm bất thường nhận về (Người 4).
package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class MlScores {
    static final int ML_MIN_SAMPLES = 20;          // phải khớp MIN_SAMPLES trong ml/ml_service.py
    private int lastShortage = -1;                 // số mẫu ở lần đã log "chưa đủ dữ liệu", để không lặp lại mỗi 30 giây

    /** Bản sao mọi mẫu FEATURES của cả phòng, mỗi mẫu có thêm studentId. Gọi trong khối synchronized của phòng. */
    static JsonArray samples(Collection<Session> sessions) {
        JsonArray samples = new JsonArray();
        for (Session s : sessions) {
            for (JsonObject sample : s.samples) {
                JsonObject copy = sample.deepCopy();
                copy.addProperty("studentId", s.id);
                samples.add(copy);
            }
        }
        return samples;
    }

    /** Ghi điểm vào Session, log và báo onChange(session, ANOMALY_ON/OFF) khi một máy bắt đầu/hết bị cờ; chưa đủ mẫu thì chỉ log (khi số mẫu đổi). */
    void apply(JsonObject response, Map<String, Session> sessions, Consumer<String> log, BiConsumer<Session, String> onChange) {
        if (!"ok".equals(Protocol.str(response, "status"))) {
            int n = sessions.values().stream().mapToInt(s -> s.samples.size()).sum();
            if (n != lastShortage) { log.accept("ML: chưa đủ dữ liệu (" + n + "/" + ML_MIN_SAMPLES + " mẫu)"); }
            lastShortage = n;
            return;
        }
        lastShortage = -1;
        for (JsonElement el : response.getAsJsonArray("results")) {
            JsonObject r = el.getAsJsonObject();
            Session s = sessions.get(Protocol.str(r, "studentId"));
            if (s == null) { continue; }
            double score = r.get("score").getAsDouble();
            boolean was = s.anomaly, now = r.get("anomaly").getAsBoolean();
            s.anomalyScore = score;
            s.anomaly = now;
            s.reasons = reasonsOf(r);
            if (now) { s.flaggedCount++; }
            if (now && !was) { log.accept("Bất thường: " + s.id + " (" + describe(score, s.reasons) + ")"); onChange.accept(s, "ANOMALY_ON"); }
            if (!now && was) { log.accept("Hết bất thường: " + s.id); onChange.accept(s, "ANOMALY_OFF"); }
            s.maxAnomaly = s.maxAnomaly == null ? score : Math.max(s.maxAnomaly, score);
        }
    }

    private static final String[] REASON_FORMATS = {
        "Tiến trình mới %s", "Rời cửa sổ %s lần", "Thời gian rời cửa sổ %s giây",
        "Địa chỉ mạng mới %s", "Vi phạm luật %s", "Số lần trả lời %s"};

    private static JsonArray reasonsOf(JsonObject result) {
        JsonElement reasons = result.get("reasons");
        if (reasons == null || !reasons.isJsonArray()) {
            return new JsonArray();
        }
        return reasons.getAsJsonArray();
    }

    /** "1.00 – Rời cửa sổ 6 lần (phòng 0.3), Số lần trả lời 8 (phòng 1.5)"; không có lý do thì chỉ còn điểm. */
    static String describe(double score, JsonArray reasons) {
        String text = String.format(Locale.ROOT, "%.2f", score);
        StringBuilder parts = new StringBuilder();
        for (JsonElement element : reasons) {
            JsonObject reason = element.getAsJsonObject();
            int feature = reason.get("feature").getAsInt();
            if (feature < 0 || feature >= REASON_FORMATS.length) {
                continue;
            }
            if (parts.length() > 0) {
                parts.append(", ");
            }
            parts.append(String.format(REASON_FORMATS[feature], number(reason.get("value").getAsDouble())));
            parts.append(" (phòng ").append(number(reason.get("room").getAsDouble())).append(")");
        }
        if (parts.length() == 0) {
            return text;
        }
        return text + " – " + parts;
    }

    // 6.0 thành "6", 0.33 thành "0.3"
    private static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
