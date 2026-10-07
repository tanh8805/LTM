# PROTOCOL – Giao thức giữa Client và Server

> Tài liệu này mô tả **mọi message** trong hệ thống. Code tương ứng nằm ở `source/common/src/main/java/exam/common/protocol/`.
> Bảng message ở mục 2 lấy tên field, kiểu và mô tả từ các class Java, còn ví dụ JSON được in ra bằng `MessageCodec.encode()` (không viết tay), nên khớp với code.
> Muốn đổi/thêm message: cập nhật class Java, `MessageCodec`, test và tài liệu này **cùng một lúc** (xem `CONTRIBUTING.md`).

## 1. Quy ước chung

| Mục | Quy ước |
|---|---|
| Truyền tải | TCP socket thuần. Server mặc định cổng `5000` (`config/server.properties`) |
| Khung message | **Mỗi message là MỘT dòng JSON**, kết thúc bằng ký tự xuống dòng `\n` (UTF-8). Không có xuống dòng bên trong JSON. Dòng dài hơn `message.max.bytes` (mặc định 2 MB) thì Server ngắt kết nối |
| Nhận diện loại | Field `"type"` (luôn được ghi đầu tiên). `MessageCodec.decode()` đọc `type` để tạo đúng class Java |
| Thời gian | Epoch **milli giây** (kiểu `long`). Các giờ có hậu tố `Server` (`serverTime`, `endTimeServer`) là theo **đồng hồ Server**; sinh viên không được tin đồng hồ máy mình |
| `seq` | Số thứ tự tăng dần do client đặt (HEARTBEAT, ANSWER, ...). Server trả lại `seq` trong ACK để client biết message nào đã được nhận |
| Field thiếu | Field `null` không được ghi ra JSON; khi đọc, field thiếu là `null` (hoặc 0/false với kiểu số/bool) |
| Message lỗi | Dòng không phải JSON, thiếu `type` hoặc `type` lạ: Server **không** ngắt kết nối mà trả `ERROR` (`code = BAD_MESSAGE`) và đọc tiếp dòng sau |
| Chưa đăng nhập | Mọi message khác ngoài `LOGIN`/`RECONNECT` bị trả `ERROR` (`NOT_LOGGED_IN`) |
| Sai vai trò | Sinh viên gửi message của giáo viên (`REQUEST`, `NOTICE`) hoặc ngược lại (`ANSWER`, `SUBMIT`, `VIOLATION`, `METRICS_DETAIL`) bị trả `ERROR` (`FORBIDDEN`) |
| Chống đoán mật khẩu | `LOGIN` sai `login.max.failures` lần (mặc định 5) trên cùng một connection thì Server ngắt kết nối |
| Mật khẩu | Lưu trong SQLite dạng hash PBKDF2 có salt. Mật khẩu chỉ đi qua `LOGIN` (TCP thuần, chưa mã hóa đường truyền; xem `docs/ARCHITECTURE.md` mục Bảo mật) |
| Phát hiện mất kết nối | Sinh viên gửi `HEARTBEAT` mỗi 10 giây. Server không nhận được gì trong **30 giây** (`heartbeat.timeout.seconds`) thì coi sinh viên offline (session vẫn giữ lại để `RECONNECT`) |
| Không tin client | Server không nhận điểm, đáp án đúng hay giờ kết thúc do client gửi. Client chỉ gửi "câu nào, chọn vị trí nào"; Server tự kiểm tra giờ, ca thi, đã nộp chưa và tự chấm |

> **Khác với yêu cầu gốc (đã được nhóm đồng ý):** message `VIOLATION` dùng field **`violationType`** thay vì `type`,
> vì `type` đã dùng để phân loại message (JSON không thể có hai key `type` trong cùng một object).

## 2. Bảng message

Cột "Hướng": C → S là Client gửi Server; S → C là Server gửi Client.

