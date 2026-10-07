# ARCHITECTURE – Kiến trúc hệ thống

## 1. Sơ đồ tổng thể

```text
                 ┌───────────────┐
                 │ TeacherMain   │
                 └───────┬───────┘
                         │ TCP
                         │
┌───────────────┐        ▼
│ StudentMain   │ ──► Server
└───────────────┘        │
                         ├── Session
                         ├── Exam
                         ├── Monitor
                         ├── Rate
                         ├── ML Gateway
                         └── SQLite
                               │
                               ▼
                         ml-service
```

> Ghi chú: Server nói chuyện với `ml-service` (HTTP) qua **ML Gateway**; SQLite là file cục bộ do Server mở bằng JDBC.

Đường đi của một message (đọc code theo đúng thứ tự này):

```text
Client app
   │  serverLink.send(message)
   ▼
ServerLink (TcpServerLink)  ── messageCodec.encode() ──►  một dòng JSON
   │
   ▼  TCP socket
ClientConnection.readLine() ── messageCodec.decode() ──►  object Message
   │
   ▼
ClientHandler.handleMessage()  →  handleLogin() / handleHeartbeat() / handleAnswer() ...
   │
   ▼
Service (ExamService / MonitorService / ...)  →  examService.login(...)
   │
   ▼
DAO (UserDao, QuestionDao, AttemptDao, ...)  →  SQLite
```

## 2. Module Maven

```text
source/pom.xml (parent)
├── common   Protocol + model dùng chung + Isolation Forest + logic Chronos.   Phụ thuộc: Gson
├── server   TCP server, exam, monitor, ML gateway, SQLite.                    Phụ thuộc: common, sqlite-jdbc
├── client   StudentMain, TeacherMain, giám sát client, Swing.                 Phụ thuộc: common, OSHI
├── tools    Recorder, Simulator, ExperimentRunner.                            Phụ thuộc: common, client
└── e2e      Kịch bản end-to-end 20 bước (JUnit + runner chạy tiến trình thật). Phụ thuộc: common, client, server
ml-service/  Python FastAPI + Chronos-Bolt tiny (không thuộc Maven).
```

Quy tắc phụ thuộc: `server` và `client` **không** import nhau; chỉ chia sẻ qua `common`.
Vì vậy nơi nào cả hai phía cần hiểu cùng một thứ (message, model) thì thứ đó nằm trong `common`.
Ngoại lệ có chủ đích: module `e2e` phụ thuộc cả hai để dựng Server và client trong cùng một test; `common` không bao giờ import `client` hay `server`.

## 3. Package và người sở hữu

