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
DAO (UserDao, QuestionDao)  →  SQLite
```

## 2. Module Maven

```text
source/pom.xml (parent)
├── common   Protocol + model dùng chung + Isolation Forest.    Phụ thuộc: Gson
├── server   TCP server, exam, monitor, ML gateway, SQLite.     Phụ thuộc: common, sqlite-jdbc
├── client   StudentMain, TeacherMain, giám sát client.         Phụ thuộc: common, OSHI
└── tools    Recorder, Simulator, ExperimentRunner.             Phụ thuộc: common, client
```

Quy tắc phụ thuộc: `server` và `client` **không** import nhau; chỉ chia sẻ qua `common`.
Vì vậy nơi nào cả hai phía cần hiểu cùng một thứ (message, model) thì thứ đó nằm trong `common`.

## 3. Package và người sở hữu

| Package | Nội dung | Owner |
|---|---|---|
| `exam.common.protocol` | `Message`, 24 class message, `MessageType`, `MessageCodec` | Nguoi1 |
| `exam.common.model` | `Metrics`, `Question`, `QuestionView`, `MonitoringRules`, enum, ... | Nguoi1 |
| `exam.common.ml` | `IsolationForest`, `MlResult` | Nguoi4 |
| `exam.server` | `ServerMain` (nối các thành phần) | Nguoi1 |
| `exam.server.api` | 6 interface: `MessageSender`, `SessionRegistry`, `ExamService`, `MonitorService`, `MlGateway`, `RateController` | Nguoi1 |
| `exam.server.net` | `TcpServer`, `ClientHandler`, `ClientConnection`, `ConnectionMessageSender`, `PeriodicJobs` | Nguoi1 |
| `exam.server.session` | `ClientSession`, `InMemorySessionRegistry` | Nguoi1 |
| `exam.server.rate` | `RateControllerImpl` | Nguoi1 |
| `exam.server.exam`, `exam.server.db` | `ExamServiceImpl`, `Database`, `UserDao`, `QuestionDao`, SQL | Nguoi2 |
| `exam.server.monitor` | `MonitorServiceImpl` | Nguoi3 |
| `exam.server.ml` | `MlGatewayImpl`, `MlConfig` | Nguoi4 |
| `exam.client.api` | `ServerLink`, `MetricsSource`, `RuleEngine`, `AnomalyScorer` (+ `IsolationForestScorer` của Nguoi4) | Nguoi1 |
| `exam.client.net` | `TcpServerLink` | Nguoi1 |
| `exam.client` | `StudentMain`, `TeacherMain` | Nguoi1 |
| `exam.client.student`, `exam.client.teacher` | `StudentFrame`, `TeacherFrame` | Nguoi2 |
| `exam.client.monitor`, `exam.client.teacher.monitor` | `OshiMetricsSource`, `RuleEngineImpl`, `MonitoringLoop`, `MonitorPanel` | Nguoi3 |
| `exam.tools` | `Recorder`, `Simulator`, `ExperimentRunner` | Nguoi4 |

## 4. Hợp đồng giữa các phần (interface)

Mỗi interface ghi rõ trong Javadoc **ai cài đặt, ai gọi**. Cần đổi interface → nhắn Nguoi1.

| Interface | Cài đặt (stub chạy được) | Ai gọi |
|---|---|---|
| `MessageSender` | Nguoi1: `ConnectionMessageSender` (chạy thật) | Nguoi2, Nguoi3, Nguoi4 |
| `SessionRegistry` | Nguoi1: `InMemorySessionRegistry` (chạy thật) | Nguoi1, Nguoi2, Nguoi3 |
| `ExamService` | Nguoi2: `ExamServiceImpl` (`login` thật, còn lại stub) | Nguoi1 (`ClientHandler`, `PeriodicJobs`) |
| `MonitorService` | Nguoi3: `MonitorServiceImpl` (stub) | Nguoi1 (`ClientHandler`, `PeriodicJobs`) |
| `MlGateway` | Nguoi4: `MlGatewayImpl` (stub, trả map rỗng) | Nguoi3 |
| `RateController` | Nguoi1: `RateControllerImpl` (stub, luôn NORMAL) | Nguoi3, Nguoi4, Nguoi1 |
| `ServerLink` | Nguoi1: `TcpServerLink` (chạy thật, chưa tự reconnect) | Nguoi1, Nguoi2, Nguoi3 |
| `MetricsSource` | Nguoi3: `OshiMetricsSource` (stub, số liệu rỗng) | Nguoi3, Nguoi1, Nguoi4 |
| `RuleEngine` | Nguoi3: `RuleEngineImpl` (stub, chỉ đếm focus) | Nguoi3, Nguoi2 (`StudentFrame` báo mất focus) |
| `AnomalyScorer` | Nguoi4: `IsolationForestScorer` (stub, score = 0) | Nguoi3 |

`ServerMain` là nơi duy nhất tạo các đối tượng và nối chúng (constructor injection thủ công, không dùng framework).
Muốn thay stub bằng bản thật: sửa class của mình, **không cần sửa `ServerMain`** vì constructor đã nhận đủ phụ thuộc.

## 5. Mô hình đồng thời (concurrency)

```text
main thread ──► TcpServer.start(): vòng lặp accept()
                    │ mỗi client mới
                    ├─► virtual thread #1: ClientHandler (đọc → xử lý → đọc ...)
                    ├─► virtual thread #2: ClientHandler
                    └─► ...