| Message | Hướng | Fields | Ý nghĩa | Ví dụ JSON |
|---|---|---|---|---|
| `LOGIN` | C → S | `username` (String) – Giáo viên: username. Sinh viên: mã sinh viên<br>`password` (String)<br>`role` (Role) – TEACHER hoặc STUDENT<br>`examCode` (String) – Mã ca thi (chỉ sinh viên, giáo viên để null) | Đăng nhập. Sinh viên gửi mã SV + mật khẩu + mã ca thi; giáo viên gửi username + mật khẩu. | `{"type":"LOGIN","username":"SV001","password":"123456","role":"STUDENT","examCode":"CA001"}` |
| `LOGIN_OK` | S → C | `token` (String) – Mã dùng để RECONNECT khi mất kết nối<br>`userId` (int)<br>`fullName` (String)<br>`role` (Role)<br>`serverTime` (long) – Giờ Server lúc trả lời (epoch milli giây) | Đăng nhập thành công. token dùng cho RECONNECT. | `{"type":"LOGIN_OK","token":"3f2c-token","userId":1,"fullName":"Nguyễn Văn An","role":"STUDENT","serverTime":1760000000000}` |
| `LOGIN_FAIL` | S → C | `reason` (String) – Ví dụ: sai mật khẩu | Đăng nhập thất bại. | `{"type":"LOGIN_FAIL","reason":"Sai tài khoản hoặc mật khẩu"}` |
| `RECONNECT` | C → S | `token` (String)<br>`lastAnswerSeq` (int) – seq của ANSWER cuối cùng đã được ANSWER_ACK | Nối lại sau khi mất kết nối, bằng token đã nhận lúc LOGIN_OK. | `{"type":"RECONNECT","token":"3f2c-token","lastAnswerSeq":5}` |
| `RECONNECT_OK` | S → C | `endTimeServer` (long) – Giờ hết bài (epoch milli giây, đồng hồ Server)<br>`answers` (Map<Integer, Integer>) – questionId -> choice các câu đã trả lời<br>`serverTime` (long) | Nối lại thành công, kèm dữ liệu để khôi phục bài làm. | `{"type":"RECONNECT_OK","endTimeServer":1760003600000,"answers":{"11":2,"12":0},"serverTime":1760000000000}` |
| `HEARTBEAT` | C → S | `seq` (int) – Số thứ tự tăng dần 1, 2, 3, ...<br>`summary` (Metrics) – Vector 8 chiều (chế độ NORMAL)<br>`anomalyScore` (double) – Điểm Isolation Forest của client (0..1). 0 nếu client không chạy Isolation Forest.<br>`anomalous` (bool) – true khi Isolation Forest đã vượt threshold đủ số lần liên tiếp. | Tín hiệu "tôi còn sống" mỗi 10 giây, kèm summary số liệu giám sát. | `{"type":"HEARTBEAT","seq":3,"summary":{"kbSent":12.5,"kbReceived":30.0,"connectionCount":8,"distinctDestinationCount":4,"cpuPercent":35.5,"ramPercent":60.0,"processCount":120,"focusLostCount":2},"anomalyScore":0.54,"anomalous":false}` |
| `HEARTBEAT_ACK` | S → C | `seq` (int) – seq của HEARTBEAT được xác nhận<br>`serverTime` (long) | Server xác nhận đã nhận HEARTBEAT. | `{"type":"HEARTBEAT_ACK","seq":3,"serverTime":1760000030000}` |
| `REQUEST` | C → S | `requestId` (String) – Client tự sinh, dùng để ghép với RESPONSE<br>`action` (String) – Ví dụ: LIST_QUESTIONS<br>`data` (object) – Tham số của action | Yêu cầu chung (chủ yếu Teacher: quản lý câu hỏi, đề, ca thi, xem điểm). Server trả RESPONSE cùng requestId. | `{"type":"REQUEST","requestId":"req-1","action":"LIST_RESULTS","data":{"code":"CA001"}}` |
| `RESPONSE` | S → C | `requestId` (String) – Giống requestId của REQUEST<br>`ok` (bool)<br>`data` (object) – Kết quả khi ok = true<br>`error` (String) – Lý do lỗi khi ok = false | Kết quả của một REQUEST. | `{"type":"RESPONSE","requestId":"req-1","ok":true,"data":{"csv":"studentCode,fullName,score\nSV001,Nguyễn Văn An,7.5"}}` |
| `EXAM_START` | S → C | `questions` (List<QuestionView>) – Câu hỏi và đáp án đã trộn, không có đáp án đúng<br>`endTimeServer` (long) – Giờ hết bài (epoch milli giây, đồng hồ Server)<br>`answers` (Map<Integer, Integer>) – Các đáp án đã lưu (questionId -> choice) để khôi phục khi đăng nhập lại giữa ca. Có thể rỗng.<br>`title` (String) – Tên đề thi, chỉ để hiển thị. | Bắt đầu làm bài: gửi đề đã trộn riêng cho sinh viên này. | `{"type":"EXAM_START","questions":[{"questionId":11,"content":"Thủ đô của Việt Nam là thành phố nào?","options":["Huế","Hà Nội","Đà Nẵng","Cần Thơ"]}],"endTimeServer":1760003600000,"answers":{"11":2,"12":0},"title":"Kiểm tra giữa kỳ"}` |
| `TIME_SYNC` | S → C | `serverTime` (long)<br>`remainingSeconds` (long) – Số giây còn lại đến endTimeServer | Server gửi định kỳ để đồng hồ đếm ngược của client khớp đồng hồ Server. | `{"type":"TIME_SYNC","serverTime":1760000060000,"remainingSeconds":3540}` |
| `ANSWER` | C → S | `seq` (int) – Số thứ tự tăng dần của ANSWER<br>`questionId` (int)<br>`choice` (int) – Vị trí 0..3 trong options ĐÃ TRỘN của QuestionView | Gửi đáp án ngay khi sinh viên chọn. | `{"type":"ANSWER","seq":6,"questionId":11,"choice":1}` |
| `ANSWER_ACK` | S → C | `seq` (int) – seq của ANSWER được xác nhận<br>`questionId` (int)<br>`saved` (bool) | Server xác nhận đã lưu đáp án. | `{"type":"ANSWER_ACK","seq":6,"questionId":11,"saved":true}` |
| `SUBMIT` | C → S | `seq` (int) – Số thứ tự message của sinh viên (tiếp sau ANSWER cuối) | Sinh viên nộp bài. | `{"type":"SUBMIT","seq":7}` |
| `SUBMIT_OK` | S → C | `answeredCount` (int) – Số câu đã trả lời | Server đã nhận bài nộp. | `{"type":"SUBMIT_OK","answeredCount":28}` |
| `EXAM_END` | S → C | `reason` (String) – TIME_UP, TEACHER_ENDED hoặc SUBMITTED | Ca thi kết thúc (hết giờ hoặc giáo viên kết thúc). Server tự chốt bài. | `{"type":"EXAM_END","reason":"TIME_UP"}` |
| `RULES_CONFIG` | S → C | `rules` (MonitoringRules)<br>`mlMode` (MlMode) – Client biết có phải chạy Isolation Forest không | Server gửi luật giám sát cho client khi bắt đầu ca thi. | `{"type":"RULES_CONFIG","rules":{"processAllowlist":[],"processDenylist":["Zalo","Telegram","Messenger","TeamViewer","AnyDesk"],"blockedDomains":["chatgpt.com","api.openai.com","gemini.google.com"],"focusLossThreshold":3,"metricsIntervalSeconds":10,"ifTrainingSeconds":300,"ifThresholdMargin":0.05,"ifConsecutiveRequired":3},"mlMode":"NONE"}` |
| `METRICS_DETAIL` | C → S | `seq` (int)<br>`time` (long) – Giờ client lúc đo (epoch milli giây)<br>`metrics` (Metrics)<br>`processNames` (List<String>) – Tên các process đang chạy<br>`remoteAddresses` (List<String>) – Các địa chỉ đích đang kết nối<br>`anomalyScore` (double) – Điểm Isolation Forest của client (0..1). 0 nếu client không chạy Isolation Forest.<br>`anomalous` (bool) – true khi Isolation Forest đã vượt threshold đủ số lần liên tiếp. | Số liệu chi tiết (chế độ HIGH mỗi 2 giây, BASELINE mỗi 1 giây). | `{"type":"METRICS_DETAIL","seq":12,"time":1760000120000,"metrics":{"kbSent":12.5,"kbReceived":30.0,"connectionCount":8,"distinctDestinationCount":4,"cpuPercent":35.5,"ramPercent":60.0,"processCount":120,"focusLostCount":2},"processNames":["java","chrome"],"remoteAddresses":["142.250.1.1:443"],"anomalyScore":0.71,"anomalous":true}` |
| `VIOLATION` | C → S | `violationType` (ViolationType) – Loại vi phạm<br>`evidence` (String) – Bằng chứng, ví dụ tên process hoặc IP<br>`time` (long) – Giờ client phát hiện (epoch milli giây) | Client báo một vi phạm luật giám sát. LƯU Ý: tên field là violationType (không phải type) vì "type" đã dùng để phân loại message. | `{"type":"VIOLATION","violationType":"PROCESS_DENYLIST","evidence":"Zalo.exe","time":1760000150000}` |
| `SET_RATE` | S → C | `mode` (RateMode) – NORMAL, HIGH hoặc BASELINE<br>`intervalMs` (int) – Chu kỳ gửi, đơn vị milli giây | Server đổi tần suất gửi số liệu của client. | `{"type":"SET_RATE","mode":"HIGH","intervalMs":2000}` |
| `ROOM_STATS` | S → C | `time` (long)<br>`medians` (Map<String, Double>) – tên metric -> trung vị cả phòng<br>`mads` (Map<String, Double>) – tên metric -> MAD cả phòng | Trung vị và MAD của từng metric trên cả phòng, để client tự so sánh. | `{"type":"ROOM_STATS","time":1760000180000,"medians":{"kbSent":10.0,"cpuPercent":22.0},"mads":{"kbSent":2.5,"cpuPercent":4.0}}` |
| `ALERT` | S → C | `machineId` (String) – Mã máy = mã sinh viên<br>`level` (AlertLevel) – YELLOW hoặc RED<br>`reason` (String) – Ví dụ: kbSent gấp 5.2 lần trung vị phòng | Server báo cảnh báo của một máy cho giáo viên. | `{"type":"ALERT","machineId":"SV003","level":"YELLOW","reason":"kbSent gấp 5.2 lần trung vị phòng"}` |
| `NOTICE` | hai chiều | `target` (String) – machineId của một máy, hoặc "ALL" cho cả phòng<br>`text` (String) | Thông báo của giáo viên. Giáo viên gửi lên Server, Server chuyển tới sinh viên. | `{"type":"NOTICE","target":"ALL","text":"Còn 5 phút nữa hết giờ"}` |
| `ERROR` | S → C | `code` (String) – Xem bảng mã lỗi trong docs/PROTOCOL.md mục 5, ví dụ BAD_MESSAGE, NOT_LOGGED_IN, FORBIDDEN<br>`message` (String) | Server báo lỗi cho client (message sai định dạng, chưa đăng nhập, sai vai trò, token hỏng, ...). | `{"type":"ERROR","code":"NOT_LOGGED_IN","message":"Hãy gửi LOGIN trước"}` |

