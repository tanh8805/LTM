// Owner: Nguoi4

package exam.tools;

import exam.common.ml.ChronosCodec;
import exam.common.ml.Quantiles;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Gọi ml-service qua HTTP (đồng bộ). Thực nghiệm chạy offline nên timeout dài hơn Server (30 giây). */
public class HttpChronosService implements ChronosService {

    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String baseUrl;

    public HttpChronosService(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Map<String, Map<String, Quantiles>> forecast(Map<String, Map<String, List<Double>>> seriesByMachine) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/score"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(ChronosCodec.buildRequest(seriesByMachine)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("ml-service trả HTTP " + response.statusCode() + ": " + response.body());
            }
            return ChronosCodec.parseResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Bị ngắt khi chờ ml-service", e);
        }
    }
}
