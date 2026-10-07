// Owner: Nguoi3

package exam.server.monitor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import exam.common.ml.MlDecision;
import exam.common.ml.MlResult;
import exam.common.model.AlertLevel;
import exam.common.model.ExamShift;
import exam.common.model.Metrics;
import exam.common.model.MlMode;
import exam.common.model.RateMode;
import exam.common.model.Role;
import exam.common.protocol.AlertMessage;
import exam.common.protocol.HeartbeatMessage;
import exam.common.protocol.MetricsDetailMessage;
import exam.common.protocol.RequestMessage;
import exam.common.protocol.ResponseMessage;
import exam.common.protocol.RoomStatsMessage;
import exam.common.protocol.ViolationMessage;
import exam.server.ServerConfig;
import exam.server.api.MessageSender;
import exam.server.api.MlGateway;
import exam.server.api.MonitorService;
import exam.server.api.RateController;
import exam.server.api.SessionRegistry;
import exam.server.db.CandidateRecord;
import exam.server.db.Database;
import exam.server.db.MetricsLogDao;
import exam.server.db.ShiftDao;
import exam.server.db.ViolationDao;
import exam.server.ml.MlConfig;
import exam.server.session.ClientSession;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giám sát phía Server.
 *
 * Dữ liệu vào:  HEARTBEAT (summary + điểm Isolation Forest), METRICS_DETAIL, VIOLATION, online/offline.
 * Việc định kỳ (runPeriodicChecks, mỗi monitor.interval.seconds):
 *   1. Tính median + MAD từng metric của cả phòng, gửi ROOM_STATS xuống sinh viên.
 *   2. Máy nào lệch quá ngưỡng so với phòng -> cảnh báo VÀNG hoặc ĐỎ, lý do "gấp X lần trung vị phòng".
 *   3. Nếu ml.mode dùng Chronos: gọi MlGateway.scoreAllMachines() (bất đồng bộ, không chặn).
 *   4. Ghép Isolation Forest và Chronos theo ml.mode (MlDecision) -> cảnh báo VÀNG "ML".
 * Cảnh báo mới được gửi cho mọi giáo viên bằng ALERT, và máy đáng ngờ được đưa sang chế độ HIGH.
 *
 * Mức cảnh báo: vi phạm luật 1-4 (process, IP, USB, network card) là ĐỎ; mất focus quá ngưỡng, lệch phòng mức
 * vừa, ML đáng ngờ, mất kết nối là VÀNG. Lệch phòng quá room.critical.multiplier là ĐỎ.
 */
public class MonitorServiceImpl implements MonitorService {

    /** Metric nào được so với phòng: 7 chiều đầu. focusLostCount đã có luật riêng nên bỏ ra. */
    private static final int ROOM_ALERT_DIMENSIONS = 7;
    private static final int MAX_REASONS_IN_ALERT = 3;
    private static final int RECENT_VIOLATIONS_IN_DETAIL = 50;

    private final ServerConfig config;
    private final MlConfig mlConfig;
    private final SessionRegistry sessionRegistry;
    private final MessageSender messageSender;
    private final RateController rateController;
    private final MlGateway mlGateway;
    private final MlMode mlMode;
    private final ViolationDao violationDao;
    private final MetricsLogDao metricsLogDao;
    private final ShiftDao shiftDao;
    private final Gson gson = new Gson();

    private final Map<String, MachineState> machines = new ConcurrentHashMap<>();

    public MonitorServiceImpl(ServerConfig config, MlConfig mlConfig, Database database, SessionRegistry sessionRegistry,
                              MessageSender messageSender, RateController rateController, MlGateway mlGateway) {
        this.config = config;
        this.mlConfig = mlConfig;
        this.mlMode = mlConfig.mode;
        this.sessionRegistry = sessionRegistry;
        this.messageSender = messageSender;
        this.rateController = rateController;
        this.mlGateway = mlGateway;
        this.violationDao = new ViolationDao(database);
        this.metricsLogDao = new MetricsLogDao(database);
        this.shiftDao = new ShiftDao(database);
    }

    // ------------------------------------------------------------------
    // Dữ liệu từ client
    // ------------------------------------------------------------------