## 3. Kiểu dữ liệu lồng nhau

### `Metrics` – vector 8 chiều (mỗi 10 giây)

| # | Field | Ý nghĩa | Chronos chấm? |
|---|---|---|---|
| 1 | `kbSent` | **KB/giây** gửi, trung bình kể từ lần đo trước | Có |
| 2 | `kbReceived` | **KB/giây** nhận, trung bình kể từ lần đo trước | Có |
| 3 | `connectionCount` | Số kết nối mạng | Có |
| 4 | `distinctDestinationCount` | Số địa chỉ đích khác nhau | Có |
| 5 | `cpuPercent` | % CPU | Có |
| 6 | `ramPercent` | % RAM | Có |
| 7 | `processCount` | Số tiến trình | Không (chỉ Isolation Forest) |
| 8 | `focusLostCount` | Số lần mất focus cửa sổ thi | Không (chỉ Isolation Forest) |

`kbSent` / `kbReceived` là **tốc độ** (KB/giây), không phải tổng, để số liệu so sánh được dù chu kỳ đo là 10 s (NORMAL), 2 s (HIGH) hay 1 s (BASELINE).

### `QuestionView` – câu hỏi gửi cho sinh viên

`{"questionId":11,"content":"...","options":["A","B","C","D"]}`. **Không có đáp án đúng.**
Thứ tự câu và thứ tự `options` đã được Server trộn riêng cho từng sinh viên (seed lưu trong bảng `shift_students`, trộn lại được khi đăng nhập lại).
Vì vậy `choice` trong `ANSWER` là vị trí (0..3) trong `options` **đã trộn**; Server tự quy đổi khi chấm.

