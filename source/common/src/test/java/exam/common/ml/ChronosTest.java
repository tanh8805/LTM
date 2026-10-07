// Owner: Nguoi4

package exam.common.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import exam.common.model.Metrics;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Test ChronosCodec (build request, parse response) và ChronosJudge (3 lần liên tiếp ngoài q0.1-q0.9). */
class ChronosTest {

    private Map<String, Quantiles> forecastForAllMetrics(double q10, double q50, double q90) {
        Map<String, Quantiles> forecast = new HashMap<>();
        for (String name : Metrics.VECTOR_NAMES) {
            forecast.put(name, new Quantiles(q10, q50, q90));
        }
        return forecast;
    }

    private Map<String, Double> actualForAllMetrics(double value) {
        Map<String, Double> actual = new HashMap<>();
        for (String name : Metrics.VECTOR_NAMES) {
            actual.put(name, value);
        }
        return actual;
    }

    // ----- codec -----

    @Test
    void buildRequestPutsAllMachinesInOneBatch() {
        Map<String, Map<String, List<Double>>> seriesByMachine = new LinkedHashMap<>();
        for (String machineId : new String[] {"SV001", "SV002", "SV003"}) {
            Map<String, List<Double>> series = new LinkedHashMap<>();
            series.put("kbSent", List.of(1.0, 2.0, 3.0));
            series.put("cpuPercent", List.of(10.0, 11.0, 12.0));
            seriesByMachine.put(machineId, series);
        }

        JsonObject request = JsonParser.parseString(ChronosCodec.buildRequest(seriesByMachine)).getAsJsonObject();

        assertEquals(1, request.get("predictionLength").getAsInt());
        JsonArray machines = request.getAsJsonArray("machines");
        assertEquals(3, machines.size());
        JsonObject first = machines.get(0).getAsJsonObject();
        assertEquals("SV001", first.get("machineId").getAsString());
        assertEquals(3, first.getAsJsonObject("series").getAsJsonArray("kbSent").size());
    }

    @Test
    void parseResponseReadsQuantilesPerMachineAndMetric() {
        String json = "{\"model\":\"amazon/chronos-bolt-tiny\",\"results\":["
                + "{\"machineId\":\"SV001\",\"forecast\":{\"kbSent\":{\"q10\":1.0,\"q50\":2.0,\"q90\":3.5}}},"
                + "{\"machineId\":\"SV002\",\"forecast\":{\"cpuPercent\":{\"q10\":5,\"q50\":6,\"q90\":7}}}]}";

        Map<String, Map<String, Quantiles>> parsed = ChronosCodec.parseResponse(json);

        assertEquals(2, parsed.size());
        assertEquals(3.5, parsed.get("SV001").get("kbSent").q90);
        assertEquals(6.0, parsed.get("SV002").get("cpuPercent").q50);
    }