| Package | Nội dung | Owner |
|---|---|---|
| `exam.common.protocol` | `Message`, 24 class message, `MessageType`, `MessageCodec` | Nguoi1 |
| `exam.common.model` | `Metrics`, `Question`, `QuestionView`, `MonitoringRules`, `ExamShift`, enum, ... | Nguoi1 |
| `exam.common.ml` | `IsolationTree`, `IsolationForest`, `MlResult`, `MlDecision`, `ChronosCodec`, `ChronosJudge`, `Quantiles` | Nguoi4 |
| `exam.server` | `ServerMain`, `ServerApp` (nối các thành phần), `ServerConfig` | Nguoi1 |
| `exam.server.api` | 6 interface: `MessageSender`, `SessionRegistry`, `ExamService`, `MonitorService`, `MlGateway`, `RateController` | Nguoi1 |
| `exam.server.net` | `TcpServer`, `ClientHandler`, `ClientConnection`, `ConnectionMessageSender`, `PeriodicJobs` | Nguoi1 |
| `exam.server.session` | `ClientSession`, `InMemorySessionRegistry` | Nguoi1 |
| `exam.server.rate` | `RateControllerImpl` | Nguoi1 |
| `exam.server.exam` | `ExamServiceImpl`, `QuestionBankService`, `ExamCreationService`, `ExamPaper`, `AttemptService`, `ShiftService`, `TeacherRequestHandler`, `Csv` | Nguoi2 |
| `exam.server.db` | `Database`, `PasswordHasher`, `UserDao`, `QuestionDao`, `ExamDao`, `ShiftDao`, `AttemptDao`, `ViolationDao`, `MetricsLogDao`, SQL | Nguoi2 |
| `exam.server.monitor` | `MonitorServiceImpl`, `MachineState`, `RoomStats` | Nguoi3 |
| `exam.server.ml` | `MlGatewayImpl`, `MlConfig` | Nguoi4 |
| `exam.client.api` | `ServerLink`, `ServerLinkListener`, `ConnectionStatus`, `MetricsSource`, `RuleEngine`, `AnomalyScorer` | Nguoi1 |
| `exam.client.net` | `TcpServerLink` (tự nối lại) | Nguoi1 |
| `exam.client` | `StudentClient`, `TeacherClient` (logic không giao diện), `StudentMain`, `TeacherMain`, `ClientConfig` | Nguoi1 |
| `exam.client.student`, `exam.client.teacher` | `StudentFrame`, `StudentView`, `StudentActions`; `TeacherFrame`, `QuestionPanel`, `ExamPanel`, `ShiftPanel`, `ResultsPanel`, `TeacherView`, `TeacherUi` | Nguoi2 |
| `exam.client.monitor`, `exam.client.teacher.monitor` | `OshiMetricsSource`, `RuleEngineImpl`, `DomainResolver`, `MonitoringLoop`, `MonitorPanel` | Nguoi3 |
| `exam.client.ml` | `IsolationForestScorer` (adapter của `AnomalyScorer`), `AnomalyDetector` | Nguoi4 |
| `exam.tools` | `Recorder`, `Simulator`, `ExperimentEngine`, `ExperimentRunner`, `TraceCsv`, ... | Nguoi4 |
| `exam.e2e` | `ExamScenario`, `EndToEndRunner`, `ChaosProxy`, harness | Nguoi1 |
| `ml-service/` | `app.py`, `forecaster.py` | Nguoi4 |

## 4. Hợp đồng giữa các phần (interface)

Mỗi interface ghi rõ trong Javadoc **ai cài đặt, ai gọi**. Cần đổi interface → nhắn Nguoi1.

| Interface | Cài đặt | Ai gọi |
|---|---|---|
| `MessageSender` | Nguoi1: `ConnectionMessageSender` | Nguoi2, Nguoi3, Nguoi4 |
| `SessionRegistry` | Nguoi1: `InMemorySessionRegistry` | Nguoi1, Nguoi2, Nguoi3 |
| `ExamService` | Nguoi2: `ExamServiceImpl` | Nguoi1 (`ClientHandler`, `PeriodicJobs`) |
| `MonitorService` | Nguoi3: `MonitorServiceImpl` | Nguoi1 (`ClientHandler`, `PeriodicJobs`) |
| `MlGateway` | Nguoi4: `MlGatewayImpl` | Nguoi3 |
| `RateController` | Nguoi1: `RateControllerImpl` | Nguoi3, Nguoi4, Nguoi1 |
| `ServerLink` | Nguoi1: `TcpServerLink` | `StudentClient`, `TeacherClient` |
| `MetricsSource` | Nguoi3: `OshiMetricsSource` | `RuleEngineImpl`, `MonitoringLoop` |
| `RuleEngine` | Nguoi3: `RuleEngineImpl` | `MonitoringLoop`, `StudentFrame` (báo mất focus) |
| `AnomalyScorer` | Nguoi4: `IsolationForestScorer` | `AnomalyDetector` |

`ServerApp` là nơi duy nhất tạo các đối tượng Server và nối chúng (constructor injection thủ công, không dùng framework); `ServerMain` chỉ đọc cấu hình rồi gọi nó.
Phía client, `StudentMain` / `TeacherMain` tạo `TcpServerLink` + `StudentClient` / `TeacherClient` + giao diện.

## 5. Mô hình đồng thời (concurrency)