### `MonitoringRules` – luật giám sát (trong `RULES_CONFIG`)

| Field | Ý nghĩa | Mặc định |
|---|---|---|
| `processAllowlist` | Process được phép thêm ngoài danh sách chụp lúc bắt đầu | rỗng |
| `processDenylist` | Process bị cấm (khớp theo tên chứa chuỗi, không phân biệt hoa thường, bỏ đuôi `.exe`) | Zalo, Telegram, Messenger, TeamViewer, AnyDesk |
| `blockedDomains` | Domain bị chặn (client resolve ra IP lúc bắt đầu) | chatgpt.com, api.openai.com, gemini.google.com |
| `focusLossThreshold` | Mất focus quá số lần này thì báo `FOCUS_LOSS` | 3 |
| `metricsIntervalSeconds` | Chu kỳ đo ở chế độ NORMAL | 10 |
| `ifTrainingSeconds` | Isolation Forest học bao lâu đầu ca | 300 |
| `ifThresholdMargin` | Threshold = max điểm lúc học + margin | 0.05 |
| `ifConsecutiveRequired` | Số lần liên tiếp vượt threshold mới báo | 3 |

Giáo viên có thể gửi `rules` riêng trong `CREATE_SHIFT`; không gửi thì dùng `MonitoringRules.createDefault()`.

