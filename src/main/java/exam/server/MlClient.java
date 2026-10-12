// Gọi service ML (Python) qua HTTP: gửi các mẫu FEATURES của cả phòng, nhận điểm bất thường (Người 4).
package exam.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exam.common.Protocol;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class MlClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String baseUrl;

    /** baseUrl ví dụ "http://localhost:8000". */
    public MlClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Mã trạng thái khác 200 thì ném IOException để server ghi log và giữ điểm cũ. */
    public JsonObject score(JsonArray samples) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.add("samples", samples);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/score"))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Protocol.GSON.toJson(body), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Service ML trả mã " + response.statusCode());
        }
        try {
            return Protocol.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new IOException("Service ML trả dữ liệu không phải JSON object", e);
        }
    }
}
