// Owner: Nguoi4

package exam.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import exam.common.model.MlMode;
import exam.common.model.Metrics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Trace v2 (không nhãn trong CSV) + ground truth ở file events.json riêng. */
class TraceEventsTest {

    @TempDir
    Path tempDirectory;

    private TraceEvents.Event event(String id, String type, String category, long start, long end) {
        TraceEvents.Event event = new TraceEvents.Event();
        event.id = id;
        event.type = type;
        event.category = category;
        event.startTime = start;
        event.endTime = end;
        return event;
    }

    @Test
    void sampleIsAssignedToTheEventContainingTheMiddleOfItsWindow() {
        TraceEvents events = new TraceEvents();
        events.events.add(event("e1", "CPU_LOAD", "anomaly", 100_000, 120_000));

        // mẫu tại 110s đo khoảng (100s,110s], điểm giữa = 105s: nằm trong sự kiện
        assertEquals("e1", events.eventAt(110_000, 10_000).id);
        // mẫu tại 104s: điểm giữa 99s, nằm ngoài
        assertNull(events.eventAt(104_000, 10_000));
        // mẫu tại 125s: điểm giữa 120s, đúng mép phải (nửa mở) nên ngoài
        assertNull(events.eventAt(125_000, 10_000));
        // mẫu tại 124.9s: điểm giữa 119.9s, trong
        assertEquals("e1", events.eventAt(124_900, 10_000).id);
    }

    @Test
    void anomalyWinsWhenEventsOverlap() {
        TraceEvents events = new TraceEvents();
        events.events.add(event("benign", "APPLICATION_ACTIVITY", "benign", 0, 100_000));
        events.events.add(event("cpu", "CPU_LOAD", "anomaly", 40_000, 60_000));

        assertEquals("cpu", events.eventAt(55_000, 10_000).id);
        assertEquals("benign", events.eventAt(25_000, 10_000).id);
    }

    @Test
    void eventsFileRoundTripsAndPathsFollowTheTraceName() throws IOException {
        TraceEvents events = new TraceEvents();
        events.sessionId = "cpu_01";
        TraceEvents.Event e = event("cpu-1", "CPU_LOAD", "anomaly", 1, 2);
        e.params.put("cores", 2);
        events.events.add(e);
        Path file = tempDirectory.resolve("cpu_01.events.json");

        events.write(file);
        TraceEvents loaded = TraceEvents.read(file);

        assertEquals("cpu_01", loaded.sessionId);
        assertEquals("CPU_LOAD", loaded.events.get(0).type);
        assertEquals(2.0, ((Number) loaded.events.get(0).params.get("cores")).doubleValue());
        assertEquals("cpu_01.events.json", TraceEvents.pathFor(Path.of("data/traces/cpu_01.csv")).getFileName().toString());
        assertEquals("cpu_01.meta.json", TraceEvents.metaPathFor(Path.of("data/traces/cpu_01.csv")).getFileName().toString());
    }

    private Path writeV2Trace(String name, int samples) throws IOException {
        StringBuilder csv = new StringBuilder(TraceCsv.HEADER_V2).append('\n');
        for (int i = 1; i <= samples; i++) {
            csv.append(TraceCsv.toLineV2(name, "LOCAL", 1_000_000L + i * 10_000L, i, new Metrics(i, i, 3, 2, 5, 40, 80, 0))).append('\n');
        }
        Path file = tempDirectory.resolve(name + ".csv");
        Files.writeString(file, csv.toString());
        return file;
    }