## 4. Giá trị của các enum

| Enum | Giá trị |
|---|---|
| `Role` | `TEACHER`, `STUDENT` |
| `RateMode` | `NORMAL` (10 s, summary trong HEARTBEAT), `HIGH` (2 s, METRICS_DETAIL), `BASELINE` (1 s, METRICS_DETAIL, không ML) |
| `MlMode` | `NONE`, `IF`, `CHRONOS`, `BOTH_OR`, `BOTH_AND` |
| `AlertLevel` | `YELLOW` (nghi ngờ), `RED` (vi phạm rõ ràng) |
| `ViolationType` | `PROCESS_NOT_ALLOWED`, `PROCESS_DENYLIST`, `BLOCKED_IP`, `USB_DEVICE`, `NETWORK_CARD`, `FOCUS_LOSS` |
| Trạng thái ca thi | `CREATED` → `RUNNING` → `ENDED` |
| Lý do `EXAM_END` | `TIME_UP` (Server hết giờ), `TEACHER_ENDED` (giáo viên kết thúc), `SUBMITTED` (sinh viên đã nộp, báo khi nối lại) |

## 5. Mã lỗi trong `ERROR`

| `code` | Khi nào |
|---|---|
| `BAD_MESSAGE` | Dòng không phải JSON hợp lệ, thiếu `type`, `type` lạ, hoặc thiếu field bắt buộc (`METRICS_DETAIL` không có `metrics`, `VIOLATION` không có `violationType`, `NOTICE` thiếu `target`/`text` hoặc quá dài) |
| `NOT_LOGGED_IN` | Gửi message cần đăng nhập khi chưa `LOGIN` |
| `ALREADY_LOGGED_IN` | Gửi `LOGIN`/`RECONNECT` lần hai trên cùng connection |
| `FORBIDDEN` | Sai vai trò (ví dụ sinh viên gửi `NOTICE` hoặc `REQUEST`) |
| `INVALID_TOKEN` | `RECONNECT` với token không tồn tại: hãy `LOGIN` lại |
| `TOKEN_EXPIRED` | `RECONNECT` khi session đã offline quá `reconnect.window.seconds` (mặc định 600 s): hãy `LOGIN` lại |
| `MACHINE_OFFLINE` | `NOTICE` gửi tới một máy không online |
| `UNEXPECTED_MESSAGE` | Gửi loại message mà Server không nhận từ client (ví dụ `ALERT`) |
| `INTERNAL_ERROR` | Server gặp lỗi bất ngờ khi xử lý (chi tiết xem log Server; client chỉ nhận thông báo chung) |

