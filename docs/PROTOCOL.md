# PROTOCOL – Giao thức giữa Client và Server

> Tài liệu này mô tả **mọi message** trong hệ thống. Code tương ứng nằm ở `source/common/src/main/java/exam/common/protocol/`.
> Ví dụ JSON trong bảng được sinh trực tiếp từ `MessageCodec` nên khớp với code.
> Muốn đổi/thêm message: nhắn **Nguoi1** (xem `CONTRIBUTING.md`).

## 1. Quy ước chung

| Mục | Quy ước |
|---|---|
| Truyền tải | TCP socket thuần. Server mặc định cổng `5000` (`config/server.properties`) |
| Khung message | **Mỗi message là MỘT dòng JSON**, kết thúc bằng ký tự xuống dòng `\n` (UTF-8). Không có xuống dòng bên trong JSON |
| Nhận diện loại | Field `"type"` (luôn được ghi đầu tiên). `MessageCodec.decode()` đọc `type` để tạo đúng class Java |
| Thời gian | Epoch **milli giây** (kiểu `long`). Các giờ có hậu tố `Server` (`serverTime`, `endTimeServer`) là theo **đồng hồ Server**; sinh viên không được tin đồng hồ máy mình |
| `seq` | Số thứ tự tăng dần do client đặt (HEARTBEAT, ANSWER, ...). Server trả lại `seq` trong ACK để client biết message nào đã được nhận |
| Field thiếu | Field `null` không được ghi ra JSON; khi đọc, field thiếu là `null` (hoặc 0/false với kiểu số/bool) |
| Message lỗi | Dòng không phải JSON, thiếu `type` hoặc `type` lạ: Server **không** ngắt kết nối mà trả `ERROR` (`code = BAD_MESSAGE`) và đọc tiếp dòng sau |
| Chưa đăng nhập | Mọi message khác ngoài `LOGIN`/`RECONNECT` bị trả `ERROR` (`NOT_LOGGED_IN`) |
| Phát hiện mất kết nối | Sinh viên gửi `HEARTBEAT` mỗi 10 giây. Server không nhận được gì trong **30 giây** thì coi sinh viên offline (session vẫn giữ lại để `RECONNECT`) |

> **Khác với yêu cầu gốc (đã được nhóm đồng ý):** message `VIOLATION` dùng field **`violationType`** thay vì `type`,
> vì `type` đã dùng để phân loại message (JSON không thể có hai key `type` trong cùng một object).

## 2. Bảng message

Cột "Hướng": C → S là Client gửi Server; S → C là Server gửi Client.
Các field mà yêu cầu gốc không nêu (ví dụ field của `LOGIN`, `LOGIN_OK`, `NOTICE`...) do nhóm đề xuất ở skeleton này; người phụ trách có thể bổ sung sau khi báo Nguoi1.

