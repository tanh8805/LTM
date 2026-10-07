// Owner: Nguoi3

package exam.server.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import exam.common.ml.MlResult;
import exam.common.model.AlertLevel;
import exam.common.model.Metrics;
import exam.common.model.RateMode;
import exam.common.model.Role;
import exam.common.model.UserAccount;
import exam.common.protocol.AlertMessage;
import exam.common.protocol.HeartbeatMessage;
import exam.common.protocol.MetricsDetailMessage;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import exam.common.protocol.RoomStatsMessage;
import exam.common.protocol.SetRateMessage;
import exam.common.protocol.ViolationMessage;
import exam.common.model.ViolationType;
import exam.server.ServerConfig;
import exam.server.db.Database;
import exam.server.db.MetricsLogDao;
import exam.server.db.ViolationDao;
import exam.server.ml.MlConfig;
import exam.server.rate.RateControllerImpl;
import exam.server.session.InMemorySessionRegistry;
import exam.server.testutil.FakeMessageSender;
import exam.server.testutil.FakeMlGateway;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

/** Test giám sát phía Server: vi phạm, thống kê phòng, ML theo ml.mode, ALERT, rate control. */
class MonitorServiceTest {

    @TempDir
    Path tempDirectory;

    private FakeMessageSender sender;
    private FakeMlGateway mlGateway;
    private RateControllerImpl rateController;
    private MonitorServiceImpl monitor;
    private Database database;

    /** Dựng MonitorService với ml.mode cho trước và 5 sinh viên SV001..SV005 đã "đăng nhập" (session giả). */
    private void setUp(String mlMode, Properties serverOverrides) throws Exception {
        database = new Database(tempDirectory.resolve("monitor-test.db").toString());
        database.createTables();
        database.loadSampleDataIfEmpty();

        ServerConfig config = ServerConfig.fromProperties(serverOverrides);
        Properties mlProperties = new Properties();
        mlProperties.setProperty("ml.mode", mlMode);
        MlConfig mlConfig = MlConfig.fromProperties(mlProperties);

        InMemorySessionRegistry registry = new InMemorySessionRegistry();
        for (int i = 1; i <= 5; i++) {
            String code = String.format("SV%03d", i);
            registry.createSession(new UserAccount(i, code, "Sinh vien " + i, Role.STUDENT), "CA001", null);
        }

        sender = new FakeMessageSender();
        mlGateway = new FakeMlGateway();
        rateController = new RateControllerImpl(config, sender);
        monitor = new MonitorServiceImpl(config, mlConfig, database, registry, sender, rateController, mlGateway);
    }

    private void setUp(String mlMode) throws Exception {
        setUp(mlMode, new Properties());
    }

    private HeartbeatMessage heartbeat(int seq, double kbSent, boolean anomalous) {
        Metrics metrics = new Metrics(kbSent, 5, 4, 3, 20, 40, 100, 0);
        return new HeartbeatMessage(seq, metrics, anomalous ? 0.8 : 0.4, anomalous);
    }

    private void sendViolation(String machineId, ViolationType type, String evidence) {
        monitor.onViolation(machineId, new ViolationMessage(type, evidence, System.currentTimeMillis()));
    }

    private AlertMessage lastAlert() {
        List<AlertMessage> alerts = sender.teacherMessagesOfType(AlertMessage.class);
        return alerts.get(alerts.size() - 1);
    }

    // ------------------------------------------------------------------
    // Vi phạm (5 luật) -> ALERT
    // ------------------------------------------------------------------

    @Test
    void processDenylistViolationIsRedAlert() throws Exception {
        setUp("NONE");

        sendViolation("SV001", ViolationType.PROCESS_DENYLIST, "Zalo.exe");

        AlertMessage alert = lastAlert();
        assertEquals("SV001", alert.machineId);
        assertEquals(AlertLevel.RED, alert.level);
        assertTrue(alert.reason.contains("Zalo.exe"));
    }

    @Test
    void processNotInAllowlistViolationIsRedAlert() throws Exception {
        setUp("NONE");

        sendViolation("SV001", ViolationType.PROCESS_NOT_ALLOWED, "notepad.exe");

        assertEquals(AlertLevel.RED, lastAlert().level);
        assertTrue(lastAlert().reason.contains("allowlist"));
    }