Lỗi của `REQUEST` không dùng `ERROR` mà trả `RESPONSE` có `ok = false` và `error` (mục 6).

## 6. `REQUEST` / `RESPONSE`

Chỉ giáo viên được gửi `REQUEST`. `REQUEST` mang `action` + `data`; Server trả `RESPONSE` có cùng `requestId`.
Thiếu hoặc sai tham số, hoặc lỗi nghiệp vụ → `RESPONSE{ok:false, error:"..."}` (connection vẫn sống). Lỗi hệ thống (database) chỉ báo chung `Lỗi Server khi xử lý <action>`.

### Ngân hàng câu hỏi và đề thi (`ExamService`, Nguoi2)

| `action` | Tham số `data` | Kết quả `data` |
|---|---|---|
| `LIST_QUESTIONS` | – | `questions`: mảng `{id, content, options[4], correctIndex}` |
| `ADD_QUESTION` | `content`, `options` (mảng 4), `correctIndex` (0..3) | `id` |
| `UPDATE_QUESTION` | `id`, `content`, `options`, `correctIndex` | – |
| `DELETE_QUESTION` | `id` | – (từ chối nếu câu đang nằm trong đề) |
| `IMPORT_QUESTIONS_CSV` | `csv`: nội dung file | `added`, `skipped`, `errors[]` (từng dòng lỗi) |
| `LIST_EXAMS` | – | `exams`: mảng `{id, title, durationMinutes, questionCount}` |
| `CREATE_EXAM` | `title`, `durationMinutes`, và **một trong** `questionIds` (chọn tay) / `randomCount` (ngẫu nhiên N câu) | `examId`, `questionIds` |

CSV câu hỏi: mỗi dòng `câu hỏi, A, B, C, D, đáp án đúng` (đáp án là `A`-`D` hoặc `1`-`4`). Có hỗ trợ ngoặc kép, dấu phẩy và xuống dòng trong ô, BOM của Excel, dòng tiêu đề.
Dòng sai (thiếu cột, đáp án lạ, trùng nội dung) chỉ bị bỏ qua và ghi vào `errors`, các dòng tốt vẫn được nhập. File không phải UTF-8 hoặc quá 5000 dòng bị từ chối cả file.

### Ca thi và điểm (`ExamService`, Nguoi2)

| `action` | Tham số `data` | Kết quả `data` |
|---|---|---|
| `LIST_SHIFTS` | – | `shifts`: mảng `{id, code, examId, examTitle, status, startTime, durationSeconds}` |
| `GET_SHIFT` | `code` | như một phần tử `shifts` + `candidates[{studentCode, fullName}]` + `rules` |
| `CREATE_SHIFT` | `examId`, `durationMinutes` hoặc `durationSeconds` (ưu tiên giây), `candidatesCsv`; tùy chọn: `code` (tự sinh nếu bỏ trống), `startTime` (epoch ms, 0 = bắt đầu thủ công), `rules` | `shiftId`, `code`, `candidatesAdded`, `errors[]` |
| `START_SHIFT` | `code` | – (đặt `endTimeServer`, gửi `RULES_CONFIG` + `EXAM_START` cho sinh viên đã đăng nhập) |
| `END_SHIFT` | `code` | – (tự chốt bài mọi sinh viên, gửi `EXAM_END{TEACHER_ENDED}`) |
| `LIST_RESULTS` | `code` | `rows`: mảng `{studentCode, fullName, submitted, autoSubmitted, score, correctCount, totalQuestions, answeredCount, submittedAt}` |
| `EXPORT_RESULTS_CSV` | `code` | `csv`: `student_code,full_name,status,score,correct,total,answered,submitted_at` (`status` = `SUBMITTED` / `AUTO_SUBMITTED` / `NOT_SUBMITTED`) |

CSV thí sinh: mỗi dòng một mã sinh viên ở cột đầu (tiêu đề tùy chọn). Mã không tồn tại hoặc trùng bị bỏ qua và ghi vào `errors`.
Điểm tính theo thang 10 (`10 * số câu đúng / tổng số câu`), do Server chấm.

### Giám sát (`MonitorService`, Nguoi3)

