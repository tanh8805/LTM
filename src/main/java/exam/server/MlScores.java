// Nối service ML với các Session: gom mẫu FEATURES gửi đi và áp điểm bất thường nhận về (Người 4).
package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
            if (now && !was) { log.accept("Bất thường: " + s.id + ". " + describe(score, s.reasons)); onChange.accept(s, "ANOMALY_ON"); }
            if (!now && was) { log.accept("Hết bất thường: " + s.id); onChange.accept(s, "ANOMALY_OFF"); }
            s.maxAnomaly = s.maxAnomaly == null ? score : Math.max(s.maxAnomaly, score);
        }
    }

    private static final String[] REASON_FORMATS = {
        "mở %s chương trình chạy nền mới (các máy khác thường %s)",
        "rời khỏi bài thi %s lần (các máy khác thường %s lần)",
        "rời khỏi bài thi tổng cộng %s giây (các máy khác thường %s giây)",
        "kết nối tới %s địa chỉ mạng mới (các máy khác thường %s)",
        "vi phạm luật %s lần (các máy khác thường %s lần)",
        "chọn hoặc đổi đáp án %s lần (các máy khác thường %s lần)"};

    private static final int WINDOW_SECONDS = 30;

    private static JsonArray reasonsOf(JsonObject result) {
        JsonElement reasons = result.get("reasons");
        if (reasons == null || !reasons.isJsonArray()) {
            return new JsonArray();
        }
        return reasons.getAsJsonArray();
    }

    /** Câu giải thích cho giám thị, dùng chung cho log, events.csv và giao diện. */
    static String describe(double score, JsonArray reasons) {
        String text = "Mức bất thường: " + level(score) + " (" + String.format(Locale.ROOT, "%.2f", score) + ").";

        List<String> parts = new ArrayList<>();
        int windows = 0;
        for (JsonElement element : reasons) {
            JsonObject reason = element.getAsJsonObject();
            int feature = reason.get("feature").getAsInt();
            if (feature < 0 || feature >= REASON_FORMATS.length) {
                continue;
            }
            windows = reason.get("windows").getAsInt();
            String mine = String.valueOf(Math.round(reason.get("total").getAsDouble()));
            String others = String.valueOf(Math.round(reason.get("room_total").getAsDouble()));
            parts.add(String.format(REASON_FORMATS[feature], mine, others));
        }
        if (parts.isEmpty()) {
            return text + " Không có hành vi nào lệch rõ rệt.";
        }
        return text + " Trong " + duration(windows * WINDOW_SECONDS) + " gần nhất, máy này: " + String.join(" · ", parts);
    }

    private static String level(double score) {
        if (score >= 0.9) {
            return "Rất cao";
        }
        if (score >= 0.7) {
            return "Cao";
        }
        return "Trung bình";
    }

    // 90 giây thành "1 phút 30 giây"
    private static String duration(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (minutes == 0) {
            return rest + " giây";
        }
        if (rest == 0) {
            return minutes + " phút";
        }
        return minutes + " phút " + rest + " giây";
    }
}