    @Test
    void parseResponseRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> ChronosCodec.parseResponse("not json"));
        assertThrows(IllegalArgumentException.class, () -> ChronosCodec.parseResponse("{\"error\":\"model_unavailable\"}"));
        assertThrows(IllegalArgumentException.class, () -> ChronosCodec.parseResponse("{\"results\":[{\"machineId\":\"SV1\"}]}"));
    }

    // ----- judge -----

    @Test
    void valueInsideRangeIsNeverSuspicious() {
        ChronosJudge judge = new ChronosJudge(3);
        for (int i = 0; i < 10; i++) {
            MlResult result = judge.judge("SV001", actualForAllMetrics(5), forecastForAllMetrics(1, 5, 9));
            assertFalse(result.suspicious);
            assertEquals(0, result.consecutiveOutOfRange);
        }
    }

    @Test
    void suspiciousOnlyAfterThreeConsecutiveOutOfRange() {
        ChronosJudge judge = new ChronosJudge(3);
        Map<String, Quantiles> forecast = forecastForAllMetrics(1, 5, 9);
        Map<String, Double> tooHigh = actualForAllMetrics(50);

        MlResult first = judge.judge("SV001", tooHigh, forecast);
        MlResult second = judge.judge("SV001", tooHigh, forecast);
        MlResult third = judge.judge("SV001", tooHigh, forecast);

        assertFalse(first.suspicious);
        assertFalse(second.suspicious);
        assertTrue(third.suspicious);
        assertEquals(3, third.consecutiveOutOfRange);
        assertTrue(third.reason.contains("3 lần liên tiếp"));
    }

    @Test
    void belowQ10CountsAsOutOfRangeToo() {
        ChronosJudge judge = new ChronosJudge(3);
        Map<String, Quantiles> forecast = forecastForAllMetrics(10, 20, 30);
        Map<String, Double> tooLow = actualForAllMetrics(1);

        judge.judge("SV001", tooLow, forecast);
        judge.judge("SV001", tooLow, forecast);
        assertTrue(judge.judge("SV001", tooLow, forecast).suspicious);
    }

    @Test
    void oneNormalPointResetsTheCount() {
        ChronosJudge judge = new ChronosJudge(3);
        Map<String, Quantiles> forecast = forecastForAllMetrics(1, 5, 9);

        judge.judge("SV001", actualForAllMetrics(50), forecast);
        judge.judge("SV001", actualForAllMetrics(50), forecast);
        judge.judge("SV001", actualForAllMetrics(5), forecast); // quay về bình thường
        MlResult afterReset = judge.judge("SV001", actualForAllMetrics(50), forecast);

        assertFalse(afterReset.suspicious);
        assertEquals(1, afterReset.consecutiveOutOfRange);
    }

    @Test
    void machinesAreCountedIndependently() {
        ChronosJudge judge = new ChronosJudge(3);
        Map<String, Quantiles> forecast = forecastForAllMetrics(1, 5, 9);

        judge.judge("SV001", actualForAllMetrics(50), forecast);
        judge.judge("SV001", actualForAllMetrics(50), forecast);
        MlResult other = judge.judge("SV002", actualForAllMetrics(50), forecast);

        assertEquals(1, other.consecutiveOutOfRange);
    }

    @Test
    void processCountAndFocusLossAreNotJudged() {
        ChronosJudge judge = new ChronosJudge(1);
        Map<String, Quantiles> forecast = forecastForAllMetrics(1, 5, 9);
        Map<String, Double> actual = actualForAllMetrics(5);
        // hai chiều cuối vượt xa khoảng dự báo nhưng Chronos không được xét chúng
        actual.put("processCount", 999.0);
        actual.put("focusLostCount", 999.0);

        assertFalse(judge.judge("SV001", actual, forecast).suspicious);
    }

    @Test
    void missingForecastForAMetricIsIgnored() {
        ChronosJudge judge = new ChronosJudge(1);
        Map<String, Quantiles> forecast = new HashMap<>();
        forecast.put("kbSent", new Quantiles(1, 5, 9));

        assertFalse(judge.judge("SV001", actualForAllMetrics(5), forecast).suspicious);
    }

    // ----- ml.mode -----

    @Test
    void modesCombineIsolationForestAndChronosCorrectly() {
        boolean[] values = {false, true};
        for (boolean forest : values) {
            for (boolean chronos : values) {
                assertFalse(MlDecision.isSuspicious(exam.common.model.MlMode.NONE, forest, chronos));
                assertEquals(forest, MlDecision.isSuspicious(exam.common.model.MlMode.IF, forest, chronos));
                assertEquals(chronos, MlDecision.isSuspicious(exam.common.model.MlMode.CHRONOS, forest, chronos));
                assertEquals(forest || chronos, MlDecision.isSuspicious(exam.common.model.MlMode.BOTH_OR, forest, chronos));
                assertEquals(forest && chronos, MlDecision.isSuspicious(exam.common.model.MlMode.BOTH_AND, forest, chronos));
            }
        }
    }

    @Test
    void modeTellsWhichModelsMustRun() {
        assertFalse(MlDecision.usesIsolationForest(exam.common.model.MlMode.NONE));
        assertFalse(MlDecision.usesChronos(exam.common.model.MlMode.NONE));
        assertTrue(MlDecision.usesIsolationForest(exam.common.model.MlMode.IF));
        assertFalse(MlDecision.usesChronos(exam.common.model.MlMode.IF));
        assertFalse(MlDecision.usesIsolationForest(exam.common.model.MlMode.CHRONOS));
        assertTrue(MlDecision.usesChronos(exam.common.model.MlMode.CHRONOS));
        assertTrue(MlDecision.usesIsolationForest(exam.common.model.MlMode.BOTH_AND));
        assertTrue(MlDecision.usesChronos(exam.common.model.MlMode.BOTH_OR));
    }
}