    @Test
    void forbiddenIpViolationIsRedAlert() throws Exception {
        setUp("NONE");

        sendViolation("SV002", ViolationType.BLOCKED_IP, "104.18.1.1");

        assertEquals(AlertLevel.RED, lastAlert().level);
        assertTrue(lastAlert().reason.contains("104.18.1.1"));
    }

    @Test
    void usbViolationIsRedAlert() throws Exception {
        setUp("NONE");

        sendViolation("SV003", ViolationType.USB_DEVICE, "SanDisk Cruzer");

        assertEquals(AlertLevel.RED, lastAlert().level);
        assertTrue(lastAlert().reason.contains("USB"));
    }

    @Test
    void networkCardViolationIsRedAlert() throws Exception {
        setUp("NONE");

        sendViolation("SV004", ViolationType.NETWORK_CARD, "tun0 (VPN)");

        assertEquals(AlertLevel.RED, lastAlert().level);
        assertTrue(lastAlert().reason.contains("tun0"));
    }

    @Test
    void focusLossViolationIsYellowWarning() throws Exception {
        setUp("NONE");

        sendViolation("SV005", ViolationType.FOCUS_LOSS, "5 lần");

        assertEquals(AlertLevel.YELLOW, lastAlert().level);
    }

    @Test
    void sameViolationReportedTwiceSendsOnlyOneAlert() throws Exception {
        setUp("NONE");

        sendViolation("SV001", ViolationType.USB_DEVICE, "SanDisk");
        sendViolation("SV001", ViolationType.USB_DEVICE, "SanDisk");

        assertEquals(1, sender.teacherMessagesOfType(AlertMessage.class).size());
    }

    @Test
    void violationIsSavedInDatabase() throws Exception {
        setUp("NONE");

        sendViolation("SV001", ViolationType.PROCESS_DENYLIST, "Zalo.exe");

        ViolationDao dao = new ViolationDao(database);
        assertEquals(1, dao.countByStudent(1));
        assertEquals("PROCESS_DENYLIST", dao.findRecent(1, 10).get(0).violationType);
        assertEquals("Zalo.exe", dao.findRecent(1, 10).get(0).evidence);
    }

    @Test
    void violationRaisesTheMachineToHighRate() throws Exception {
        setUp("NONE");
        assertEquals(RateMode.NORMAL, rateController.getMode("SV001"));

        sendViolation("SV001", ViolationType.USB_DEVICE, "SanDisk");

        assertEquals(RateMode.HIGH, rateController.getMode("SV001"));
        List<SetRateMessage> rateMessages = sender.machineMessagesOfType(SetRateMessage.class);
        assertEquals(1, rateMessages.size());
        assertEquals(RateMode.HIGH, rateMessages.get(0).mode);
        assertEquals(2000, rateMessages.get(0).intervalMs);
    }

    @Test
    void violationFromUnknownMachineIsIgnored() throws Exception {
        setUp("NONE");

        sendViolation("SV999", ViolationType.USB_DEVICE, "SanDisk");

        assertEquals(0, sender.teacherMessagesOfType(AlertMessage.class).size());
    }

    // ------------------------------------------------------------------
    // Thống kê phòng
    // ------------------------------------------------------------------

    private void feedRoom(double... kbSentPerMachine) {
        for (int i = 0; i < kbSentPerMachine.length; i++) {
            monitor.onHeartbeat(String.format("SV%03d", i + 1), heartbeat(1, kbSentPerMachine[i], false));
        }
    }

    @Test
    void periodicCheckSendsRoomStatsWithMedianAndMad() throws Exception {
        setUp("NONE");
        feedRoom(10, 11, 9, 10, 12);

        monitor.runPeriodicChecks();

        List<RoomStatsMessage> stats = sender.sentToStudents.stream()
                .filter(m -> m instanceof RoomStatsMessage).map(m -> (RoomStatsMessage) m).toList();
        assertEquals(1, stats.size());
        assertEquals(10.0, stats.get(0).medians.get("kbSent"));
        assertEquals(1.0, stats.get(0).mads.get("kbSent")); // |x-10| = [0,1,1,0,2] -> MAD = 1
        assertEquals(5.0, stats.get(0).medians.get("kbReceived"));
    }