| Message | Hướng | Fields | Ý nghĩa | Ví dụ JSON |
|---|---|---|---|---|
| `LOGIN` | Client → Server | `username` (String) – Giáo viên: username. Sinh viên: mã sinh viên<br>`password` (String)<br>`role` (Role) – TEACHER hoặc STUDENT<br>`examCode` (String) – Mã ca thi (chỉ sinh viên, giáo viên để null) | Đăng nhập. Sinh viên gửi mã SV + mật khẩu + mã ca thi; giáo viên gửi username + mật khẩu. | `{"type":"LOGIN","username":"SV001","password":"123456","role":"STUDENT","examCode":"CA001"}` |
| `LOGIN_OK` | Server → Client | `token` (String) – Mã dùng để RECONNECT khi mất kết nối<br>`userId` (int)<br>`fullName` (String)<br>`role` (Role)<br>`serverTime` (long) – Giờ Server lúc trả lời (epoch milli giây) | Đăng nhập thành công. token dùng cho RECONNECT. | `{"type":"LOGIN_OK","token":"3f2c-token","userId":1,"fullName":"Nguyễn Văn An","role":"STUDENT","serverTime":1760000000000}` |
| `LOGIN_FAIL` | Server → Client | `reason` (String) – Ví dụ: sai mật khẩu | Đăng nhập thất bại. | `{"type":"LOGIN_FAIL","reason":"Sai tài khoản hoặc mật khẩu"}` |
| `RECONNECT` | Client → Server | `token` (String)<br>`lastAnswerSeq` (int) – seq của ANSWER cuối cùng đã được ANSWER_ACK | Nối lại sau khi mất kết nối, bằng token đã nhận lúc LOGIN_OK. | `{"type":"RECONNECT","token":"3f2c-token","lastAnswerSeq":5}` |
| `RECONNECT_OK` | Server → Client | `endTimeServer` (long) – Giờ hết bài (epoch milli giây, đồng hồ Server)<br>`answers` (Map<Integer, Integer>) – questionId -> choice các câu đã trả lời<br>`serverTime` (long) | Nối lại thành công, kèm dữ liệu để khôi phục bài làm. | `{"type":"RECONNECT_OK","endTimeServer":1760003600000,"answers":{"11":2,"12":0},"serverTime":1760000000000}` |
| `HEARTBEAT` | Client → Server | `seq` (int) – Số thứ tự tăng dần 1, 2, 3, ...<br>`summary` (Metrics) – Vector 8 chiều (chế độ NORMAL) | Tín hiệu "tôi còn sống" mỗi 10 giây, kèm summary số liệu giám sát. | `{"type":"HEARTBEAT","seq":3,"summary":{"kbSent":12.5,"kbReceived":30.0,"connectionCount":8,"distinctDestinationCount":4,"cpuPercent":35.5,"ramPercent":60.0,"processCount":120,"focusLostCount":2}}` |
| `HEARTBEAT_ACK` | Server → Client | `seq` (int) – seq của HEARTBEAT được xác nhận<br>`serverTime` (long) | Server xác nhận đã nhận HEARTBEAT. | `{"type":"HEARTBEAT_ACK","seq":3,"serverTime":1760000030000}` |
| `REQUEST` | Client → Server | `requestId` (String) – Client tự sinh, dùng để ghép với RESPONSE<br>`action` (String) – Ví dụ: LIST_QUESTIONS<br>`data` (object) – Tham số của action | Yêu cầu chung (chủ yếu Teacher: quản lý câu hỏi, đề, ca thi, xem điểm). Server trả RESPONSE cùng requestId. | `{"type":"REQUEST","requestId":"req-1","action":"LIST_RESULTS","data":{"examCode":"CA001"}}` |
| `RESPONSE` | Server → Client | `requestId` (String) – Giống requestId của REQUEST<br>`ok` (bool)<br>`data` (object) – Kết quả khi ok = true<br>`error` (String) – Lý do lỗi khi ok = false | Kết quả của một REQUEST. | `{"type":"RESPONSE","requestId":"req-1","ok":true,"data":{"count":30}}` |
| `EXAM_START` | Server → Client | `questions` (List<QuestionView>) – Câu hỏi và đáp án đã trộn, không có đáp án đúng<br>`endTimeServer` (long) – Giờ hết bài (epoch milli giây, đồng hồ Server) | Bắt đầu làm bài: gửi đề đã trộn riêng cho sinh viên này. | `{"type":"EXAM_START","questions":[{"questionId":11,"content":"Thủ đô của Việt Nam là thành phố nào?","options":["Huế","Hà Nội","Đà Nẵng","Cần Thơ"]}],"endTimeServer":1760003600000}` |
| `TIME_SYNC` | Server → Client | `serverTime` (long)<br>`remainingSeconds` (long) – Số giây còn lại đến endTimeServer | Server gửi định kỳ để đồng hồ đếm ngược của client khớp đồng hồ Server. | `{"type":"TIME_SYNC","serverTime":1760000060000,"remainingSeconds":3540}` |
| `ANSWER` | Client → Server | `seq` (int) – Số thứ tự tăng dần của ANSWER<br>`questionId` (int)<br>`choice` (int) – Vị trí 0..3 trong options ĐÃ TRỘN của QuestionView | Gửi đáp án ngay khi sinh viên chọn. | `{"type":"ANSWER","seq":6,"questionId":11,"choice":1}` |
| `ANSWER_ACK` | Server → Client | `seq` (int) – seq của ANSWER được xác nhận<br>`questionId` (int)<br>`saved` (bool) | Server xác nhận đã lưu đáp án. | `{"type":"ANSWER_ACK","seq":6,"questionId":11,"saved":true}` |
| `SUBMIT` | Client → Server | `seq` (int) – Số thứ tự message của sinh viên (tiếp sau ANSWER cuối) | Sinh viên nộp bài. | `{"type":"SUBMIT","seq":7}` |
| `SUBMIT_OK` | Server → Client | `answeredCount` (int) – Số câu đã trả lời | Server đã nhận bài nộp. | `{"type":"SUBMIT_OK","answeredCount":28}` |
| `EXAM_END` | Server → Client | `reason` (String) – TIME_UP, TEACHER_ENDED hoặc SUBMITTED | Ca thi kết thúc (hết giờ hoặc giáo viên kết thúc). Server tự chốt bài. | `{"type":"EXAM_END","reason":"TIME_UP"}` |
| `RULES_CONFIG` | Server → Client | `rules` (MonitoringRules)<br>`mlMode` (MlMode) – Client biết có phải chạy Isolation Forest không | Server gửi luật giám sát cho client khi bắt đầu ca thi. | `{"type":"RULES_CONFIG","rules":{"processAllowlist":[],"processDenylist":["Zalo","Telegram","Messenger","TeamViewer","AnyDesk"],"blockedDomains":["chatgpt.com","api.openai.com","gemini.google.com"],"focusLossThreshold":3},"mlMode":"NONE"}` |
| `METRICS_DETAIL` | Client → Server | `seq` (int)<br>`time` (long) – Giờ client lúc đo (epoch milli giây)<br>`metrics` (Metrics)<br>`processNames` (List<String>) – Tên các process đang chạy<br>`remoteAddresses` (List<String>) – Các địa chỉ đích đang kết nối | Số liệu chi tiết (chế độ HIGH mỗi 2 giây, BASELINE mỗi 1 giây). | `{"type":"METRICS_DETAIL","seq":12,"time":1760000120000,"metrics":{"kbSent":12.5,"kbReceived":30.0,"connectionCount":8,"distinctDestinationCount":4,"cpuPercent":35.5,"ramPercent":60.0,"processCount":120,"focusLostCount":2},"processNames":["java","chrome"],"remoteAddresses":["142.250.1.1:443"]}` |
| `VIOLATION` | Client → Server | `violationType` (ViolationType) – Loại vi phạm<br>`evidence` (String) – Bằng chứng, ví dụ tên process hoặc IP<br>`time` (long) – Giờ client phát hiện (epoch milli giây) | Client báo một vi phạm luật giám sát. LƯU Ý: tên field là violationType (không phải type) vì "type" đã dùng để phân loại message. | `{"type":"VIOLATION","violationType":"PROCESS_DENYLIST","evidence":"Zalo.exe","time":1760000150000}` |
| `SET_RATE` | Server → Client | `mode` (RateMode) – NORMAL, HIGH hoặc BASELINE<br>`intervalMs` (int) – Chu kỳ gửi, đơn vị milli giây | Server đổi tần suất gửi số liệu của client. | `{"type":"SET_RATE","mode":"HIGH","intervalMs":2000}` |
| `ROOM_STATS` | Server → Client | `time` (long)<br>`medians` (Map<String, Double>) – tên metric -> trung vị cả phòng<br>`mads` (Map<String, Double>) – tên metric -> MAD cả phòng | Trung vị và MAD của từng metric trên cả phòng, để client tự so sánh. | `{"type":"ROOM_STATS","time":1760000180000,"medians":{"kbSent":10.0,"cpuPercent":22.0},"mads":{"kbSent":2.5,"cpuPercent":4.0}}` |
| `ALERT` | Server → Client | `machineId` (String) – Mã máy = mã sinh viên<br>`level` (AlertLevel) – YELLOW hoặc RED<br>`reason` (String) – Ví dụ: kbSent gấp 5.2 lần trung vị phòng | Server báo cảnh báo của một máy cho giáo viên. | `{"type":"ALERT","machineId":"SV003","level":"YELLOW","reason":"kbSent gấp 5.2 lần trung vị phòng"}` |
| `NOTICE` | hai chiều | `target` (String) – machineId của một máy, hoặc "ALL" cho cả phòng<br>`text` (String) | Thông báo của giáo viên. Giáo viên gửi lên Server, Server chuyển tới sinh viên. | `{"type":"NOTICE","target":"ALL","text":"Còn 5 phút nữa hết giờ"}` |
| `ERROR` | Server → Client | `code` (String) – Ví dụ: BAD_MESSAGE, NOT_LOGGED_IN, NOT_IMPLEMENTED<br>`message` (String) | Server báo lỗi cho client (message sai định dạng, chưa đăng nhập, chưa hỗ trợ, ...). | `{"type":"ERROR","code":"NOT_LOGGED_IN","message":"Hãy gửi LOGIN trước"}` |

