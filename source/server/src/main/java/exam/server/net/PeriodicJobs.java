// Owner: Nguoi1

package exam.server.net;

import exam.server.ServerConfig;
import exam.server.api.ExamService;
import exam.server.api.MonitorService;
import exam.server.api.RateController;

/**
 * Một vòng lặp nền, mỗi giây "gõ" một lần để các service làm việc định kỳ:
 *
 *   mỗi giây              : examService.startDueShifts()                (tới giờ hẹn thì bắt đầu ca)
 *                           examService.finishExpiredExams()            (Server tự chốt bài khi hết giờ)
 *                           rateController.returnCalmMachinesToNormal() (HIGH -> NORMAL sau khi yên)
 *   mỗi time.sync.interval: examService.sendTimeSync()                  (đồng hồ đếm ngược khớp Server)
 *   mỗi monitor.interval  : monitorService.runPeriodicChecks()          (ROOM_STATS, gọi ML, ALERT)
 */
public class PeriodicJobs {

    private final ServerConfig config;
    private final ExamService examService;
    private final MonitorService monitorService;
    private final RateController rateController;
    private volatile boolean running = false;
    private Thread thread;

    public PeriodicJobs(ServerConfig config, ExamService examService, MonitorService monitorService,
                        RateController rateController) {
        this.config = config;
        this.examService = examService;
        this.monitorService = monitorService;
        this.rateController = rateController;
    }

    public void start() {
        running = true;
        thread = Thread.startVirtualThread(this::runForever);
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runForever() {
        int secondsPassed = 0;
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Có ai đó yêu cầu dừng: giữ lại cờ interrupt rồi thoát vòng lặp.
                Thread.currentThread().interrupt();
                return;
            }
            secondsPassed++;

            // Mỗi việc chạy riêng trong try/catch: một việc lỗi không được làm các việc khác ngừng chạy.
            runSafely("startDueShifts", examService::startDueShifts);
            runSafely("finishExpiredExams", examService::finishExpiredExams);
            runSafely("returnCalmMachinesToNormal", rateController::returnCalmMachinesToNormal);
            if (secondsPassed % config.timeSyncIntervalSeconds == 0) {
                runSafely("sendTimeSync", examService::sendTimeSync);
            }
            if (secondsPassed % config.monitorIntervalSeconds == 0) {
                runSafely("runPeriodicChecks", monitorService::runPeriodicChecks);
            }
        }
    }

    private void runSafely(String jobName, Runnable job) {
        try {
            job.run();
        } catch (RuntimeException e) {
            System.out.println("[Jobs] " + jobName + " lỗi:");
            e.printStackTrace();
        }
    }
}