```text
main thread ──► TcpServer.start(): vòng lặp accept()
                    │ mỗi client mới
                    ├─► virtual thread #1: ClientHandler (đọc → xử lý → đọc ...)
                    ├─► virtual thread #2: ClientHandler
                    └─► ...
PeriodicJobs: 1 virtual thread, mỗi giây "gõ" các service làm việc định kỳ
MlGatewayImpl: gọi ml-service bằng CompletableFuture bất đồng bộ (không chặn vòng giám sát)
Client: 1 thread đọc socket (TcpServerLink) + 1 virtual thread heartbeat/lấy mẫu (StudentClient/MonitoringLoop)
        + 1 thread gửi METRICS_DETAIL khi HIGH/BASELINE + thread giao diện Swing
```

- **Mỗi connection một virtual thread.** Code `ClientHandler` viết kiểu blocking đơn giản (`readLine()` rồi xử lý), nhưng Server vẫn giữ được rất nhiều connection mà không cần tự quản lý thread pool.
- Dữ liệu dùng chung (danh sách session, trạng thái từng máy) nằm trong `ConcurrentHashMap`; các field `ClientSession` thay đổi từ nhiều thread khai báo `volatile`.
- **Gửi** vào cùng một socket có thể đến từ nhiều thread (trả lời, ALERT, SET_RATE...) nên `ClientConnection.send()` là `synchronized` để hai message không chồng lên nhau.
- **Mỗi lần gọi DAO mở một connection SQLite riêng** rồi đóng (try-with-resources); SQLite bật WAL + `busy_timeout` để nhiều thread cùng ghi không bị khóa.
- **Nộp bài và tự chốt khi hết giờ dùng chung một khóa** (`AttemptService`) nên một bài không bao giờ bị chấm hai lần.
- `MlGatewayImpl` có cờ "đang gọi dở": nếu lần gọi trước chưa xong thì chu kỳ sau bỏ qua, không dồn request.
- Phía client, callback của `ServerLinkListener` chạy trên thread đọc socket; muốn đổi giao diện phải qua `SwingUtilities.invokeLater` (các Frame đã làm sẵn).

## 6. Các luồng chính

### LOGIN

```text
Client: serverLink.send(LOGIN{username,password,role,examCode})
Server: ClientHandler.handleLogin()
          → kiểm tra định dạng (độ dài, thiếu field), chống đoán mật khẩu (login.max.failures)
          → examService.login(username, password, role)  → UserDao → so hash PBKDF2
          → sinh viên: ca có tồn tại? chưa kết thúc? có trong danh sách thí sinh? không đăng nhập ở máy khác?
          → sai: LOGIN_FAIL
          → đúng: sessionRegistry.createSession() (sinh token UUID) → LOGIN_OK{token,...}
          → sinh viên: bật timeout im lặng 30 giây, báo MonitorService/RateController máy online,
                       nếu ca đang RUNNING gửi RULES_CONFIG + EXAM_START (kèm đáp án đã lưu)
```

### HEARTBEAT, mất kết nối và RECONNECT

```text
Student (mỗi 10 giây): HEARTBEAT{seq, summary Metrics 8 chiều, anomalyScore, anomalous}
Server: handleHeartbeat() → ghi lastHeartbeatSeq/Time, monitorService.onHeartbeat() → HEARTBEAT_ACK{seq}
Mất heartbeat 30 giây: SocketTimeoutException → markOffline() → monitorService.onMachineOffline() (ALERT vàng "OFFLINE")

Student nối lại: RECONNECT{token, lastAnswerSeq}
Server: tìm session theo token (không tạo session mới) → còn trong reconnect.window? → RECONNECT_OK{endTimeServer, answers}
        → nếu đã nộp / ca đã kết thúc trong lúc mất mạng: gửi EXAM_END ngay để client khóa bài
Client (StudentClient): gửi lại các ANSWER chưa được ANSWER_ACK; INVALID_TOKEN/TOKEN_EXPIRED → LOGIN lại
Mạng "chết im" (socket không báo lỗi): Server im lặng quá 3 chu kỳ heartbeat → client tự reset connection rồi RECONNECT
```

