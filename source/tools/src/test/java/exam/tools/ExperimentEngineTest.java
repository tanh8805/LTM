// Owner: Nguoi4

package exam.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import exam.common.model.Metrics;
import exam.common.model.MlMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExperimentEngineTest {

    @TempDir
    Path tempDirectory;

    private final List<MlMode> allModes = List.of(MlMode.NONE, MlMode.IF, MlMode.CHRONOS, MlMode.BOTH_OR, MlMode.BOTH_AND);

    private ModeResult find(List<ModeResult> results, MlMode mode) {
        for (ModeResult result : results) {
            if (result.mode == mode) {
                return result;
            }
        }
        throw new AssertionError("không có kết quả cho " + mode);
    }

    // ------------------------------------------------------------------
    // Công thức đánh giá (kiểm tra bằng tay với dãy nhỏ)
    // ------------------------------------------------------------------

    private List<TraceRow> labeledRows(int... labels) {
        List<TraceRow> rows = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            rows.add(new TraceRow("SV001", i * 10_000L, labels[i], labels[i] == 1 ? "x" : "normal", new Metrics()));
        }
        return rows;
    }

    @Test
    void confusionMatrixAndDerivedMetricsAreComputedCorrectly() {
        // t:      0  1  2  3  4  5  6  7  8  9      (trainSamples = 2: chỉ xét t >= 2)
        // nhãn:   0  0  0  1  1  1  0  0  1  0
        // cờ:     1  1  0  0  1  1  1  0  1  0
        List<TraceRow> rows = labeledRows(0, 0, 0, 1, 1, 1, 0, 0, 1, 0);
        boolean[] flags = {true, true, false, false, true, true, true, false, true, false};

        ModeResult result = ExperimentEngine.evaluateForTest(MlMode.IF, rows, flags, 2);

        assertEquals(3, result.truePositives);   // t=4,5,8
        assertEquals(1, result.falsePositives);  // t=6
        assertEquals(1, result.falseNegatives);  // t=3
        assertEquals(3, result.trueNegatives);   // t=2,7,9
        assertEquals(0.75, result.precision(), 1e-9);
        assertEquals(0.75, result.recall(), 1e-9);
        assertEquals(0.75, result.f1(), 1e-9);
        assertEquals(0.25, result.falsePositiveRate(), 1e-9);
    }

    @Test
    void windowDetectionAndDelayAreComputedPerAnomalyWindow() {
        // hai đoạn gian lận: [3..5] và [8..9]; cờ báo ở t=5 (trễ 2 mẫu) và không báo đoạn thứ hai
        List<TraceRow> rows = labeledRows(0, 0, 0, 1, 1, 1, 0, 0, 1, 1);
        boolean[] flags = {false, false, false, false, false, true, false, false, false, false};

        ModeResult result = ExperimentEngine.evaluateForTest(MlMode.IF, rows, flags, 2);

        assertEquals(2, result.totalWindows);
        assertEquals(1, result.detectedWindows);
        assertEquals(2.0, result.averageDelaySamples(), 1e-9);
    }

    @Test
    void trainingPeriodIsNotEvaluated() {
        List<TraceRow> rows = labeledRows(1, 1, 0, 0);
        boolean[] flags = {true, true, false, false};

        ModeResult result = ExperimentEngine.evaluateForTest(MlMode.IF, rows, flags, 2);

        assertEquals(0, result.truePositives);
        assertEquals(0, result.totalWindows);
    }

    @Test
    void metricsAreZeroInsteadOfNaNWhenThereIsNothingToDivide() {
        ModeResult empty = new ModeResult(MlMode.NONE);

        assertEquals(0.0, empty.precision());
        assertEquals(0.0, empty.recall());
        assertEquals(0.0, empty.f1());
        assertEquals(0.0, empty.falsePositiveRate());
        assertTrue(Double.isNaN(empty.averageDelaySamples()));
    }

    // ------------------------------------------------------------------
    // Chạy cả hệ thống trên trace mô phỏng
    // ------------------------------------------------------------------

    @Test
    void allFiveModesRunOnSimulatedTraceWithSensibleResults() {
        List<TraceRow> rows = Simulator.generate(10, 120, 42L, 0.3);
        NaiveChronosService chronos = new NaiveChronosService();

        List<ModeResult> results = new ExperimentEngine().run(rows, allModes, chronos, new ExperimentEngine.Config());

        assertEquals(5, results.size());
        ModeResult none = find(results, MlMode.NONE);
        ModeResult forest = find(results, MlMode.IF);
        ModeResult chronosOnly = find(results, MlMode.CHRONOS);
        ModeResult either = find(results, MlMode.BOTH_OR);
        ModeResult both = find(results, MlMode.BOTH_AND);

        // NONE không phát hiện gì
        assertEquals(0, none.truePositives + none.falsePositives);
        assertEquals(0.0, none.recall());

        // Isolation Forest với luật threshold max(train)+0.05 rất bảo thủ: gần như không báo nhầm,
        // nhưng bỏ sót nhiều (đây là kết quả thực nghiệm, ghi vào báo cáo). Test chỉ kiểm tra hai tính chất ổn định đó.
        assertTrue(forest.falsePositiveRate() < 0.05, "IF fpr " + forest.falsePositiveRate());
        assertTrue(forest.detectedWindows >= 1, "IF phải phát hiện ít nhất một đoạn gian lận");
        // Chronos (bản giả naive: trung vị + MAD) nhạy hơn nhiều trên các đột biến rõ rệt
        assertTrue(chronosOnly.recall() > 0.4, "Chronos(naive) recall " + chronosOnly.recall());
        assertTrue(chronosOnly.recall() > forest.recall());
        // Ngược lại luật "ngoài q0.1-q0.9 trên bất kỳ metric nào, 3 lần liên tiếp" báo nhầm nhiều hơn IF (khoảng 20-25%
        // với dự báo naive trên nhiễu tự tương quan): đánh đổi recall/FPR này là nội dung của phần Results trong báo cáo.
        assertTrue(chronosOnly.falsePositiveRate() > forest.falsePositiveRate());

        // Quan hệ luôn đúng giữa các chế độ ghép: OR báo nhiều hơn AND
        assertTrue(either.truePositives >= forest.truePositives);
        assertTrue(either.truePositives >= chronosOnly.truePositives);
        assertTrue(both.truePositives <= forest.truePositives);
        assertTrue(both.truePositives <= chronosOnly.truePositives);
        assertTrue(either.falsePositives >= both.falsePositives);
    }

    @Test
    void chronosGetsOneBatchWithEveryMachinePerStep() {
        List<TraceRow> rows = Simulator.generate(6, 50, 4L, 0.3);
        NaiveChronosService chronos = new NaiveChronosService();
        ExperimentEngine.Config config = new ExperimentEngine.Config();
        config.trainSamples = 20;
        config.chronosMinPoints = 12;

        new ExperimentEngine().run(rows, List.of(MlMode.CHRONOS), chronos, config);

        assertEquals(50 - 12, chronos.calls, "mỗi bước một lần gọi cho cả phòng");
        assertEquals(6, chronos.lastBatchMachineCount);
    }

    @Test
    void isolationForestOnlyModesNeverCallChronos() {
        List<TraceRow> rows = Simulator.generate(5, 60, 8L, 0.3);
        NaiveChronosService chronos = new NaiveChronosService();

        new ExperimentEngine().run(rows, List.of(MlMode.NONE, MlMode.IF), chronos, new ExperimentEngine.Config());

        assertEquals(0, chronos.calls);
    }

    @Test
    void unavailableChronosSkipsOnlyTheModesThatNeedIt() {
        List<TraceRow> rows = Simulator.generate(5, 60, 8L, 0.3);
        ChronosService broken = series -> {
            throw new IOException("Connection refused");
        };

        List<ModeResult> results = new ExperimentEngine().run(rows, allModes, broken, new ExperimentEngine.Config());

        assertEquals(null, find(results, MlMode.NONE).skippedReason);
        assertEquals(null, find(results, MlMode.IF).skippedReason);
        assertNotNull(find(results, MlMode.CHRONOS).skippedReason);
        assertTrue(find(results, MlMode.CHRONOS).skippedReason.contains("Connection refused"));
        assertNotNull(find(results, MlMode.BOTH_OR).skippedReason);
        assertNotNull(find(results, MlMode.BOTH_AND).skippedReason);
    }

    @Test
    void traceShorterThanTrainingPeriodIsRejected() {
        List<TraceRow> rows = Simulator.generate(3, 20, 1L, 0.3);

        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentEngine().run(rows, List.of(MlMode.IF), null, new ExperimentEngine.Config()));
    }

    @Test
    void runIsRepeatableWithTheSameTrace() {
        List<TraceRow> rows = Simulator.generate(8, 100, 13L, 0.3);

        List<ModeResult> first = new ExperimentEngine().run(rows, allModes, new NaiveChronosService(), new ExperimentEngine.Config());
        List<ModeResult> second = new ExperimentEngine().run(rows, allModes, new NaiveChronosService(), new ExperimentEngine.Config());

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).truePositives, second.get(i).truePositives);
            assertEquals(first.get(i).falsePositives, second.get(i).falsePositives);
            assertEquals(first.get(i).detectedWindows, second.get(i).detectedWindows);
        }
    }

    // ------------------------------------------------------------------
    // Đầu ra của ExperimentRunner
    // ------------------------------------------------------------------

    @Test
    void parseModesAcceptsAllModeNamesAndRejectsUnknown() {
        assertEquals(List.of(MlMode.NONE, MlMode.BOTH_AND), ExperimentRunner.parseModes("none, both_and"));
        assertThrows(IllegalArgumentException.class, () -> ExperimentRunner.parseModes("IF,MAGIC"));
    }

    @Test
    void resultsAreWrittenAsCsvAndTableMentionsSkippedModes() throws IOException {
        ModeResult measured = new ModeResult(MlMode.IF);
        measured.truePositives = 8;
        measured.falsePositives = 2;
        measured.falseNegatives = 2;
        measured.trueNegatives = 88;
        measured.totalWindows = 3;
        measured.detectedWindows = 3;
        measured.totalDelaySamples = 6;
        ModeResult skipped = new ModeResult(MlMode.CHRONOS);
        skipped.skippedReason = "ml-service không dùng được: boom, boom";

        Path out = tempDirectory.resolve("results/experiment.csv");
        ExperimentRunner.writeCsv(out, List.of(measured, skipped));
        String table = ExperimentRunner.formatTable(List.of(measured, skipped));

        List<String> lines = Files.readAllLines(out);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).startsWith("mode,precision,recall,f1,fpr"));
        assertTrue(lines.get(1).startsWith("IF,0.8000,0.8000,0.8000,0.0222,8,2,2,88,3,3,2.00"));
        assertTrue(lines.get(2).contains("boom; boom"), "dấu phẩy trong lý do không được làm hỏng CSV");
        assertTrue(table.contains("SKIPPED"));
        assertTrue(table.contains("IF"));
    }

    @Test
    void trainingPeriodAndWindowAreTheDocumentedDefaults() {
        ExperimentEngine.Config config = new ExperimentEngine.Config();

        assertEquals(30, config.trainSamples);      // 5 phút / 10 giây
        assertEquals(60, config.window);
        assertEquals(3, config.ifConsecutive);
        assertEquals(3, config.chronosConsecutive);
        assertEquals(0.05, config.ifThresholdMargin);
        assertEquals(Map.of(), Map.of());
    }
}
