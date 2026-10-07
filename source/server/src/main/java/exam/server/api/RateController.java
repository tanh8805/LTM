// Owner: Nguoi1

package exam.server.api;

import exam.common.model.RateMode;

/**
 * Điều khiển tần suất gửi số liệu của từng máy bằng message SET_RATE.
 *
 *   NORMAL   : summary trong HEARTBEAT, mỗi 10 giây
 *   HIGH     : detail mỗi 2 giây, tự về NORMAL sau 60 giây yên
 *   BASELINE : mọi máy gửi detail mỗi 1 giây, không ML
 *
 * Ai cài đặt: Nguoi1 (exam.server.rate.RateControllerImpl)
 * Ai gọi: Nguoi3 (khi một máy bị nghi ngờ), Nguoi4 (chế độ BASELINE khi thu dữ liệu),
 *         Nguoi1 (PeriodicJobs mỗi giây gọi returnCalmMachinesToNormal)
 */
public interface RateController {

    /**
     * Máy sinh viên vừa đăng nhập hoặc nối lại: gửi SET_RATE theo chế độ hiện tại
     * (để máy mới vào giữa lúc BASELINE hoặc HIGH vẫn đúng tần suất).
     */
    void onMachineOnline(String machineId);

    /** Chế độ hiện tại của một máy. */
    RateMode getMode(String machineId);

    /** Máy đáng ngờ: chuyển sang HIGH (gửi SET_RATE) và đặt lại đồng hồ 60 giây yên. */
    void raiseToHigh(String machineId);

    /** Chuyển mọi máy sang BASELINE (gửi SET_RATE cho tất cả). */
    void startBaselineForAll();

    /** Đưa mọi máy về NORMAL sau khi thu xong dữ liệu BASELINE. */
    void stopBaselineForAll();

    /** Máy HIGH nào đã yên đủ 60 giây thì chuyển về NORMAL. */
    void returnCalmMachinesToNormal();
}