### Thi: ANSWER, SUBMIT, hết giờ

```text
Giáo viên START_SHIFT (hoặc tới giờ hẹn): ShiftService đặt endTimeServer = giờ Server + thời lượng,
        gửi RULES_CONFIG + EXAM_START (đề đã trộn riêng bằng seed của từng sinh viên) cho mọi sinh viên đã đăng nhập
Student chọn đáp án → ANSWER{seq, questionId, choice}
Server: handleAnswer() → examService.saveAnswer() (kiểm tra giờ, ca, đã nộp chưa) → ANSWER_ACK{seq, saved}
Student nộp → SUBMIT → examService.submit() (Server tự chấm, thang 10) → SUBMIT_OK{answeredCount}
Hết giờ: PeriodicJobs → examService.finishExpiredExams() → tự chốt bài → EXAM_END{TIME_UP}
Giáo viên END_SHIFT: tự chốt bài mọi sinh viên → EXAM_END{TEACHER_ENDED}
TIME_SYNC mỗi 30 giây; client tính chênh lệch với đồng hồ Server nên đếm ngược không phụ thuộc đồng hồ máy sinh viên.
```

### VIOLATION và giám sát client

```text
Student: MonitoringLoop (mỗi chu kỳ heartbeat) → ruleEngine.checkForViolations() → VIOLATION{violationType, evidence, time}
         5 luật: process allow/deny (mới ngoài allowlist phải thấy 2 lần liên tiếp), IP bị chặn, USB mới, network card mới, focus
Server: handleViolation() → monitorService.onViolation()
        → ghi bảng violations, chọn AlertLevel (luật 1-4 ĐỎ, focus VÀNG), ALERT{machineId, level, reason} tới giáo viên
        → rateController.raiseToHigh(machineId) → SET_RATE{HIGH, 2000} tới máy đó
        → HIGH: client gửi METRICS_DETAIL mỗi 2 giây; 60 giây yên → tự về NORMAL
```

### ML

```text
Client (ml.mode có IF): 5 phút đầu học (fit) Isolation Forest, sau đó score mỗi chu kỳ,
        vượt threshold (max train + 0.05) 3 lần liên tiếp → anomalous=true trong HEARTBEAT / METRICS_DETAIL
Server (ml.mode có CHRONOS), mỗi 10 giây trong monitorService.runPeriodicChecks():
        mlGateway.scoreAllMachines()  ── một batch 60 điểm của mọi máy ──► ml-service POST /score
        (bất đồng bộ, timeout 3 giây; lỗi/503/NaN → map rỗng, bỏ qua ML, hệ thống vẫn chạy)
        ChronosJudge: ngoài q0.1-q0.9 đủ 3 lần liên tiếp → đáng ngờ (6 metric, không gồm process và focus)
        MlDecision ghép kết quả IF và Chronos theo ml.mode (NONE / IF / CHRONOS / BOTH_OR / BOTH_AND)
        → đáng ngờ: ALERT vàng "ML (<mode>): ..." + máy sang HIGH
BASELINE (giáo viên bật SET_BASELINE): mọi máy gửi METRICS_DETAIL mỗi 1 giây, tắt cả IF lẫn Chronos;
        dữ liệu ghi vào bảng metrics_log và xuất bằng EXPORT_METRICS_CSV làm trace cho exam.tools.
```

### ALERT

```text
MonitorService (luật vi phạm / ML đáng ngờ / lệch trung vị phòng / offline)
   → messageSender.sendToAllTeachers(ALERT{machineId, level YELLOW|RED, reason})
   → TeacherClient.onMessage() → MonitorPanel (bảng Machine / Student / Online / Alert / Reason, đổi màu dòng)
Cùng lúc: runPeriodicChecks() tính median + MAD cả phòng → ROOM_STATS xuống mọi sinh viên
Giáo viên chọn một máy → REQUEST MACHINE_DETAIL (metrics, process, IP, vi phạm gần đây, điểm IF, lý do Chronos)
Giáo viên gửi NOTICE{machineId|ALL} → Server chuyển tới sinh viên (máy không online → ERROR MACHINE_OFFLINE)
```