## 3. Kiểu dữ liệu lồng nhau

### `Metrics` – vector 8 chiều (mỗi 10 giây)

| # | Field | Ý nghĩa | Chronos chấm? |
|---|---|---|---|
| 1 | `kbSent` | KB gửi trong chu kỳ | Có |
| 2 | `kbReceived` | KB nhận trong chu kỳ | Có |
| 3 | `connectionCount` | Số kết nối mạng | Có |
| 4 | `distinctDestinationCount` | Số địa chỉ đích khác nhau | Có |
| 5 | `cpuPercent` | % CPU | Có |
| 6 | `ramPercent` | % RAM | Có |
| 7 | `processCount` | Số tiến trình | Không (chỉ Isolation Forest) |
| 8 | `focusLostCount` | Số lần mất focus cửa sổ thi | Không (chỉ Isolation Forest) |

### `QuestionView` – câu hỏi gửi cho sinh viên

`{"questionId":11,"content":"...","options":["A","B","C","D"]}`. **Không có đáp án đúng.**
Thứ tự câu và thứ tự `options` đã được Server trộn riêng cho từng sinh viên. Vì vậy `choice` trong `ANSWER` là vị trí (0..3) trong `options` **đã trộn**; Server tự quy đổi khi chấm.

### `MonitoringRules` – luật giám sát (trong `RULES_CONFIG`)

