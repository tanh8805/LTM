// Nối service ML với các Session: gom mẫu FEATURES gửi đi và áp điểm bất thường nhận về (Người 4).
package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

final class MlScores {
    private MlScores() {}

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

    /** Ghi điểm vào Session; status khác "ok" (ví dụ not_enough_data) thì không cập nhật gì. */
    static void apply(JsonObject response, Map<String, Session> sessions, Consumer<String> log) {
        if (!"ok".equals(Protocol.str(response, "status"))) { return; }
        for (JsonElement el : response.getAsJsonArray("results")) {
            JsonObject r = el.getAsJsonObject();
            Session s = sessions.get(Protocol.str(r, "studentId"));
            if (s == null) { continue; }
            double score = r.get("score").getAsDouble();
            s.anomalyScore = score;
            s.anomaly = r.get("anomaly").getAsBoolean();
            s.maxAnomaly = s.maxAnomaly == null ? score : Math.max(s.maxAnomaly, score);
        }
    }
}