## 7. Bảo mật

| Vấn đề | Cách xử lý |
|---|---|
| Mật khẩu | Hash PBKDF2 + salt ngẫu nhiên (`PasswordHasher`); không có mật khẩu rõ trong database hay log |
| Phân quyền | `ClientHandler.requireRole`: sinh viên không gọi được `REQUEST`/`NOTICE`, giáo viên không gửi `ANSWER`...; sai → `ERROR FORBIDDEN` |
| Không tin client | Điểm, đáp án đúng, giờ kết thúc đều do Server giữ và tính; `ANSWER` bị từ chối nếu hết giờ, ca đã kết thúc hoặc đã nộp; client không bao giờ nhận đáp án đúng |
| Đầu vào | Giới hạn độ dài dòng (`message.max.bytes`), username, mật khẩu, mã ca, NOTICE; JSON sai → `BAD_MESSAGE` nhưng không ngắt connection; mọi truy vấn SQL dùng `PreparedStatement` |
| Đoán mật khẩu | Ngắt connection sau `login.max.failures` lần sai |
| Tài khoản | Một sinh viên chỉ đăng nhập một máy; muốn vào lại dùng `RECONNECT` bằng token |
| Token | UUID ngẫu nhiên, hết hạn sau `reconnect.window.seconds` khi offline |
| Lỗi nội bộ | Chi tiết (stack trace) chỉ ghi log Server; client chỉ nhận thông báo chung |
| Đường truyền | **Chưa mã hóa** (TCP thuần theo quyết định của nhóm); nếu triển khai thật nên bọc bằng TLS hoặc chạy trong mạng LAN phòng thi |

## 8. Cấu hình và dữ liệu

| Đường dẫn | Nội dung |
|---|---|
| `config/server.properties` | Cổng, SQLite, heartbeat/reconnect, đồng hồ thi, thống kê phòng (median/MAD), rate control |
| `config/ml.properties` | `ml.mode`, URL ml-service, timeout, cửa sổ Chronos, tham số Isolation Forest |
| `config/client.properties` | Địa chỉ Server, chu kỳ heartbeat, nối lại |
| `data/exam.db` | SQLite (tự tạo, không commit) |
| `data/traces/` | Trace số liệu thu bằng `Recorder`/`Simulator` (`sim.csv` là trace mô phỏng) |
| `statics/results/` | Kết quả thực nghiệm kèm `README.md` giải thích |
| `report/` | Báo cáo |

Chạy Server và client từ **thư mục gốc repo** để các đường dẫn tương đối ở trên đúng.

## 9. Kiểm thử

| Mức | Ở đâu | Nội dung |
|---|---|---|
| Unit | `common`, `server`, `client`, `tools` | Codec, Isolation Forest, Chronos judge, DAO, hash, CSV, trộn đề + chấm điểm, `RoomStats`, `RateController`, `MlGateway` (HTTP giả), `RuleEngine` (nguồn số liệu giả), `AnomalyDetector`... |
| Integration (mạng) | `ServerNetworkTest`, `StudentClientTest`, `TeacherClientTest` | Server thật trên cổng ngẫu nhiên + client thật: đăng nhập, sai vai trò, JSON hỏng, heartbeat timeout, reconnect, ngắt mạng |
| End-to-end | `source/e2e` | `ExamScenario` 20 bước: (1) `EndToEndTest` chạy trong JUnit với Server trong tiến trình và ml-service giả; (2) `EndToEndRunner` chạy **ml-service (uvicorn) và Server thành tiến trình thật**, dùng OSHI thật và một process `anydesk` thật, dừng/tiếp tục ml-service bằng `SIGSTOP/SIGCONT` để thử timeout ML |
| ml-service | `ml-service/tests` (pytest) | `/health`, `/score` batch, 503 khi chưa nạp model, NaN, độ dài chuỗi khác nhau |