    @Test
    void v2TraceHasNoLabelColumnAndLabelsComeOnlyFromTheEventsFile() throws IOException {
        Path file = writeV2Trace("t1", 10);
        assertFalse(Files.readString(file).toLowerCase().contains("label"), "CSV số liệu không được chứa nhãn");

        // Không có events.json: mọi dòng bình thường
        for (TraceRow row : TraceCsv.read(file)) {
            assertEquals(0, row.label);
            assertEquals("normal", row.category);
            assertEquals("t1", row.machineId);
        }

        // Có events.json: sample i đo khoảng (1_000_000+10000*(i-1), 1_000_000+10000*i]; sự kiện phủ i = 4..6
        TraceEvents events = new TraceEvents();
        events.sessionId = "t1";
        events.events.add(event("e", "NETWORK_LOAD", "anomaly", 1_030_000L, 1_060_000L));
        events.write(TraceEvents.pathFor(file));

        List<TraceRow> rows = TraceCsv.read(file);
        StringBuilder labels = new StringBuilder();
        for (TraceRow row : rows) {
            labels.append(row.label);
        }
        // mẫu 1..10 nằm ở chỉ số 0..9; sự kiện phủ mẫu 4,5,6 (chỉ số 3,4,5)
        assertEquals("0001110000", labels.toString());
        assertEquals("NETWORK_LOAD", rows.get(4).scenario);
        assertEquals(1, rows.get(4).label);
        assertEquals(0, rows.get(8).label);
    }

    @Test
    void benignEventsAreNotLabelledAsAnomalies() throws IOException {
        Path file = writeV2Trace("t2", 10);
        TraceEvents events = new TraceEvents();
        events.events.add(event("app", "APPLICATION_ACTIVITY", "benign", 1_000_000L, 1_100_000L));
        events.write(TraceEvents.pathFor(file));

        for (TraceRow row : TraceCsv.read(file)) {
            assertEquals(0, row.label);
            assertEquals("benign", row.category);
        }
    }

    @Test
    void malformedV2LineReportsLineNumber() throws IOException {
        Path file = tempDirectory.resolve("bad.csv");
        Files.writeString(file, TraceCsv.HEADER_V2 + "\nS,L,1,1,1,2,3,4,5,6,7,8\nS,L,oops\n");

        IOException error = assertThrows(IOException.class, () -> TraceCsv.read(file));
        assertTrue(error.getMessage().contains("dòng 3"), error.getMessage());
    }

    @Test
    void tracesOfDifferentLengthAreEvaluatedIndependentlyAndCategoriesAreCounted() throws IOException {
        // Hai trace độc lập: dài 50 và 80 mẫu; trace 2 có đoạn benign và đoạn anomaly.
        Path first = writeV2Trace("short", 50);
        Path second = writeV2Trace("long", 80);
        TraceEvents events = new TraceEvents();
        events.events.add(event("app", "APPLICATION_ACTIVITY", "benign", 1_000_000L + 40 * 10_000L, 1_000_000L + 50 * 10_000L));
        events.events.add(event("cpu", "CPU_LOAD", "anomaly", 1_000_000L + 60 * 10_000L, 1_000_000L + 70 * 10_000L));
        events.write(TraceEvents.pathFor(second));

        List<TraceRow> rows = new ArrayList<>(TraceCsv.read(first));
        rows.addAll(TraceCsv.read(second));
        ExperimentEngine engine = new ExperimentEngine();
        List<ModeResult> results = engine.run(rows, List.of(MlMode.NONE, MlMode.IF), null, new ExperimentEngine.Config());

        ModeResult none = results.get(0);
        assertEquals(0, none.truePositives + none.falsePositives);
        // đánh giá từ mẫu 30: trace ngắn 20 mẫu, trace dài 50 mẫu = 70; trong đó 10 benign, 10 anomaly
        assertEquals(70, none.truePositives + none.falsePositives + none.falseNegatives + none.trueNegatives);
        assertEquals(10, none.benignSamples);
        assertEquals(10, none.byScenario.get("CPU_LOAD").samples);
        assertEquals(1, none.byScenario.get("CPU_LOAD").windows);
        assertEquals(50, none.normalSamples);
        assertEquals(50 + 80, engine.getDetails().size());
    }
}
