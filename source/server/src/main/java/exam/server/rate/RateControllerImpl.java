// Owner: Nguoi1

package exam.server.rate;

import exam.common.model.RateMode;
import exam.server.api.MessageSender;
import exam.server.api.RateController;
import exam.server.api.SessionRegistry;

/**
 * STUB của RateController: chưa gửi SET_RATE, mọi máy luôn ở NORMAL.
 *
 * Gợi ý khi cài: giữ Map machineId -> (mode, lastSuspiciousTime). Khi đổi mode thì
 * messageSender.sendToMachine(machineId, new SetRateMessage(mode, intervalMs)).
 */
public class RateControllerImpl implements RateController {

    // Các field này chưa dùng, sẽ dùng khi cài đặt các TODO bên dưới.
    private final MessageSender messageSender;
    private final SessionRegistry sessionRegistry;

    public RateControllerImpl(MessageSender messageSender, SessionRegistry sessionRegistry) {
        this.messageSender = messageSender;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public RateMode getMode(String machineId) {
        // TODO(Nguoi1): Trả về mode hiện tại của máy (đang luôn NORMAL).
        return RateMode.NORMAL;
    }

    @Override
    public void raiseToHigh(String machineId) {
        // TODO(Nguoi1): Đặt mode HIGH, ghi lại giờ nghi ngờ gần nhất, gửi SET_RATE(HIGH, 2000 ms).
    }

    @Override
    public void startBaselineForAll() {
        // TODO(Nguoi1): Đặt mọi máy online sang BASELINE, gửi SET_RATE(BASELINE, 1000 ms).
    }

    @Override
    public void stopBaselineForAll() {
        // TODO(Nguoi1): Đưa mọi máy về NORMAL, gửi SET_RATE(NORMAL, 10000 ms).
    }

    @Override
    public void returnCalmMachinesToNormal() {
        // TODO(Nguoi1): Máy HIGH nào 60 giây không bị nghi ngờ thì về NORMAL và gửi SET_RATE(NORMAL, 10000 ms).
    }
}
