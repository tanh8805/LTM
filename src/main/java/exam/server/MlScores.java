// Nối service ML với các Session: gom mẫu FEATURES gửi đi và áp điểm bất thường nhận về (Người 4).
package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
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

    /** Ghi điểm vào Session và log khi một máy bắt đầu/hết bị cờ; chưa đủ mẫu thì chỉ log (khi số mẫu đổi). */
    void apply(JsonObject response, Map<String, Session> sessions, Consumer<String> log) {
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
            if (now) { s.flaggedCount++; }
            if (now && !was) { log.accept(String.format(Locale.ROOT, "Bất thường: %s (%.2f)", s.id, score)); }
            if (!now && was) { log.accept("Hết bất thường: " + s.id); }
            s.maxAnomaly = s.maxAnomaly == null ? score : Math.max(s.maxAnomaly, score);
        }
    }
}