`processAllowlist`, `processDenylist`, `blockedDomains` (danh sách chuỗi) và `focusLossThreshold` (số nguyên). Giá trị mặc định: xem `MonitoringRules.createDefault()`.

## 4. Giá trị của các enum

| Enum | Giá trị |
|---|---|
| `Role` | `TEACHER`, `STUDENT` |
| `RateMode` | `NORMAL` (10 s, summary trong HEARTBEAT), `HIGH` (2 s, METRICS_DETAIL), `BASELINE` (1 s, METRICS_DETAIL, không ML) |
| `MlMode` | `NONE`, `IF`, `CHRONOS`, `BOTH_OR`, `BOTH_AND` |
| `AlertLevel` | `YELLOW` (nghi ngờ), `RED` (vi phạm rõ ràng) |
| `ViolationType` | `PROCESS_NOT_ALLOWED`, `PROCESS_DENYLIST`, `BLOCKED_IP`, `USB_DEVICE`, `NETWORK_CARD`, `FOCUS_LOSS` |

## 5. Mã lỗi trong `ERROR`

| `code` | Khi nào |
|---|---|
| `BAD_MESSAGE` | Dòng không phải JSON hợp lệ, thiếu `type` hoặc `type` lạ |
| `NOT_LOGGED_IN` | Gửi message cần đăng nhập khi chưa `LOGIN` |
| `ALREADY_LOGGED_IN` | Gửi `LOGIN` lần hai trên cùng connection |
| `FORBIDDEN` | Sai vai trò (ví dụ sinh viên gửi `NOTICE` hoặc `REQUEST`) |
| `UNEXPECTED_MESSAGE` | Gửi loại message mà Server không nhận từ client (ví dụ `ALERT`) |
| `NOT_IMPLEMENTED` | Tính năng chưa cài đặt (skeleton), ví dụ `RECONNECT` |
| `INTERNAL_ERROR` | Server gặp lỗi bất ngờ khi xử lý (chi tiết xem log Server) |

## 6. `REQUEST` / `RESPONSE` của giáo viên

`REQUEST` mang `action` + `data`; Server trả `RESPONSE` có cùng `requestId`. Danh sách action **đề xuất** (Nguoi2 chốt khi cài đặt, rồi cập nhật bảng này):

| `action` | Việc |
|---|---|
| `LIST_QUESTIONS`, `ADD_QUESTION`, `UPDATE_QUESTION`, `DELETE_QUESTION` | Ngân hàng câu hỏi |
| `IMPORT_QUESTIONS_CSV` | Nhập câu hỏi từ CSV |
| `CREATE_EXAM` | Tạo đề (chọn tay hoặc ngẫu nhiên N câu, đặt thời gian) |
| `CREATE_SHIFT`, `SET_RULES`, `START_SHIFT`, `END_SHIFT` | Ca thi, luật giám sát, bắt đầu/kết thúc |
| `LIST_RESULTS`, `EXPORT_RESULTS_CSV` | Xem điểm, xuất CSV |

Hiện tại Server trả `RESPONSE` với `ok = false`, `error = "NOT_IMPLEMENTED: <action>"`.

## 7. Luồng mẫu

Đăng nhập và heartbeat (đã chạy được ở skeleton):

```text
Student                         Server
  |--- LOGIN ------------------>|   ExamService.login() -> SQLite
  |<-- LOGIN_OK (token) --------|
  |--- HEARTBEAT seq=1 -------->|   log "[Heartbeat] SV001 seq=1"
  |<-- HEARTBEAT_ACK seq=1 -----|
  |        ... mỗi 10 giây ...  |
```

Làm bài (TODO Nguoi1/Nguoi2):

```text
Server --- EXAM_START (câu hỏi đã trộn, endTimeServer) --> Student
Student --- ANSWER seq=1 --> Server --- ANSWER_ACK seq=1 --> Student      (mỗi lần chọn đáp án)
Server --- TIME_SYNC --> Student                                          (định kỳ)
Student --- SUBMIT --> Server --- SUBMIT_OK --> Student
hoặc hết giờ: Server tự chốt bài --- EXAM_END(TIME_UP) --> Student
```

Giám sát (TODO Nguoi3/Nguoi4):

```text
Student --- VIOLATION --> Server --- ALERT(RED) --> Teacher
Server  --- SET_RATE(HIGH, 2000) --> Student      (máy đáng ngờ -> gửi METRICS_DETAIL mỗi 2 giây)
Server  --- ROOM_STATS --> Student                (trung vị + MAD cả phòng)
```
