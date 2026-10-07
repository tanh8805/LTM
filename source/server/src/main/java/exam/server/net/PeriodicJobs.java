// Owner: Nguoi1

package exam.server.net;

import exam.server.api.ExamService;
import exam.server.api.MonitorService;
import exam.server.api.RateController;

/**
 * Một vòng lặp nền, mỗi giây "gõ" một lần để các service làm việc định kỳ:
 *
 *   mỗi giây    : examService.finishExpiredExams()           (Server tự chốt bài khi hết giờ)
 *                 rateController.returnCalmMachinesToNormal() (HIGH -> NORMAL sau 60 giây yên)
 *   mỗi 10 giây : monitorService.runPeriodicChecks()         (ROOM_STATS, gọi ML, ALERT)
 */
public class PeriodicJobs {

    private static final int MONITOR_EVERY_N_SECONDS = 10;

    private final ExamService examService;
    private final MonitorService monitorService;
    private final RateController rateController;

    public PeriodicJobs(ExamService examService, MonitorService monitorService, RateController rateController) {
        this.examService = examService;
        this.monitorService = monitorService;
        this.rateController = rateController;
    }

    public void start() {
        Thread.startVirtualThread(this::runForever);
    }

    private void runForever() {
        int secondsPassed = 0;
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Có ai đó yêu cầu dừng: giữ lại cờ interrupt rồi thoát vòng lặp.
                Thread.currentThread().interrupt();
                return;
            }
            secondsPassed++;

            try {
                examService.finishExpiredExams();
                rateController.returnCalmMachinesToNormal();
                if (secondsPassed % MONITOR_EVERY_N_SECONDS == 0) {
                    monitorService.runPeriodicChecks();
                }
            } catch (RuntimeException e) {
                // Một lần lỗi không được làm chết vòng lặp định kỳ.
                e.printStackTrace();
            }
        }
    }
}