| `action` | Tham số `data` | Kết quả `data` |
|---|---|---|
| `LIST_MACHINES` | `code` (tùy chọn; có thì liệt kê đủ thí sinh của ca, kể cả người chưa đăng nhập) | `machines`: mảng `{machineId, fullName, online, level (NONE/YELLOW/RED), reason, rateMode, lastSeen}` |
| `MACHINE_DETAIL` | `machineId` | như một dòng `machines` + `reasons[]`, `ifScore`, `chronosReason`, `metrics`, `processNames`, `remoteAddresses`, `violations[{violationType, evidence, time}]` |
| `SET_BASELINE` | `enabled` (true/false) | – (bật/tắt chế độ BASELINE cho mọi máy: `SET_RATE{BASELINE, 1000}`) |
| `EXPORT_METRICS_CSV` | `machineId`, `rateMode` (tùy chọn, lọc theo chế độ) | `csv`: `machineId,time,kind,rateMode,kbSent,...,focusLostCount`, dùng làm trace cho `exam.tools` |

## 7. Luồng mẫu

Đăng nhập và heartbeat:

```text
Student                         Server
  |--- LOGIN ------------------>|   ExamService.login() -> kiểm tra hash, mã ca, danh sách thí sinh
  |<-- LOGIN_OK (token) --------|
  |<-- RULES_CONFIG + EXAM_START|   chỉ khi ca đang RUNNING
  |--- HEARTBEAT seq=1 -------->|   (summary Metrics + anomalyScore của IF)
  |<-- HEARTBEAT_ACK seq=1 -----|
  |        ... mỗi 10 giây ...  |
```

Làm bài:

```text
Server --- EXAM_START (câu hỏi đã trộn, endTimeServer, answers đã lưu) --> Student
Student --- ANSWER seq=1 --> Server --- ANSWER_ACK seq=1 --> Student      (mỗi lần chọn đáp án)
Server --- TIME_SYNC --> Student                                          (mỗi 30 giây)
Student --- SUBMIT --> Server --- SUBMIT_OK --> Student                   (sau đó không sửa được)
hoặc hết giờ: Server tự chốt bài --- EXAM_END(TIME_UP) --> Student
```

Mất mạng và nối lại:

```text
Student mất mạng (hoặc Server không nhận gì 30 s) → Server đánh dấu offline, giữ session + bài làm, báo giáo viên (ALERT vàng "OFFLINE")
Student kết nối lại: --- RECONNECT{token, lastAnswerSeq} --> Server
Server: <-- RECONNECT_OK{endTimeServer, answers, serverTime}   (cùng session, KHÔNG tạo session mới)
        token sai → ERROR(INVALID_TOKEN); offline quá 600 s → ERROR(TOKEN_EXPIRED) → client LOGIN lại (EXAM_START kèm answers đã lưu)
Client gửi lại các ANSWER chưa được ANSWER_ACK.
Client im lặng nghe Server > 3 chu kỳ heartbeat (mạng chết mà socket không báo lỗi) → tự reset connection và RECONNECT.
```

Giám sát:

```text
Student --- VIOLATION{violationType, evidence, time} --> Server --- ALERT(RED/YELLOW) --> Teacher
Server  --- SET_RATE(HIGH, 2000) --> Student      (máy đáng ngờ -> gửi METRICS_DETAIL mỗi 2 giây; 60 s yên thì về NORMAL)
Server  --- ROOM_STATS --> Student                (trung vị + MAD cả phòng, mỗi 10 giây)
Server  --- ALERT(YELLOW/RED, "kbSent gấp 5.2 lần trung vị phòng") --> Teacher
Server  --- ALERT(YELLOW, "ML ...") --> Teacher   (ml.mode có IF/CHRONOS và kết quả thỏa semantic của mode)
Teacher --- NOTICE{target=machineId|ALL, text} --> Server --- NOTICE --> Student(s)
```

Mức cảnh báo: luật 1-4 (process, IP, USB, network card) → `RED`; `FOCUS_LOSS` → `YELLOW`; lệch trung vị phòng ≥ `room.warning.multiplier` → `YELLOW`, ≥ `room.critical.multiplier` → `RED`.
