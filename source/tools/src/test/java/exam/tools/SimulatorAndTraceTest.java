// Owner: Nguoi4

package exam.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import exam.common.model.Metrics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulatorAndTraceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void sameSeedGivesIdenticalTrace() {
        List<TraceRow> first = Simulator.generate(5, 60, 7L, 0.4);
        List<TraceRow> second = Simulator.generate(5, 60, 7L, 0.4);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(TraceCsv.toLine(first.get(i)), TraceCsv.toLine(second.get(i)));
        }
    }

    @Test
    void differentSeedsGiveDifferentTraces() {
        String first = TraceCsv.toLine(Simulator.generate(3, 30, 1L, 0.3).get(0));
        String second = TraceCsv.toLine(Simulator.generate(3, 30, 2L, 0.3).get(0));

        assertTrue(!first.equals(second));
    }

    @Test
    void traceHasTheRequestedShape() {
        List<TraceRow> rows = Simulator.generate(10, 120, 42L, 0.3);

        assertEquals(1200, rows.size());
        Set<String> machines = new HashSet<>();
        for (TraceRow row : rows) {
            machines.add(row.machineId);
        }
        assertEquals(10, machines.size());
    }

    @Test
    void firstFortyPercentOfEveryMachineIsAlwaysNormal() {
        List<TraceRow> rows = Simulator.generate(10, 100, 3L, 1.0); // mọi máy đều gian lận
        long firstTime = rows.get(0).time;

        for (TraceRow row : rows) {
            long sampleIndex = (row.time - firstTime) / 10_000;
            if (sampleIndex < 40) {
                assertEquals(0, row.label, "mẫu " + sampleIndex + " phải là bình thường");
            }
        }
    }

    @Test
    void anomalyFractionControlsNumberOfCheatingMachines() {
        List<TraceRow> rows = Simulator.generate(10, 100, 5L, 0.3);

        Set<String> cheaters = new HashSet<>();
        for (TraceRow row : rows) {
            if (row.label == 1) {
                cheaters.add(row.machineId);
            }
        }
        assertEquals(3, cheaters.size());
    }

    @Test
    void labeledRowsCarryAScenarioNameAndNormalRowsDoNot() {
        for (TraceRow row : Simulator.generate(10, 100, 9L, 0.5)) {
            if (row.label == 1) {
                assertTrue(!row.scenario.equals("normal"));
            } else {
                assertEquals("normal", row.scenario);
            }
        }
    }

    @Test
    void generatedValuesAreInValidRanges() {
        for (TraceRow row : Simulator.generate(10, 100, 11L, 0.5)) {
            Metrics m = row.metrics;
            assertTrue(m.kbSent >= 0 && m.kbReceived >= 0);
            assertTrue(m.cpuPercent >= 0 && m.cpuPercent <= 100);
            assertTrue(m.ramPercent >= 0 && m.ramPercent <= 100);
            assertTrue(m.distinctDestinationCount >= 0 && m.connectionCount >= 0);
        }
    }

    @Test
    void anomalousRowsLookDifferentFromNormalOnes() {
        List<TraceRow> rows = Simulator.generate(10, 100, 42L, 0.3);

        double normalKbSent = 0, anomalousMax = 0;
        int normalCount = 0;
        for (TraceRow row : rows) {
            if (row.label == 0) {
                normalKbSent += row.metrics.kbSent;
                normalCount++;
            }
        }
        assertTrue(normalCount > 0);
        assertTrue(normalKbSent / normalCount < 100, "KB gửi bình thường thấp");
        for (TraceRow row : rows) {
            if (row.label == 1) {
                anomalousMax = Math.max(anomalousMax, Math.max(row.metrics.kbSent, row.metrics.distinctDestinationCount * 10));
            }
        }
        assertTrue(anomalousMax > 100);
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Simulator.generate(0, 100, 1L, 0.3));
        assertThrows(IllegalArgumentException.class, () -> Simulator.generate(5, 3, 1L, 0.3));
    }

    @Test
    void csvRoundTripKeepsEverything() throws IOException {
        List<TraceRow> rows = Simulator.generate(4, 50, 21L, 0.5);
        Path file = tempDirectory.resolve("nested/dir/trace.csv");

        TraceCsv.write(file, rows);
        List<TraceRow> loaded = TraceCsv.read(file);

        assertEquals(rows.size(), loaded.size());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(TraceCsv.toLine(rows.get(i)), TraceCsv.toLine(loaded.get(i)));
        }
        assertTrue(Files.readAllLines(file).get(0).startsWith("machineId,time,label,scenario,kbSent"));
    }

    @Test
    void readingMalformedTraceReportsTheLineNumber() throws IOException {
        Path file = tempDirectory.resolve("bad.csv");
        Files.writeString(file, TraceCsv.HEADER + "\nSV001,1,0,normal,1,2,3,4,5,6,7,8\nSV001,hong\n");

        IOException error = assertThrows(IOException.class, () -> TraceCsv.read(file));

        assertTrue(error.getMessage().contains("dòng 3"));
    }

    @Test
    void recorderConvertsServerBaselineCsvKeepingOnlyDetailRows() {
        String serverCsv = "machineId,time,kind,rateMode,kbSent,kbReceived,connectionCount,distinctDestinationCount,"
                + "cpuPercent,ramPercent,processCount,focusLostCount\n"
                + "SV001,1000,SUMMARY,NORMAL,1.0,2.0,3,4,5.0,6.0,7,0\n"
                + "SV001,2000,DETAIL,BASELINE,1.5,2.5,3,4,5.5,6.5,7,1\n"
                + "SV002,2000,DETAIL,BASELINE,9.0,8.0,1,1,1.0,2.0,3,0\n";

        List<TraceRow> rows = Recorder.convertServerCsv(serverCsv, "BASELINE");

        assertEquals(2, rows.size());
        assertEquals("SV001", rows.get(0).machineId);
        assertEquals(1.5, rows.get(0).metrics.kbSent);
        assertEquals(1, rows.get(0).metrics.focusLostCount);
        assertEquals("baseline", rows.get(0).scenario);
        assertEquals(0, rows.get(0).label);
    }

    @Test
    void argsParsesOptionsAndRejectsGarbage() {
        Args args = new Args(new String[] {"--machines", "5", "--flag", "--seed", "9"});

        assertEquals(5, args.getInt("machines", 1));
        assertEquals(9L, args.getLong("seed", 0));
        assertTrue(args.has("flag"));
        assertEquals(77, args.getInt("missing", 77));
        assertThrows(IllegalArgumentException.class, () -> args.require("nothing"));
        assertThrows(IllegalArgumentException.class, () -> new Args(new String[] {"oops"}));
        assertThrows(IllegalArgumentException.class, () -> new Args(new String[] {"--machines", "abc"}).getInt("machines", 1));
    }
}
