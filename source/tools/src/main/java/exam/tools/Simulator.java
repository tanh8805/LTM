// Owner: Nguoi4

package exam.tools;

import exam.common.model.Metrics;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Sinh trace mô phỏng: nhiều máy sinh viên hành xử bình thường, một phần trong đó có đoạn gian lận có nhãn.
 *
 *   java -cp "source/tools/target/tools.jar:source/tools/target/lib/*" exam.tools.Simulator
 *        [--machines 10] [--samples 120] [--seed 42] [--anomaly-fraction 0.3] [--out data/traces/sim.csv]
 *
 * Hành vi bình thường: mỗi máy có mức nền riêng, số liệu dao động quanh mức nền (tự hồi quy, nhiễu ~10%).
 * 40% mẫu đầu của MỌI máy luôn bình thường (để mô hình có dữ liệu học).
 * Kịch bản gian lận (một đoạn 8-15 mẫu liên tiếp). Hoạt động gian lận thật (mở trình duyệt, gọi chatbot, gửi file...)
 * luôn tác động NHIỀU metric cùng lúc, nên mỗi kịch bản đổi nhiều chiều:
 *   upload-burst     : KB gửi tăng 15-40 lần (gửi đề ra ngoài), thêm vài kết nối và CPU
 *   ai-chat          : KB nhận tăng 5-10 lần, kết nối và địa chỉ đích tăng, KB gửi (câu hỏi) và CPU (trình duyệt) tăng
 *   remote-desktop   : CPU tăng mạnh, KB gửi và nhận tăng 8-12 lần, thêm kết nối
 *   many-destinations: số địa chỉ đích và kết nối tăng vọt (quét web tìm đáp án), KB nhận và CPU tăng
 * Cùng seed cho ra đúng cùng một file (tái lập được).
 */
public class Simulator {

    private static final long START_TIME = 1_700_000_000_000L;
    private static final long SAMPLE_INTERVAL_MS = 10_000L;
    private static final String[] SCENARIOS = {"upload-burst", "ai-chat", "remote-desktop", "many-destinations"};

    private Simulator() {
    }

    public static void main(String[] args) throws IOException {
        Args options = new Args(args);
        int machines = options.getInt("machines", 10);
        int samples = options.getInt("samples", 120);
        long seed = options.getLong("seed", 42L);
        double anomalyFraction = options.getDouble("anomaly-fraction", 0.3);
        Path out = Path.of(options.get("out", "data/traces/sim.csv"));

        List<TraceRow> rows = generate(machines, samples, seed, anomalyFraction);
        TraceCsv.write(out, rows);

        int anomalousRows = 0;
        for (TraceRow row : rows) {
            anomalousRows += row.label;
        }
        System.out.println("[Simulator] Đã ghi " + rows.size() + " dòng (" + machines + " máy x " + samples
                + " mẫu, " + anomalousRows + " dòng gian lận) vào " + out);
    }

    /** Sinh trace. Kết quả sắp theo (thời gian, mã máy). */
    public static List<TraceRow> generate(int machines, int samples, long seed, double anomalyFraction) {
        if (machines < 1 || samples < 10) {
            throw new IllegalArgumentException("Cần ít nhất 1 máy và 10 mẫu");
        }
        Random random = new Random(seed);

        // Chọn các máy gian lận
        List<Integer> machineIndexes = new ArrayList<>();
        for (int i = 0; i < machines; i++) {
            machineIndexes.add(i);
        }
        Collections.shuffle(machineIndexes, random);
        int cheaterCount = (int) Math.ceil(machines * anomalyFraction);

        List<TraceRow> rows = new ArrayList<>();
        for (int machine = 0; machine < machines; machine++) {
            String machineId = String.format("SV%03d", machine + 1);
            boolean cheater = machineIndexes.indexOf(machine) < cheaterCount;
            rows.addAll(generateMachine(machineId, samples, random, cheater));
        }

        rows.sort(Comparator.comparingLong((TraceRow row) -> row.time).thenComparing(row -> row.machineId));
        return rows;
    }

    private static List<TraceRow> generateMachine(String machineId, int samples, Random random, boolean cheater) {
        // Mức nền riêng của máy: {kbSent, kbReceived, connections, distinct, cpu, ram, processes}
        double[] base = {
                10 + random.nextDouble() * 30, 40 + random.nextDouble() * 60, 6 + random.nextInt(8),
                4 + random.nextInt(5), 8 + random.nextDouble() * 20, 35 + random.nextDouble() * 25,
                90 + random.nextInt(60)};
        double[] current = base.clone();

        int anomalyStart = -1;
        int anomalyEnd = -1;
        String scenario = "normal";
        if (cheater) {
            anomalyStart = (int) (samples * (0.5 + random.nextDouble() * 0.25));
            int length = 8 + random.nextInt(8);
            anomalyEnd = Math.min(samples - 1, anomalyStart + length - 1);
            scenario = SCENARIOS[random.nextInt(SCENARIOS.length)];
        }

        List<TraceRow> rows = new ArrayList<>();
        int focusLost = 0;
        for (int t = 0; t < samples; t++) {
            for (int d = 0; d < base.length; d++) {
                // Tự hồi quy về mức nền + nhiễu ~10%
                current[d] = base[d] + 0.6 * (current[d] - base[d]) + random.nextGaussian() * 0.1 * base[d];
                current[d] = Math.max(0, current[d]);
            }
            if (random.nextDouble() < 0.01) {
                focusLost++;
            }

            double[] values = current.clone();
            boolean inAnomaly = t >= anomalyStart && t <= anomalyEnd && cheater;
            if (inAnomaly) {
                applyScenario(scenario, values, random);
            }

            Metrics metrics = new Metrics(
                    round(values[0]), round(values[1]), (int) Math.round(values[2]), (int) Math.round(values[3]),
                    round(Math.min(100, values[4])), round(Math.min(100, values[5])),
                    (int) Math.round(values[6]), focusLost);
            rows.add(new TraceRow(machineId, START_TIME + t * SAMPLE_INTERVAL_MS,
                    inAnomaly ? 1 : 0, inAnomaly ? scenario : "normal", metrics));
        }
        return rows;
    }

    private static void applyScenario(String scenario, double[] values, Random random) {
        // values: {kbSent, kbReceived, connections, distinct, cpu, ram, processes}
        switch (scenario) {
            case "upload-burst":
                values[0] *= 15 + random.nextDouble() * 25;
                values[2] += 3 + random.nextInt(3);
                values[4] += 8 + random.nextDouble() * 8;
                break;
            case "ai-chat":
                values[0] *= 2 + random.nextDouble() * 2;
                values[1] *= 5 + random.nextDouble() * 5;
                values[2] += 10 + random.nextInt(6);
                values[3] += 6 + random.nextInt(4);
                values[4] += 10 + random.nextDouble() * 10;
                break;
            case "remote-desktop":
                values[0] *= 8 + random.nextDouble() * 4;
                values[1] *= 8 + random.nextDouble() * 4;
                values[2] += 3 + random.nextInt(3);
                values[4] += 40 + random.nextDouble() * 20;
                break;
            case "many-destinations":
            default:
                values[1] *= 3 + random.nextDouble() * 2;
                values[2] += 20 + random.nextInt(10);
                values[3] += 25 + random.nextInt(15);
                values[4] += 8 + random.nextDouble() * 8;
                break;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