PeriodicJobs: 1 virtual thread, mỗi giây "gõ" các service làm việc định kỳ
Client: 1 virtual thread đọc socket (TcpServerLink) + 1 virtual thread gửi HEARTBEAT + thread giao diện Swing
```

- **Mỗi connection một virtual thread.** Code `ClientHandler` viết kiểu blocking đơn giản (`readLine()` rồi xử lý), nhưng Server vẫn giữ được rất nhiều connection mà không cần tự quản lý thread pool.
- Dữ liệu dùng chung (danh sách session) nằm trong `ConcurrentHashMap`; các field `ClientSession` thay đổi từ nhiều thread khai báo `volatile`.
- **Gửi** vào cùng một socket có thể đến từ nhiều thread (trả lời, ALERT, SET_RATE...) nên `ClientConnection.send()` là `synchronized` để hai message không chồng lên nhau.
- **Mỗi lần gọi DAO mở một connection SQLite riêng** rồi đóng (try-with-resources) nên các thread không dùng chung connection.
- Phía client, callback của `ServerLinkListener` chạy trên thread đọc socket; muốn đổi giao diện phải qua `SwingUtilities.invokeLater` (các Frame đã làm sẵn).

## 6. Các luồng chính

### LOGIN

```text
Client: serverLink.send(LOGIN{username,password,role,examCode})
Server: ClientHandler.handleLogin()
          → examService.login(username, password, role)  → UserDao → SQLite
          → sai: LOGIN_FAIL
          → đúng: sessionRegistry.createSession() (sinh token UUID) → LOGIN_OK{token,...}
          → sinh viên: bật timeout im lặng 30 giây
```

### HEARTBEAT

```text
Student (mỗi 10 giây): HEARTBEAT{seq, summary Metrics 8 chiều}
Server: handleHeartbeat() → ghi lastHeartbeatSeq/Time, log, monitorService.onMetricsSummary()
        → HEARTBEAT_ACK{seq}
Mất heartbeat 30 giây: SocketTimeoutException → markOffline() → monitorService.onMachineOffline()
```

### ANSWER và SUBMIT

```text
Student chọn đáp án → ANSWER{seq, questionId, choice}
Server: handleAnswer() → examService.saveAnswer() → ANSWER_ACK{seq, saved}
Student nộp → SUBMIT → examService.submit() (chấm điểm) → SUBMIT_OK{answeredCount}
Hết giờ: PeriodicJobs → examService.finishExpiredExams() → tự chốt bài → EXAM_END{TIME_UP}
```

### VIOLATION

```text
Student: MonitoringLoop → ruleEngine.checkForViolations() → VIOLATION{violationType, evidence, time}
Server: handleViolation() → monitorService.onViolation()
        → ghi bảng violations, chọn AlertLevel, ALERT{machineId, level, reason} tới giáo viên
        → rateController.raiseToHigh(machineId) → SET_RATE{HIGH, 2000} tới máy đó
```

### ML

```text
Client (ml.mode có IF): 5 phút đầu học (fit) Isolation Forest, sau đó score mỗi 10 giây,
        vượt threshold (max train + 0.05) 3 lần liên tiếp → báo lên Server
Server (ml.mode có CHRONOS), mỗi 10 giây trong monitorService.runPeriodicChecks():
        mlGateway.scoreAllMachines()  ── một batch 60 điểm của mọi máy ──► ml-service POST /score
        (bất đồng bộ, timeout 3 giây; lỗi → map rỗng, bỏ qua ML)
        ngoài q0.1-q0.9 đủ 3 lần liên tiếp → đáng ngờ
        ghép với kết quả IF theo ml.mode (NONE / IF / CHRONOS / BOTH_OR / BOTH_AND)
```

### ALERT

```text
MonitorService (luật vi phạm / ML đáng ngờ / lệch trung vị phòng)
   → messageSender.sendToAllTeachers(ALERT{machineId, level YELLOW|RED, reason})
   → TeacherMain.onMessage() → MonitorPanel.showAlert()
Cùng lúc: runPeriodicChecks() tính median + MAD cả phòng → ROOM_STATS xuống mọi sinh viên
```

## 7. Cấu hình và dữ liệu

| Đường dẫn | Nội dung |
|---|---|
| `config/server.properties` | Cổng TCP, đường dẫn SQLite |
| `config/ml.properties` | `ml.mode`, URL ml-service, timeout, thời gian client học |
| `data/exam.db` | SQLite (tự tạo, không commit) |
| `data/traces/` | Trace số liệu thu bằng `Recorder`/`Simulator` |
| `statics/results/` | Kết quả thực nghiệm |
| `report/` | Báo cáo |

Chạy Server và client từ **thư mục gốc repo** để các đường dẫn tương đối ở trên đúng.