    @Test
    void machineFarAboveRoomMedianGetsRedAlertWithRatioInReason() throws Exception {
        setUp("NONE");
        feedRoom(10, 10, 10, 10, 100); // 10 lần trung vị >= room.critical.multiplier (6)

        monitor.runPeriodicChecks();

        AlertMessage alert = lastAlert();
        assertEquals("SV005", alert.machineId);
        assertEquals(AlertLevel.RED, alert.level);
        assertTrue(alert.reason.contains("kbSent gấp 10.0 lần trung vị phòng"), alert.reason);
    }

    @Test
    void moderateDeviationGetsYellowAlert() throws Exception {
        setUp("NONE");
        feedRoom(10, 11, 9, 10, 40); // 4 lần: giữa warning (3) và critical (6)

        monitor.runPeriodicChecks();

        AlertMessage alert = lastAlert();
        assertEquals("SV005", alert.machineId);
        assertEquals(AlertLevel.YELLOW, alert.level);
        assertTrue(alert.reason.contains("gấp 4.0 lần trung vị phòng"), alert.reason);
    }

    @Test
    void normalRoomProducesNoAlert() throws Exception {
        setUp("NONE");
        feedRoom(10, 11, 9, 10, 12);

        monitor.runPeriodicChecks();

        assertEquals(0, sender.teacherMessagesOfType(AlertMessage.class).size());
    }

    @Test
    void roomAlertNeedsMinimumNumberOfMachines() throws Exception {
        setUp("NONE");
        feedRoom(10, 100); // chỉ 2 máy < room.min.machines (3)

        monitor.runPeriodicChecks();

        assertEquals(0, sender.teacherMessagesOfType(AlertMessage.class).size());
    }

    @Test
    void roomAlertClearsWhenMachineBecomesNormalAgain() throws Exception {
        setUp("NONE");
        feedRoom(10, 10, 10, 10, 100);
        monitor.runPeriodicChecks();
        assertNotNull(monitor.getStateForTest("SV005").getCurrentLevel());

        monitor.onHeartbeat("SV005", heartbeat(2, 10, false));
        monitor.runPeriodicChecks();

        assertNull(monitor.getStateForTest("SV005").getCurrentLevel());
    }

    @Test
    void roomAlertThresholdsAreReadFromConfig() throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("room.warning.multiplier", "1.5");
        overrides.setProperty("room.critical.multiplier", "50");
        setUp("NONE", overrides);
        feedRoom(10, 10, 10, 10, 20); // 2 lần: chỉ vượt ngưỡng vàng đã hạ xuống 1.5

        monitor.runPeriodicChecks();

