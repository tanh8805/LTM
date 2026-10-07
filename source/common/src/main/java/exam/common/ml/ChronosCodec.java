// Owner: Nguoi4

package exam.common.ml;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Định dạng JSON giữa Server (Java) và ml-service (Python). Một request chứa MỌI máy (batch).
 *
 * Request  (POST /score):
 *   {"predictionLength":1,
 *    "machines":[{"machineId":"SV001","series":{"kbSent":[1.0,2.0,...],"cpuPercent":[...], ...}}, ...]}
 *
 * Response:
 *   {"model":"amazon/chronos-bolt-tiny",
 *    "results":[{"machineId":"SV001","forecast":{"kbSent":{"q10":0.5,"q50":1.0,"q90":2.0}, ...}}, ...]}
 *
 * Lớp này dùng chung cho Server (MlGatewayImpl) và tools (ExperimentRunner) nên hai nơi luôn hiểu giống nhau.
 */
public class ChronosCodec {

    private static final Gson GSON = new Gson();

    private ChronosCodec() {
    }

    /** machineId -> (tên metric -> chuỗi giá trị, cũ nhất ở đầu). */
    public static String buildRequest(Map<String, Map<String, List<Double>>> seriesByMachine) {
        JsonArray machines = new JsonArray();
        for (Map.Entry<String, Map<String, List<Double>>> machine : seriesByMachine.entrySet()) {
            JsonObject series = new JsonObject();
            for (Map.Entry<String, List<Double>> metric : machine.getValue().entrySet()) {
                series.add(metric.getKey(), GSON.toJsonTree(metric.getValue()));
            }
            JsonObject machineJson = new JsonObject();
            machineJson.addProperty("machineId", machine.getKey());
            machineJson.add("series", series);
            machines.add(machineJson);
        }

        JsonObject request = new JsonObject();
        request.addProperty("predictionLength", 1);
        request.add("machines", machines);
        return GSON.toJson(request);
    }

    /**
     * Đọc response của ml-service.
     * Ném IllegalArgumentException nếu JSON hỏng hoặc thiếu "results", để người gọi bỏ qua ML.
     */
    public static Map<String, Map<String, Quantiles>> parseResponse(String json) {
        Map<String, Map<String, Quantiles>> forecastByMachine = new LinkedHashMap<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("results") || !root.get("results").isJsonArray()) {
                throw new IllegalArgumentException("Response thiếu mảng \"results\"");
            }
            for (JsonElement resultElement : root.getAsJsonArray("results")) {
                JsonObject result = resultElement.getAsJsonObject();
                String machineId = result.get("machineId").getAsString();

                Map<String, Quantiles> forecast = new LinkedHashMap<>();
                JsonObject forecastJson = result.getAsJsonObject("forecast");
                for (Map.Entry<String, JsonElement> metric : forecastJson.entrySet()) {
                    forecast.put(metric.getKey(), GSON.fromJson(metric.getValue(), Quantiles.class));
                }
                forecastByMachine.put(machineId, forecast);
            }
        } catch (JsonParseException | IllegalStateException | NullPointerException | ClassCastException e) {
            throw new IllegalArgumentException("Response của ml-service sai định dạng: " + e.getMessage(), e);
        }
        return forecastByMachine;
    }
}
