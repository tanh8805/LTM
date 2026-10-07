// Owner: Nguoi1

package exam.server.rate;

import exam.common.model.RateMode;
import exam.common.protocol.SetRateMessage;
import exam.server.ServerConfig;
import exam.server.api.MessageSender;
import exam.server.api.RateController;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Điều khiển tần suất gửi số liệu của từng máy.
 *
 *   NORMAL   : summary trong HEARTBEAT, mỗi rate.normal.interval.ms (10 giây)
 *   HIGH     : METRICS_DETAIL mỗi rate.high.interval.ms (2 giây). Về NORMAL sau rate.high.quiet.seconds (60 giây) yên.
 *   BASELINE : METRICS_DETAIL mỗi rate.baseline.interval.ms (1 giây), không ML. Có ưu tiên cao hơn HIGH.
 *
 * Mỗi lần đổi chế độ của một máy, Server gửi SET_RATE cho máy đó.
 */
public class RateControllerImpl implements RateController {

    /** Trạng thái của một máy. */
    private static class MachineRate {
        RateMode mode = RateMode.NORMAL;
        /** Lần gần nhất máy bị nghi ngờ (epoch milli giây). */
        long lastSuspiciousTime = 0;
    }

    private final ServerConfig config;
    private final MessageSender messageSender;
    private final Map<String, MachineRate> machines = new ConcurrentHashMap<>();
    /** true khi giáo viên bật BASELINE cho cả phòng (máy vào sau cũng phải BASELINE). */
    private volatile boolean baselineForAll = false;

    public RateControllerImpl(ServerConfig config, MessageSender messageSender) {
        this.config = config;
        this.messageSender = messageSender;
    }

    @Override
    public void onMachineOnline(String machineId) {
        MachineRate machine = machines.computeIfAbsent(machineId, key -> new MachineRate());
        synchronized (machine) {
            if (baselineForAll) {
                machine.mode = RateMode.BASELINE;
            }
            // Luôn gửi chế độ hiện tại để client mới (hoặc client nối lại) đồng bộ với Server.
            sendRate(machineId, machine.mode);
        }
    }

    @Override
    public RateMode getMode(String machineId) {
        MachineRate machine = machines.get(machineId);
        if (machine == null) {
            return baselineForAll ? RateMode.BASELINE : RateMode.NORMAL;
        }
        return machine.mode;
    }

    @Override
    public void raiseToHigh(String machineId) {
        MachineRate machine = machines.computeIfAbsent(machineId, key -> new MachineRate());
        synchronized (machine) {
            machine.lastSuspiciousTime = System.currentTimeMillis(); // đặt lại đồng hồ "yên"
            if (machine.mode == RateMode.NORMAL) {
                machine.mode = RateMode.HIGH;
                System.out.println("[Rate] " + machineId + " NORMAL -> HIGH");
                sendRate(machineId, RateMode.HIGH);
            }
            // Đang BASELINE thì giữ nguyên BASELINE; đang HIGH thì chỉ cần gia hạn như trên.
        }
    }

    @Override
    public void startBaselineForAll() {
        baselineForAll = true;
        for (Map.Entry<String, MachineRate> entry : machines.entrySet()) {
            MachineRate machine = entry.getValue();
            synchronized (machine) {
                if (machine.mode != RateMode.BASELINE) {
                    machine.mode = RateMode.BASELINE;
                    sendRate(entry.getKey(), RateMode.BASELINE);
                }
            }
        }
        System.out.println("[Rate] BASELINE cho cả phòng");
    }

    @Override
    public void stopBaselineForAll() {
        baselineForAll = false;
        for (Map.Entry<String, MachineRate> entry : machines.entrySet()) {
            MachineRate machine = entry.getValue();
            synchronized (machine) {
                if (machine.mode == RateMode.BASELINE) {
                    machine.mode = RateMode.NORMAL;
                    sendRate(entry.getKey(), RateMode.NORMAL);
                }
            }
        }
        System.out.println("[Rate] Kết thúc BASELINE, về NORMAL");
    }

    @Override
    public void returnCalmMachinesToNormal() {
        long now = System.currentTimeMillis();
        long quietMillis = config.rateHighQuietSeconds * 1000L;

        for (Map.Entry<String, MachineRate> entry : machines.entrySet()) {
            MachineRate machine = entry.getValue();
            synchronized (machine) {
                if (machine.mode == RateMode.HIGH && now - machine.lastSuspiciousTime >= quietMillis) {
                    machine.mode = RateMode.NORMAL;
                    System.out.println("[Rate] " + entry.getKey() + " HIGH -> NORMAL (yên "
                            + config.rateHighQuietSeconds + " giây)");
                    sendRate(entry.getKey(), RateMode.NORMAL);
                }
            }
        }
    }

    private void sendRate(String machineId, RateMode mode) {
        messageSender.sendToMachine(machineId, new SetRateMessage(mode, intervalOf(mode)));
    }

    private int intervalOf(RateMode mode) {
        switch (mode) {
            case HIGH:
                return config.rateHighIntervalMs;
            case BASELINE:
                return config.rateBaselineIntervalMs;
            case NORMAL:
            default:
                return config.rateNormalIntervalMs;
        }
    }
}