        assertEquals(AlertLevel.YELLOW, lastAlert().level);
    }

    @Test
    void roomDeviationRaisesMachineToHighRate() throws Exception {
        setUp("NONE");
        feedRoom(10, 10, 10, 10, 100);

        monitor.runPeriodicChecks();

        assertEquals(RateMode.HIGH, rateController.getMode("SV005"));
        assertEquals(RateMode.NORMAL, rateController.getMode("SV001"));
    }

    // ------------------------------------------------------------------
    // Online / offline
    // ------------------------------------------------------------------

    @Test
    void offlineMachineGetsYellowAlertAndOnlineClearsIt() throws Exception {
        setUp("NONE");
        monitor.onMachineOnline("SV001");

        monitor.onMachineOffline("SV001");

        assertFalse(monitor.getStateForTest("SV001").isOnline());
        assertEquals(AlertLevel.YELLOW, lastAlert().level);
        assertTrue(lastAlert().reason.contains("Mất kết nối"));

        monitor.onMachineOnline("SV001");

        assertTrue(monitor.getStateForTest("SV001").isOnline());
        assertNull(monitor.getStateForTest("SV001").getCurrentLevel());
    }

    // ------------------------------------------------------------------
    // ML theo ml.mode
    // ------------------------------------------------------------------

    private void giveChronosVerdict(String machineId, boolean suspicious) {
        mlGateway.nextResults.put(machineId, new MlResult(suspicious, suspicious ? 3 : 0,
                suspicious ? "kbSent ngoài khoảng q0.1-q0.9 3 lần liên tiếp" : null));
    }

    private int mlAlertCount() {
        int count = 0;
        for (AlertMessage alert : sender.teacherMessagesOfType(AlertMessage.class)) {
            if (alert.reason.startsWith("ML")) {
                count++;
            }
        }
        return count;
    }

    @Test
    void modeNoneNeverRaisesMlAlertAndNeverCallsChronos() throws Exception {
        setUp("NONE");
        giveChronosVerdict("SV001", true);

        monitor.onHeartbeat("SV001", heartbeat(1, 10, true));
        monitor.runPeriodicChecks();

        assertEquals(0, mlAlertCount());
        assertEquals(0, mlGateway.scoreCalls);
        assertTrue(mlGateway.recordedMachines.isEmpty());
    }

    @Test
    void modeIfUsesIsolationForestOnly() throws Exception {
        setUp("IF");
        giveChronosVerdict("SV001", true); // Chronos nói đáng ngờ nhưng mode IF phải bỏ qua

        monitor.onHeartbeat("SV001", heartbeat(1, 10, false));
        monitor.runPeriodicChecks();
        assertEquals(0, mlAlertCount());

        monitor.onHeartbeat("SV001", heartbeat(2, 10, true));
        assertEquals(1, mlAlertCount());
        assertEquals(AlertLevel.YELLOW, lastAlert().level);
        assertTrue(lastAlert().reason.contains("Isolation Forest"));
        assertEquals(0, mlGateway.scoreCalls, "mode IF không gọi ml-service");
    }

    @Test
    void modeChronosUsesChronosOnly() throws Exception {
        setUp("CHRONOS");

        monitor.onHeartbeat("SV001", heartbeat(1, 10, true)); // IF báo bất thường, Chronos chưa nói gì
        monitor.runPeriodicChecks();
        assertEquals(0, mlAlertCount());

        giveChronosVerdict("SV001", true);
        monitor.runPeriodicChecks();

        assertEquals(1, mlAlertCount());
        assertTrue(lastAlert().reason.contains("Chronos"));
        assertEquals(1, mlGateway.recordedMachines.size());
    }

    @Test
    void modeBothAndNeedsBothModels() throws Exception {
        setUp("BOTH_AND");
        giveChronosVerdict("SV001", true);

        monitor.onHeartbeat("SV001", heartbeat(1, 10, false)); // chỉ Chronos báo
        monitor.runPeriodicChecks();
        assertEquals(0, mlAlertCount(), "chỉ một mô hình báo thì chưa đủ");

        monitor.onHeartbeat("SV001", heartbeat(2, 10, true)); // giờ cả hai cùng báo
        assertEquals(1, mlAlertCount());
    }

    @Test
    void modeBothOrNeedsEitherModel() throws Exception {
        setUp("BOTH_OR");

        monitor.onHeartbeat("SV001", heartbeat(1, 10, true)); // chỉ IF báo
        assertEquals(1, mlAlertCount());

        monitor.onHeartbeat("SV002", heartbeat(1, 10, false));
        giveChronosVerdict("SV002", true); // chỉ Chronos báo
        monitor.runPeriodicChecks();
        assertEquals(2, mlAlertCount());
    }

    @Test
    void mlAlertClearsWhenModelStopsFlagging() throws Exception {
        setUp("IF");
        monitor.onHeartbeat("SV001", heartbeat(1, 10, true));
        assertNotNull(monitor.getStateForTest("SV001").getCurrentLevel());

        monitor.onHeartbeat("SV001", heartbeat(2, 10, false));

        assertNull(monitor.getStateForTest("SV001").getCurrentLevel());
    }

    @Test
    void emptyChronosResultsFromMlFailureDoNotCrashOrAlert() throws Exception {
        setUp("CHRONOS");
        mlGateway.nextResults.clear(); // gateway trả map rỗng khi ml-service lỗi hoặc timeout
        monitor.onHeartbeat("SV001", heartbeat(1, 10, false));

        monitor.runPeriodicChecks();
        monitor.runPeriodicChecks();

        assertEquals(2, mlGateway.scoreCalls);
        assertEquals(0, mlAlertCount());
    }

    @Test
    void baselineModeDisablesMlAlerts() throws Exception {
        setUp("IF");
        monitor.onHeartbeat("SV001", heartbeat(1, 10, false));
        rateController.startBaselineForAll();

        monitor.onHeartbeat("SV001", heartbeat(2, 10, true));

        assertEquals(0, mlAlertCount());
    }

    // ------------------------------------------------------------------
    // Số liệu được lưu, yêu cầu của giáo viên
    // ------------------------------------------------------------------

    @Test
    void heartbeatAndDetailAreLoggedForBaselineAndStatistics() throws Exception {
        setUp("NONE");

        monitor.onHeartbeat("SV001", heartbeat(1, 10, false));
        monitor.onMetricsDetail("SV001", new MetricsDetailMessage(2, System.currentTimeMillis(),
                new Metrics(1, 2, 3, 4, 5, 6, 7, 8), List.of("java"), List.of("1.1.1.1")));

        assertEquals(2, new MetricsLogDao(database).count("SV001"));
        String csv = new MetricsLogDao(database).exportCsv("SV001", null);
        assertEquals(3, csv.split("\n").length); // tiêu đề + 2 dòng
    }

    private ResponseMessage teacherRequest(String action, JsonObject data) {
        assertTrue(monitor.handlesAction(action));
        return monitor.handleTeacherRequest(new RequestMessage("r1", action, data));
    }

    @Test
    void teacherCanListMachinesAndSeeDetail() throws Exception {
        setUp("NONE");
        monitor.onHeartbeat("SV001", heartbeat(1, 10, false));
        monitor.onMetricsDetail("SV001", new MetricsDetailMessage(2, System.currentTimeMillis(),
                new Metrics(1, 2, 3, 4, 5, 6, 7, 8), List.of("java", "chrome"), List.of("1.1.1.1")));
        sendViolation("SV001", ViolationType.USB_DEVICE, "SanDisk");

        ResponseMessage list = teacherRequest("LIST_MACHINES", new JsonObject());
        assertTrue(list.ok);
        JsonObject row = list.data.getAsJsonArray("machines").get(0).getAsJsonObject();
        assertEquals("SV001", row.get("machineId").getAsString());
        assertEquals("RED", row.get("level").getAsString());
        assertTrue(row.get("online").getAsBoolean());

        JsonObject detailRequest = new JsonObject();
        detailRequest.addProperty("machineId", "SV001");
        ResponseMessage detail = teacherRequest("MACHINE_DETAIL", detailRequest);
        assertTrue(detail.ok);
        assertEquals(2, detail.data.getAsJsonArray("processNames").size());
        assertEquals(1, detail.data.getAsJsonArray("violations").size());
        assertEquals(8.0, detail.data.getAsJsonObject("metrics").get("focusLostCount").getAsDouble());

        JsonObject unknown = new JsonObject();
        unknown.addProperty("machineId", "SV999");
        assertFalse(teacherRequest("MACHINE_DETAIL", unknown).ok);
    }

    @Test
    void teacherCanSwitchWholeRoomToBaselineAndBack() throws Exception {
        setUp("NONE");
        rateController.onMachineOnline("SV001");

        JsonObject on = new JsonObject();
        on.addProperty("enabled", true);
        assertTrue(teacherRequest("SET_BASELINE", on).ok);
        assertEquals(RateMode.BASELINE, rateController.getMode("SV001"));

        JsonObject off = new JsonObject();
        off.addProperty("enabled", false);
        assertTrue(teacherRequest("SET_BASELINE", off).ok);
        assertEquals(RateMode.NORMAL, rateController.getMode("SV001"));

        assertFalse(teacherRequest("SET_BASELINE", new JsonObject()).ok); // thiếu tham số
    }
}