    @Override
    public void onHeartbeat(String machineId, HeartbeatMessage heartbeat) {
        MachineState state = getOrCreateState(machineId);
        if (state == null || heartbeat.summary == null) {
            return;
        }
        long now = System.currentTimeMillis();
        state.updateFromHeartbeat(heartbeat.summary, heartbeat.anomalyScore, heartbeat.anomalous, now);
        logMetrics(machineId, now, "SUMMARY", heartbeat.summary);

        // ML chỉ chạy ngoài chế độ BASELINE (BASELINE là lúc thu dữ liệu nền, không ML).
        if (rateController.getMode(machineId) != RateMode.BASELINE && MlDecision.usesChronos(mlMode)) {
            mlGateway.recordMetrics(machineId, heartbeat.summary);
        }
        evaluateMlAlert(state);
    }

    @Override
    public void onMetricsDetail(String machineId, MetricsDetailMessage detail) {
        MachineState state = getOrCreateState(machineId);
        if (state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        state.updateFromDetail(detail, now);
        logMetrics(machineId, now, "DETAIL", detail.metrics);
        evaluateMlAlert(state);
    }

    @Override
    public void onViolation(String machineId, ViolationMessage violation) {
        MachineState state = getOrCreateState(machineId);
        if (state == null || violation.violationType == null) {
            return;
        }

        saveViolation(state, violation);

        AlertLevel level = levelOfViolation(violation);
        String reason = describeViolation(violation);
        String key = "V:" + violation.violationType + ":" + violation.evidence;
        if (state.setAlert(key, level, reason)) {
            sendAlert(machineId, level, reason);
        }
        rateController.raiseToHigh(machineId);
    }

    @Override
    public void onMachineOnline(String machineId) {
        MachineState state = getOrCreateState(machineId);
        if (state == null) {
            return;
        }
        state.setOnline(true);
        state.clearAlert("OFFLINE");
    }

    @Override
    public void onMachineOffline(String machineId) {
        MachineState state = machines.get(machineId);
        if (state == null) {
            return;
        }
        state.setOnline(false);
        String reason = "Mất kết nối (không nhận được heartbeat)";
        if (state.setAlert("OFFLINE", AlertLevel.YELLOW, reason)) {
            sendAlert(machineId, AlertLevel.YELLOW, reason);
        }
    }

    // ------------------------------------------------------------------
    // Việc định kỳ
    // ------------------------------------------------------------------

    @Override
    public void runPeriodicChecks() {
        long now = System.currentTimeMillis();
        List<MachineState> activeMachines = findActiveMachines();

        if (!activeMachines.isEmpty()) {
            Map<String, Double> medians = new LinkedHashMap<>();
            Map<String, Double> mads = new LinkedHashMap<>();
            computeRoomStats(activeMachines, medians, mads);
            messageSender.sendToAllStudents(new RoomStatsMessage(now, medians, mads));

            if (activeMachines.size() >= config.roomMinMachines) {
                for (MachineState state : activeMachines) {
                    evaluateRoomAlert(state, medians, mads);
                }
            }
        }

        // Chronos: gọi bất đồng bộ. Kết quả (hoặc map rỗng nếu lỗi/timeout) được xử lý khi có, không chặn vòng lặp này.
        if (MlDecision.usesChronos(mlMode)) {
            mlGateway.scoreAllMachines().thenAccept(this::applyChronosResults);
        }

        for (MachineState state : activeMachines) {
            evaluateMlAlert(state);
        }
    }

    /** Các máy sinh viên đang online và đã có số liệu. */
    private List<MachineState> findActiveMachines() {
        List<MachineState> active = new ArrayList<>();
        for (MachineState state : machines.values()) {
            if (state.isOnline() && state.getLastMetrics() != null) {
                active.add(state);
            }
        }
        return active;
    }

    private void computeRoomStats(List<MachineState> activeMachines, Map<String, Double> medians, Map<String, Double> mads) {
        for (int metric = 0; metric < Metrics.VECTOR_NAMES.length; metric++) {
            List<Double> values = new ArrayList<>();
            for (MachineState state : activeMachines) {
                values.add(state.getLastMetrics().toVector()[metric]);
            }
            double median = RoomStats.median(values);
            medians.put(Metrics.VECTOR_NAMES[metric], median);
            mads.put(Metrics.VECTOR_NAMES[metric], RoomStats.mad(values, median));
        }
    }

    /** So một máy với phòng; đặt hoặc xóa cảnh báo "ROOM". */
    private void evaluateRoomAlert(MachineState state, Map<String, Double> medians, Map<String, Double> mads) {
        if (rateController.getMode(state.machineId) == RateMode.BASELINE) {
            state.clearAlert("ROOM");
            return;
        }

        double[] vector = state.getLastMetrics().toVector();
        AlertLevel worstLevel = null;
        List<String> reasons = new ArrayList<>();
        for (int metric = 0; metric < ROOM_ALERT_DIMENSIONS; metric++) {
            String name = Metrics.VECTOR_NAMES[metric];
            RoomStats.Deviation deviation = RoomStats.check(vector[metric], medians.get(name), mads.get(name), config);
            if (deviation == null) {
                continue;
            }
            if (reasons.size() < MAX_REASONS_IN_ALERT) {
                reasons.add(name + " gấp " + String.format("%.1f", deviation.ratio) + " lần trung vị phòng");
            }
            if (worstLevel == null || deviation.level == AlertLevel.RED) {
                worstLevel = deviation.level;
            }
        }

        if (worstLevel == null) {
            state.clearAlert("ROOM");
            return;
        }
        String reason = String.join("; ", reasons);
        if (state.setAlert("ROOM", worstLevel, reason)) {
            sendAlert(state.machineId, worstLevel, reason);
        }
        rateController.raiseToHigh(state.machineId);
    }

    /** Nhận kết quả Chronos (có thể rỗng khi ML lỗi hoặc timeout: khi đó không làm gì). */
    private void applyChronosResults(Map<String, MlResult> results) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, MlResult> entry : results.entrySet()) {
            MachineState state = machines.get(entry.getKey());
            if (state == null) {
                continue;
            }
            state.updateChronos(entry.getValue().suspicious, entry.getValue().reason, now);
            evaluateMlAlert(state);
        }
    }

    /** Ghép Isolation Forest và Chronos theo ml.mode; đặt hoặc xóa cảnh báo "ML". */
    private void evaluateMlAlert(MachineState state) {
        if (mlMode == MlMode.NONE) {
            return;
        }
        if (!state.isOnline() || rateController.getMode(state.machineId) == RateMode.BASELINE) {
            state.clearAlert("ML");
            return;
        }

        long now = System.currentTimeMillis();
        long ifMaxAgeMillis = 3L * config.monitorIntervalSeconds * 1000;
        long chronosMaxAgeMillis = mlConfig.chronosValidSeconds * 1000L;
        boolean ifSuspicious = MlDecision.usesIsolationForest(mlMode) && state.isIfActive(now, ifMaxAgeMillis);
        boolean chronosSuspicious = MlDecision.usesChronos(mlMode) && state.isChronosActive(now, chronosMaxAgeMillis);

        if (!MlDecision.isSuspicious(mlMode, ifSuspicious, chronosSuspicious)) {
            state.clearAlert("ML");
            return;
        }

        List<String> parts = new ArrayList<>();
        if (ifSuspicious) {
            parts.add("Isolation Forest điểm " + String.format("%.2f", state.getIfScore()) + " vượt threshold");
        }
        if (chronosSuspicious) {
            parts.add("Chronos: " + state.getChronosReason());
        }
        String reason = "ML (" + mlMode + "): " + String.join("; ", parts);
        if (state.setAlert("ML", AlertLevel.YELLOW, reason)) {
            sendAlert(state.machineId, AlertLevel.YELLOW, reason);
        }
        rateController.raiseToHigh(state.machineId);
    }

    // ------------------------------------------------------------------
    // Vi phạm
    // ------------------------------------------------------------------

    /** Luật 1-4 là ĐỎ; mất focus quá ngưỡng là VÀNG (WARNING). */
    private AlertLevel levelOfViolation(ViolationMessage violation) {
        switch (violation.violationType) {
            case FOCUS_LOSS:
                return AlertLevel.YELLOW;
            case PROCESS_NOT_ALLOWED:
            case PROCESS_DENYLIST:
            case BLOCKED_IP:
            case USB_DEVICE:
            case NETWORK_CARD:
            default:
                return AlertLevel.RED;
        }
    }

    private String describeViolation(ViolationMessage violation) {
        String evidence = violation.evidence == null ? "" : violation.evidence;
        switch (violation.violationType) {
            case PROCESS_NOT_ALLOWED:
                return "Process lạ không nằm trong allowlist: " + evidence;
            case PROCESS_DENYLIST:
                return "Process bị cấm: " + evidence;
            case BLOCKED_IP:
                return "Kết nối tới IP bị chặn: " + evidence;
            case USB_DEVICE:
                return "Thiết bị USB mới: " + evidence;
            case NETWORK_CARD:
                return "Network card mới: " + evidence;
            case FOCUS_LOSS:
                return "Mất focus cửa sổ thi: " + evidence;
            default:
                return violation.violationType + ": " + evidence;
        }
    }

    private void saveViolation(MachineState state, ViolationMessage violation) {
        try {
            int shiftId = 0;
            ExamShift shift = shiftDao.findByCode(state.examCode);
            if (shift != null) {
                shiftId = shift.id;
            }
            long time = violation.time > 0 ? violation.time : System.currentTimeMillis();
            violationDao.insert(shiftId, state.studentId, violation.violationType.name(), violation.evidence, time);
        } catch (SQLException e) {
            // Không để lỗi ghi database làm mất cảnh báo realtime: ghi log rồi tiếp tục.
            System.out.println("[Monitor] Không lưu được vi phạm của " + state.machineId + ": " + e.getMessage());
        }
    }

    private void logMetrics(String machineId, long time, String kind, Metrics metrics) {
        try {
            metricsLogDao.insert(machineId, time, kind, rateController.getMode(machineId).name(), metrics);
        } catch (SQLException e) {
            System.out.println("[Monitor] Không lưu được số liệu của " + machineId + ": " + e.getMessage());
        }
    }

    private void sendAlert(String machineId, AlertLevel level, String reason) {
        System.out.println("[Alert] " + level + " " + machineId + ": " + reason);
        messageSender.sendToAllTeachers(new AlertMessage(machineId, level, reason));
    }

    /** Lấy trạng thái của máy, tạo mới nếu chưa có. Trả về null nếu không có session sinh viên tương ứng. */
    private MachineState getOrCreateState(String machineId) {
        MachineState existing = machines.get(machineId);
        if (existing != null) {
            return existing;
        }
        ClientSession session = sessionRegistry.findByMachineId(machineId);
        if (session == null || session.role != Role.STUDENT) {
            return null;
        }
        return machines.computeIfAbsent(machineId,
                id -> new MachineState(id, session.userId, session.fullName, session.examCode));
    }

    // ------------------------------------------------------------------
    // REQUEST của giáo viên
    // ------------------------------------------------------------------

    @Override
    public boolean handlesAction(String action) {
        return "LIST_MACHINES".equals(action) || "MACHINE_DETAIL".equals(action)
                || "SET_BASELINE".equals(action) || "EXPORT_METRICS_CSV".equals(action);
    }

    @Override
    public ResponseMessage handleTeacherRequest(RequestMessage request) {
        JsonObject data = request.data != null ? request.data : new JsonObject();
        try {
            JsonObject result;
            switch (request.action) {
                case "LIST_MACHINES":
                    result = listMachines(readString(data, "code"));
                    break;
                case "MACHINE_DETAIL":
                    result = machineDetail(readString(data, "machineId"));
                    break;
                case "SET_BASELINE":
                    result = setBaseline(data);
                    break;
                case "EXPORT_METRICS_CSV":
                    result = exportMetrics(readString(data, "machineId"), readString(data, "rateMode"));
                    break;
                default:
                    throw new IllegalArgumentException("Không hỗ trợ action: " + request.action);
            }
            return new ResponseMessage(request.requestId, true, result, null);
        } catch (IllegalArgumentException e) {
            return new ResponseMessage(request.requestId, false, null, e.getMessage());
        } catch (SQLException | IllegalStateException e) {
            e.printStackTrace();
            return new ResponseMessage(request.requestId, false, null, "Lỗi Server khi xử lý " + request.action);
        }
    }

    /** Danh sách máy. Có "code" thì liệt kê đủ thí sinh của ca (kể cả người chưa đăng nhập), không thì mọi máy đã biết. */
    private JsonObject listMachines(String shiftCode) throws SQLException {
        JsonArray rows = new JsonArray();

        if (shiftCode != null && !shiftCode.isBlank()) {
            ExamShift shift = shiftDao.findByCode(shiftCode.trim().toUpperCase());
            if (shift == null) {
                throw new IllegalArgumentException("Không có ca thi " + shiftCode);
            }
            for (CandidateRecord candidate : shiftDao.findCandidates(shift.id)) {
                rows.add(machineRow(candidate.username, candidate.fullName, machines.get(candidate.username)));
            }
        } else {
            for (MachineState state : machines.values()) {
                rows.add(machineRow(state.machineId, state.fullName, state));
            }
        }

        JsonObject result = new JsonObject();
        result.add("machines", rows);
        return result;
    }

    private JsonObject machineRow(String machineId, String fullName, MachineState state) {
        JsonObject row = new JsonObject();
        row.addProperty("machineId", machineId);
        row.addProperty("fullName", fullName);
        boolean online = state != null && state.isOnline();
        row.addProperty("online", online);

        AlertLevel level = state != null ? state.getCurrentLevel() : null;
        row.addProperty("level", level != null ? level.name() : "NONE");
        row.addProperty("reason", state != null ? state.getCurrentReason() : "");
        row.addProperty("rateMode", rateController.getMode(machineId).name());
        row.addProperty("lastSeen", state != null ? state.getLastSeenTime() : 0L);
        return row;
    }

    private JsonObject machineDetail(String machineId) throws SQLException {
        if (machineId == null || machineId.isBlank()) {
            throw new IllegalArgumentException("Thiếu machineId");
        }
        MachineState state = machines.get(machineId);
        if (state == null) {
            throw new IllegalArgumentException("Chưa có dữ liệu giám sát của máy " + machineId);
        }

        JsonObject result = machineRow(machineId, state.fullName, state);
        result.add("reasons", gson.toJsonTree(state.getReasonList()));
        result.addProperty("ifScore", state.getIfScore());
        result.addProperty("chronosReason", state.getChronosReason());

        Metrics metrics = state.getLastMetrics();
        if (metrics != null) {
            JsonObject metricsJson = new JsonObject();
            double[] vector = metrics.toVector();
            for (int i = 0; i < vector.length; i++) {
                metricsJson.addProperty(Metrics.VECTOR_NAMES[i], vector[i]);
            }
            result.add("metrics", metricsJson);
        }
        MetricsDetailMessage detail = state.getLastDetail();
        result.add("processNames", gson.toJsonTree(detail != null ? detail.processNames : new ArrayList<String>()));
        result.add("remoteAddresses", gson.toJsonTree(detail != null ? detail.remoteAddresses : new ArrayList<String>()));

        JsonArray violations = new JsonArray();
        for (var record : violationDao.findRecent(state.studentId, RECENT_VIOLATIONS_IN_DETAIL)) {
            JsonObject json = new JsonObject();
            json.addProperty("violationType", record.violationType);
            json.addProperty("evidence", record.evidence);
            json.addProperty("time", record.time);
            violations.add(json);
        }
        result.add("violations", violations);
        return result;
    }

    private JsonObject setBaseline(JsonObject data) {
        JsonElement enabled = data.get("enabled");
        if (enabled == null || !enabled.isJsonPrimitive()) {
            throw new IllegalArgumentException("Thiếu tham số \"enabled\" (true/false)");
        }
        if (enabled.getAsBoolean()) {
            rateController.startBaselineForAll();
        } else {
            rateController.stopBaselineForAll();
        }
        return new JsonObject();
    }

    private JsonObject exportMetrics(String machineId, String rateMode) throws SQLException {
        JsonObject result = new JsonObject();
        result.addProperty("csv", metricsLogDao.exportCsv(machineId, rateMode));
        return result;
    }

    private String readString(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    /** Dùng trong test: trạng thái của một máy. */
    public MachineState getStateForTest(String machineId) {
        return machines.get(machineId);
    }

    /** Dùng trong test: số máy đang được theo dõi. */
    public int countMachinesForTest() {
        return new HashMap<>(machines).size();
    }
}
