# WORK_SPLIT – Phân công công việc

Hạn nộp: **31/10/2026**. Đọc kèm `CONTRIBUTING.md` (quy tắc làm việc), `docs/SPEC.md` (yêu cầu), `docs/PROTOCOL.md`, `docs/ARCHITECTURE.md`.

## Lịch

| Giai đoạn | Thời gian | Mục tiêu |
|---|---|---|
| Tuần 1 | 07/10 – 13/10 | Nền tảng từng mảng: mạng, database, đo số liệu, Isolation Forest |
| Tuần 2 | 14/10 – 20/10 | Chức năng cốt lõi từng mảng, bắt đầu ghép các mảng với nhau |
| Tuần 3 | 21/10 – **28/10** | Hoàn thiện, giao diện, thực nghiệm, ghép toàn hệ thống |
| Đệm | 29/10 – 31/10 | **Đóng băng tính năng.** Viết báo cáo, slide, tập vấn đáp, nộp bài 31/10 |

Quy ước: mã chức năng `N<người>-<số>` (số theo danh sách phân công). Cột **"Xong khi"** là tiêu chí kiểm chứng được; chưa đạt thì chưa tính là xong.
Dùng `grep -rn "TODO(NguoiX)" source config` để xem việc còn lại của mình.

## Trạng thái hiện tại

Toàn bộ chức năng trong bảng dưới đây đã được cài đặt và kiểm thử trong phiên "hoàn thiện" (xem `git log`). Cột **"Xong khi"** của từng chức năng được kiểm bằng unit/integration test hoặc kịch bản end-to-end (`source/e2e`). Chỉ còn các việc sau:

| Việc còn lại | Người | Vì sao chưa xong |
|---|---|---|
| **N4-05 / N4-14 / N4-15**: chạy Chronos-Bolt **thật**, so sánh với số liệu thật, chọn `ml.mode` mặc định (hiện `NONE`) | Nguoi4 | Môi trường phát triển chặn `huggingface.co` nên chưa tải được trọng số. Đường ống đã kiểm thử bằng model khởi tạo ngẫu nhiên và backend `naive`. Số liệu mô phỏng ở `statics/results/` chỉ có phần Isolation Forest là thật (xem `statics/results/README.md`) |
| **Thu trace thật** bằng `Recorder` trên máy thật / baseline phòng thi | Nguoi3, Nguoi4 | Cần máy thật và thời gian đo; hiện mới có `data/traces/sim.csv` (mô phỏng) |
| **N1-03 / N1-04 ở mức 500 client đăng nhập thật** | Nguoi1 | Đã có test 40 client đăng nhập đồng thời và 500 connection nhàn rỗi (client mới vẫn đăng nhập trong ~100 ms). Chương trình tạo tải 500 client đã đăng nhập chưa viết |
| **Phần báo cáo (d)** và slide | cả nhóm | Chưa viết (`report/` trống) |
| Điền `@REPLACE-ME-NGUOI1/3/4` trong `.github/CODEOWNERS` | Nguoi1, Nguoi3, Nguoi4 | Chưa biết tài khoản GitHub (không được đoán). `@tanh8805` = Nguoi2 |
| Thử giao diện Swing trên máy Windows/macOS thật | cả nhóm | Mới kiểm tra trên Linux (Xvfb) |

Thay đổi quyền sở hữu so với bản đầu: `exam.client.ml` (`IsolationForestScorer`, `AnomalyDetector`) thuộc **Nguoi4**; `StudentClient`, `TeacherClient` và module `e2e` thuộc **Nguoi1**.
Hiểu về N3-13: "so sánh số liệu của từng máy với thống kê phòng" = công thức median/MAD ở `docs/SPEC.md` mục 10, cộng với `anomalyScore` của client gửi kèm heartbeat khi ghép ML theo `ml.mode`.

---

## Nguoi1 – Network

### (a) Sở hữu

`source/common/.../protocol/` (+ test), `common/.../model/`, `server/.../{api,net,session,rate}/`, `ServerMain`, `client/.../{api,net}/`, `StudentMain`, `TeacherMain`, `config/server.properties`, `docs/`, `CONTRIBUTING.md`, `WORK_SPLIT.md`, file `pom.xml`, `.github/`. Riêng `IsolationForestScorer.java` trong `client/api` thuộc Nguoi4.

### (b) Cần đọc của người khác

- `server/.../exam/ExamServiceImpl.java`, `server/.../db/*` (Nguoi2) – để biết `login`, `saveAnswer`, `submit` trả gì.
- `server/.../monitor/MonitorServiceImpl.java` (Nguoi3) – nơi ALERT/ROOM_STATS được tạo.
- `client/.../student/StudentFrame.java`, `client/.../teacher/TeacherFrame.java` (Nguoi2) – nơi nhận message từ `ServerLink`.
- `server/.../ml/MlGatewayImpl.java` (Nguoi4) và `ml-service/app.py` – hợp đồng gọi ML.

### (c) Chức năng

**Tuần 1 (07/10 – 13/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N1-01 | Protocol | Rà soát 24 message với cả nhóm, chốt field còn bỏ ngỏ (`LOGIN_OK`, `RECONNECT_OK`, `ROOM_STATS`, `METRICS_DETAIL`, `NOTICE`); cập nhật `docs/PROTOCOL.md` | Cả 3 người còn lại xác nhận bảng trong `PROTOCOL.md` bằng comment trong PR; `mvn -q test` pass |
| N1-02 | Codec | Thêm test biên cho `MessageCodec`: tiếng Việt có dấu, chuỗi dài 100 KB, dòng rỗng, JSON thiếu field, `type` sai kiểu | Các test mới pass; gửi dòng rác tới Server nhận `ERROR BAD_MESSAGE` và connection vẫn dùng tiếp được |
| N1-03 | TCP server | Chạy ổn với nhiều client; xử lý tắt Server gọn (đóng socket, dừng vòng lặp) | Script/chương trình thử mở **50 client** cùng lúc, mỗi client nhận `LOGIN_OK` và `HEARTBEAT_ACK`; log Server không có exception |
| N1-04 | Virtual thread | Chứng minh mô hình một virtual thread mỗi connection hoạt động | Giữ **500 connection** mở đồng thời mà client mới vẫn `LOGIN_OK` trong < 1 giây; ghi số liệu vào báo cáo; giải thích được vì sao không cần thread pool |
| N1-05 | Authentication | Kết hợp Nguoi2 kiểm tra mã ca thi (`examService.findShiftByCode`) | Sai mật khẩu / sai vai trò / thiếu field / mã ca không tồn tại đều trả `LOGIN_FAIL` có lý do; có test tự động cho từng trường hợp |
| N1-07 | Heartbeat | Hoàn thiện HEARTBEAT/ACK, ghi `lastHeartbeat` | Với một Student chạy 2 phút: log Server có `seq` tăng liên tục, cách nhau 10 giây (±1 giây); Student hiện `Heartbeat: seq=N` cập nhật theo |

**Tuần 2 (14/10 – 20/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N1-06 | Token | Token gắn vào session, hết hạn khi ca thi kết thúc | Token sai/đã hết hạn → `ERROR`; token đúng tìm được session (`findByToken`) – có test |
| N1-09 | Reconnect (Server) | Cài `handleReconnect`: gắn connection mới, `online = true`, gửi `RECONNECT_OK` (kèm `endTimeServer` + đáp án đã lưu, lấy từ Nguoi2) | Đóng socket của Student giữa chừng rồi gửi `RECONNECT` bằng token → nhận `RECONNECT_OK` đúng dữ liệu; Server log `online` trở lại |
| N1-08 | Disconnect detection | Dùng timeout 30 giây đã có; gửi báo offline cho giáo viên (phối hợp Nguoi3) | Tắt đột ngột một Student (kill process): trong **≤ 30 giây** Server log `offline` và giáo viên thấy máy offline; Student reconnect thì thấy online lại |
| N1-10 | ServerLink (client) | `TcpServerLink` tự nối lại có backoff, gửi `RECONNECT`; gửi lại `ANSWER` chưa được ACK (phối hợp Nguoi2) | Tắt Server rồi bật lại trong 20 giây: Student chuyển `RECONNECTING` → `Connected`, không phải đăng nhập tay; không mất `ANSWER` nào |
| N1-11 | RateController | Cài `RateControllerImpl`: map máy → (mode, lần nghi ngờ cuối) | `raiseToHigh` đưa máy sang HIGH; 60 giây không nghi ngờ lại tự về NORMAL; `startBaselineForAll` đưa mọi máy sang BASELINE – test với thời gian rút ngắn |
| N1-12 | SET_RATE | Gửi/nhận SET_RATE, client đổi chu kỳ (phối hợp Nguoi3 ở `MonitoringLoop.setRate`) | Student nhận `SET_RATE(HIGH, 2000)` và thật sự gửi `METRICS_DETAIL` mỗi 2 giây (xem log Server) |

**Tuần 3 (21/10 – 28/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N1-13 | NOTICE | Giáo viên gửi NOTICE từ UI (phối hợp Nguoi3 ở `MonitorPanel`); Student hiện thông báo | Gửi tới một máy: **chỉ máy đó** nhận; gửi `ALL`: mọi máy online nhận; máy offline: giáo viên biết không gửi được |
| N1-14 | ALERT | Chuyển ALERT từ `MonitorService` tới mọi giáo viên online | ALERT hiện trên màn hình giáo viên trong **< 1 giây** kể từ lúc Server nhận `VIOLATION` |
| – | Tích hợp chung | Chạy thử toàn hệ thống với ≥ 10 Student; sửa lỗi liên mảng; kịch bản demo | Kịch bản demo chạy trọn vẹn 2 lần liên tiếp không lỗi: đăng nhập → làm bài → vi phạm → ALERT → NOTICE → nộp → xem điểm |

### (d) Phần báo cáo

**Network Communication Design** (kiến trúc client/server, protocol, định dạng message, luồng message, reconnect) và **Concurrency** (virtual thread, đồng bộ hóa, số liệu 50/500 connection).

### (e) Câu hỏi vấn đáp cần chuẩn bị

1. Vì sao chọn TCP mà không phải UDP? "Mỗi message một dòng JSON" giải quyết vấn đề gì của TCP (stream không có ranh giới message)?
2. Virtual thread khác platform thread thế nào? Vì sao không cần thread pool? Điều gì xảy ra nếu client không bao giờ gửi `\n`?
3. Nhiều thread cùng ghi vào một socket thì có vấn đề gì? Em xử lý bằng gì (`synchronized` ở đâu)?
4. Phát hiện client chết mà không đóng socket bằng cách nào? Vì sao chọn 30 giây?
5. Reconnect hoạt động ra sao? Token lấy từ đâu, nếu bị đánh cắp thì sao?
6. `MessageCodec.decode` chọn class theo cách nào? Thêm một message mới phải sửa những chỗ nào?
7. Server đang gửi dở message thì client ngắt kết nối: chuyện gì xảy ra?
8. `ConcurrentHashMap` và `volatile` trong `ClientSession` để làm gì?

---

## Nguoi2 – Exam Business

### (a) Sở hữu

`server/.../exam/`, `server/.../db/`, `server/src/main/resources/{schema.sql,sample_data.sql}`, `server/src/test/.../db/`, `client/.../student/`, `client/.../teacher/` (trừ `teacher/monitor/`).

### (b) Cần đọc của người khác

- `server/.../api/ExamService.java`, `MessageSender.java`, `SessionRegistry.java` (Nguoi1) – hợp đồng phải cài.
- `server/.../net/ClientHandler.java` (Nguoi1) – nơi gọi `login`, `saveAnswer`, `submit`, `handleTeacherRequest`.
- `client/.../api/ServerLink.java`, `StudentMain.java`, `TeacherMain.java` (Nguoi1) – cách nhận message trên UI.
- `client/.../api/RuleEngine.java` (Nguoi3) – `StudentFrame` gọi `onFocusLost()`.
- `common/.../model/*`, `docs/PROTOCOL.md` (`EXAM_START`, `ANSWER`, `QuestionView`).

### (c) Chức năng

**Tuần 1 (07/10 – 13/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N2-01 | SQLite schema | Rà soát `schema.sql` (khóa ngoại, ràng buộc); lưu mật khẩu dạng hash thay vì chữ thường | Xóa `exam.db` rồi chạy Server tạo lại đủ bảng; `DatabaseInitTest` pass; đăng nhập `SV001/123456` vẫn thành công sau khi đổi sang hash |
| N2-02 | DAO | Thêm DAO cho đề, ca thi, thí sinh, đáp án, kết quả (SQL text block + `PreparedStatement`) | Mỗi DAO có test với DB tạm (thêm → đọc lại → so sánh); không có SQL ghép chuỗi từ dữ liệu người dùng |
| N2-03 | Question bank | Thêm/sửa/xóa/liệt kê câu hỏi 4 đáp án; action `REQUEST` tương ứng; tab "Câu hỏi" trên `TeacherFrame` | Giáo viên thêm, sửa, xóa một câu qua UI; dữ liệu còn sau khi restart Server; sửa câu có `correct_option` ngoài 0..3 bị từ chối |
| N2-04 | CSV import | Nhập câu hỏi từ file CSV (chốt định dạng và ghi vào `PROTOCOL.md`) | Nhập file mẫu 30 câu thành công; file có dòng lỗi báo rõ số dòng lỗi và **không** nhập dở dang (hoặc báo rõ dòng nào đã nhập) |

**Tuần 2 (14/10 – 20/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N2-05 | Exam creation | Tạo đề, đặt thời gian; tab "Đề thi" | Tạo đề 10 câu qua UI; đề lưu đúng `exam_questions` |
| N2-06 | Random question selection | Chọn ngẫu nhiên N câu từ ngân hàng | Chọn 10 trong 30 câu: không trùng câu; N lớn hơn số câu hiện có → báo lỗi rõ ràng; có test |
| N2-07 | Question/answer shuffle | Trộn thứ tự câu và đáp án riêng từng sinh viên bằng `shuffle_seed` lưu DB | Hai sinh viên khác nhau nhận thứ tự khác nhau; **cùng sinh viên** gọi lại (reconnect) ra **đúng thứ tự cũ**; test đảm bảo `choice` quy đổi về đáp án gốc chính xác |
| N2-08 | Exam session | Tạo ca thi (chọn đề, giờ bắt đầu, thời lượng, thí sinh từ CSV), bắt đầu/kết thúc ca, gửi `EXAM_START`; tab "Ca thi" | Ca chuyển CREATED → RUNNING → ENDED; sinh viên đăng nhập đúng mã ca nhận `EXAM_START`; sinh viên không có trong danh sách bị từ chối |
| N2-09 | Server timer | `finishExpiredExams()` tự chốt bài khi hết giờ; gửi `TIME_SYNC` định kỳ; gửi `EXAM_END` | Ca 2 phút: hết giờ Server **tự chốt** mọi bài chưa nộp, sinh viên nhận `EXAM_END(TIME_UP)`; sai lệch giữa đồng hồ client và Server < 2 giây (đã `TIME_SYNC`) |
| N2-10 | ANSWER | `saveAnswer` ghi DB (ghi đè khi chọn lại), UI gửi `ANSWER` ngay khi chọn | Chọn rồi đổi đáp án: DB chỉ còn đáp án cuối; sau hết giờ gửi ANSWER → `saved=false` |

**Tuần 3 (21/10 – 28/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N2-11 | SUBMIT | Sinh viên nộp bài, khóa bài, `SUBMIT_OK` | Nộp xong không gửi `ANSWER` thêm được; nộp hai lần không tạo hai kết quả |
| N2-12 | Grading | Chấm điểm, lưu `results` | Với đề mẫu biết trước đáp án: điểm khớp tính tay cho ít nhất 3 sinh viên có thứ tự trộn khác nhau |
| N2-13 | CSV export | Xuất bảng điểm CSV (`exportScoresCsv`) | File mở bằng Excel đúng tiếng Việt (UTF-8 có BOM nếu cần); số dòng = số thí sinh |
| N2-14 | Teacher UI | Hoàn thiện đăng nhập + 4 tab (Câu hỏi, Đề, Ca thi, Điểm) | Giáo viên làm trọn vẹn: tạo câu hỏi → đề → ca → bắt đầu → xem điểm → xuất CSV chỉ bằng UI |
| N2-15 | Student UI | Đăng nhập (mã SV, mật khẩu, mã ca), làm bài, chuyển câu, đánh dấu câu, đồng hồ đếm ngược theo Server, nộp, khôi phục sau reconnect (phối hợp Nguoi1) | Sinh viên làm trọn một bài; tắt mạng 20 giây rồi bật lại: bài làm và đồng hồ khôi phục đúng; `StudentFrame` báo `RuleEngine.onFocusLost()` khi mất focus |

### (d) Phần báo cáo

**Problem**, **System Overview**, **Implementation – Exam** (schema, luồng thi, trộn đề, chấm điểm, đồng hồ Server).

### (e) Câu hỏi vấn đáp cần chuẩn bị

1. Vì sao dùng đồng hồ Server thay vì đồng hồ máy sinh viên? `endTimeServer` hoạt động ra sao?
2. Trộn đáp án riêng từng người: server chấm điểm bằng cách nào? `shuffle_seed` lưu để làm gì?
3. Vì sao gửi `ANSWER` ngay khi chọn thay vì gửi cả bài lúc nộp? Điều gì xảy ra nếu mất mạng giữa chừng?
4. SQL injection là gì? Em đã chống bằng cách nào (`PreparedStatement`)?
5. Mỗi lần gọi DAO mở một connection SQLite: vì sao OK? Hai sinh viên ghi đáp án cùng lúc có xung đột không?
6. Server "tự chốt" khi hết giờ chạy ở thread nào? Nếu sinh viên nộp đúng lúc hết giờ thì sao?
7. Mật khẩu lưu như thế nào? Vì sao phải hash?
8. Nhập CSV: xử lý dòng lỗi thế nào?

---

## Nguoi3 – Monitoring

### (a) Sở hữu

`server/.../monitor/`, `client/.../monitor/`, `client/.../teacher/monitor/`.

### (b) Cần đọc của người khác

- `common/.../model/{Metrics,MonitoringRules,ViolationType,AlertLevel,RateMode}.java`, `docs/PROTOCOL.md` (`HEARTBEAT`, `METRICS_DETAIL`, `VIOLATION`, `SET_RATE`, `ROOM_STATS`, `ALERT`) (Nguoi1).
- `server/.../api/{MonitorService,MessageSender,SessionRegistry,RateController}.java`, `server/.../net/ClientHandler.java`, `PeriodicJobs.java` (Nguoi1).
- `client/.../api/{MetricsSource,RuleEngine,ServerLink}.java`, `StudentMain.java` (Nguoi1).
- `server/.../api/MlGateway.java` và `exam/common/ml/MlResult.java` (Nguoi4) – ghép kết quả ML vào cảnh báo.
- `client/.../student/StudentFrame.java` (Nguoi2) – nơi báo mất focus.

### (c) Chức năng

**Tuần 1 (07/10 – 13/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N3-01 | OSHI MetricsSource | `OshiMetricsSource`: KB gửi/nhận (so lần đo trước), số kết nối, số địa chỉ đích khác nhau, %CPU, %RAM, số process; danh sách process, USB, network card, IP đích | Chạy riêng một chương trình thử in vector 8 chiều mỗi 10 giây trên máy thật; mở một video/tải file thì `kbReceived` tăng rõ rệt; chạy được trên cả Windows và Linux (hoặc ghi rõ hạn chế) |
| N3-02 | RuleEngine | Khung `RuleEngineImpl`: chụp mốc ban đầu, trả danh sách vi phạm mới mỗi lần `checkForViolations()` | Gọi `start` rồi `checkForViolations` liên tiếp không có thay đổi → trả danh sách rỗng; có unit test dùng `MetricsSource` giả |
| N3-04 | Focus tracking | Phối hợp Nguoi2 để `StudentFrame` gọi `onFocusLost()`; đếm; Rule 5 | Chuyển qua cửa sổ khác 4 lần: `getFocusLostCount() == 4`; vượt `focusLossThreshold` thì sinh ra vi phạm `FOCUS_LOSS` |

**Tuần 2 (14/10 – 20/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N3-03 | 5 luật giám sát | Rule 1 process (allowlist + denylist), Rule 2 domain→IP (resolve lúc bắt đầu), Rule 3 USB mới, Rule 4 network card mới, Rule 5 focus | **Mỗi luật có một kịch bản thử thật** ghi trong báo cáo: mở Notepad/Zalo → vi phạm process; truy cập `chatgpt.com` → vi phạm IP; cắm USB → vi phạm USB; bật hotspot/VPN → vi phạm network card; không có vi phạm giả khi không làm gì trong 5 phút |
| N3-05 | VIOLATION | `MonitoringLoop` gửi `VIOLATION` (`violationType`, `evidence`, `time`) | Mở process cấm → Server log `[Violation] SV001 PROCESS_DENYLIST: ...` trong ≤ 10 giây; cùng một vi phạm không bị báo lặp liên tục |
| N3-06 | Summary metrics | Đưa vector 8 chiều vào `HEARTBEAT.summary` mỗi 10 giây (nối vào `StudentMain`) | Log Server hiện CPU/RAM thật thay vì 0 |
| N3-07 | Detailed metrics | `METRICS_DETAIL` (kèm process, địa chỉ đích) khi ở HIGH/BASELINE | Khi Server gửi `SET_RATE(HIGH,2000)`: Server nhận detail cách nhau 2 giây (±0,5 giây) |
| N3-08 | SET_RATE handling | `MonitoringLoop.setRate(...)` đổi chu kỳ gửi; NORMAL quay lại summary trong HEARTBEAT | Chuyển NORMAL → HIGH → NORMAL: tần suất đổi đúng và không bị gửi trùng cả hai kiểu |
| N3-09 | MonitorService | Server nhận summary/detail/violation, ghi bảng `violations`, gọi `rateController.raiseToHigh`, theo dõi online/offline | Vi phạm được ghi DB (xem bằng SQLite); máy vi phạm chuyển HIGH |

**Tuần 3 (21/10 – 28/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N3-10 | Median | Trung vị từng metric của cả phòng | Unit test với dữ liệu biết trước (số lẻ và số chẵn phần tử, một phần tử, rỗng) |
| N3-11 | MAD | Median Absolute Deviation từng metric | Unit test với dữ liệu biết trước; MAD = 0 không gây chia cho 0 |
| N3-12 | ROOM_STATS | Mỗi 10 giây trong `runPeriodicChecks()` gửi `ROOM_STATS` xuống sinh viên | Student log nhận `ROOM_STATS` mỗi 10 giây với `medians`/`mads` đúng khi đối chiếu tay với 5 máy |
| N3-13 | Client-score comparison | So sánh số liệu/điểm của từng máy với thống kê phòng (và với điểm client gửi lên), sinh WARNING "gấp X lần trung vị phòng" | Cho một máy tải lớn bất thường: WARNING với reason chứa đúng hệ số X; các máy bình thường không bị cảnh báo; **chốt ngưỡng** và ghi vào `docs/SPEC.md` mục 10 |
| N3-14 | Alert level | Luật quyết định YELLOW và RED | Bảng "loại vi phạm → mức" ghi trong `SPEC.md`; vi phạm luật 1–4 ra RED, các cảnh báo thống kê/ML/focus ra YELLOW (hoặc theo bảng nhóm chốt) |
| N3-15 | Teacher monitoring UI | `MonitorPanel`: bảng danh sách máy, online/offline, màu đỏ/vàng, lý do, xem chi tiết một máy, gửi NOTICE | Giáo viên nhìn **một màn hình** thấy mọi máy; ALERT đổi màu dòng trong < 1 giây; bấm "Chi tiết" thấy `METRICS_DETAIL` gần nhất |

### (d) Phần báo cáo

**Monitoring Design** (5 luật, metrics, rate control, thống kê phòng) và **Error Handling** (xử lý lỗi toàn hệ thống: message sai, mất kết nối, ML lỗi, OSHI không đọc được...).

### (e) Câu hỏi vấn đáp cần chuẩn bị

1. 5 luật hoạt động thế nào? Luật nào dễ bị qua mặt (ví dụ dùng điện thoại tra bài)? Hạn chế là gì?
2. Vì sao dùng median và MAD thay vì mean và độ lệch chuẩn? (chịu được giá trị ngoại lai).
3. Vì sao chia NORMAL/HIGH? Vì sao không để mọi máy gửi chi tiết mỗi giây (băng thông, tải Server)?
4. Resolve domain thành IP lúc bắt đầu có nhược điểm gì (IP của dịch vụ đổi, CDN)?
5. Làm sao tránh báo vi phạm giả (false positive) khi sinh viên mở app hệ thống?
6. Nếu OSHI đọc lỗi hoặc client không gửi được dữ liệu thì chuyện gì xảy ra? (error handling)
7. Cảnh báo đỏ và vàng khác nhau thế nào? Vì sao?
8. "Gấp X lần trung vị phòng" tính ra sao? Ngưỡng chọn dựa vào đâu?

---

## Nguoi4 – ML / Experiment

### (a) Sở hữu

`common/.../ml/` (`IsolationForest`, `MlResult`), `client/.../api/IsolationForestScorer.java`, `server/.../ml/`, `source/tools/`, `ml-service/`, `config/ml.properties`, `data/traces/`, `statics/results/`.

### (b) Cần đọc của người khác

- `common/.../model/Metrics.java` (vector 8 chiều, 6 chiều đầu cho Chronos) (Nguoi1).
- `client/.../api/{AnomalyScorer,MetricsSource}.java` (Nguoi1) và `client/.../monitor/{MonitoringLoop,OshiMetricsSource}.java` (Nguoi3) – nơi gọi scorer và nguồn dữ liệu cho `Recorder`.
- `server/.../api/{MlGateway,RateController}.java` (Nguoi1), `server/.../monitor/MonitorServiceImpl.java` (Nguoi3) – nơi gọi `MlGateway`.
- `docs/SPEC.md` (mục 8, 9, 11–13).

### (c) Chức năng

**Tuần 1 (07/10 – 13/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N4-01 | Isolation Forest tự cài | `IsolationForest.fit/score` bằng Java thuần (cây ngẫu nhiên, độ dài đường đi, anomaly score), có seed để tái lập | Trên dữ liệu 2 chiều tạo tay: các điểm xa cụm có score > 0,6 và các điểm trong cụm < 0,5; cùng seed cho cùng kết quả |
| N4-02 | Isolation Forest tests | JUnit cho `IsolationForest` | ≥ 5 test: score trong [0,1], điểm ngoại lai cao hơn điểm thường, tái lập theo seed, `fit` mẫu rỗng báo lỗi rõ ràng, chiều sai báo lỗi rõ ràng |
| N4-03 | AnomalyScorer | Hoàn thiện `IsolationForestScorer`; logic threshold `max(training score) + 0.05` và cảnh báo 3 lần liên tiếp (phối hợp Nguoi3 trong `MonitoringLoop`) | Mô phỏng chuỗi: 5 phút bình thường (rút ngắn khi test) rồi ngoại lai: **không báo** khi vượt 1–2 lần, **báo** khi vượt đủ 3 lần liên tiếp; có test |
| N4-04 | ml-service | Chốt hợp đồng `/score` (đã có khung), kiểm tra dữ liệu đầu vào | `uvicorn app:app` chạy; `/health` trả `{"status":"ok"}`; `/score` với body sai trả HTTP 422; ghi hợp đồng cuối cùng vào `ml-service/README.md` và `docs/PROTOCOL.md`/`SPEC.md` nếu đổi |
| N4-05 | Chronos | Nạp `amazon/chronos-bolt-tiny` chạy trên CPU, dự báo q0.1/q0.5/q0.9 | Script thử: 30 máy × 6 metric × 60 điểm → dự báo trong thời gian đo được (ghi số vào báo cáo); đo RAM/CPU của tiến trình |

**Tuần 2 (14/10 – 20/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N4-06 | MlGateway | `MlGatewayImpl.recordMetrics` giữ 60 điểm gần nhất/máy; `scoreAllMachines` gọi `ml-service` bằng `java.net.http.HttpClient` (thư viện chuẩn của JDK) | Với `ml-service` Chronos thật, Server nhận được kết quả machineId → `MlResult` |
| N4-07 | Batching | Gom mọi máy vào **một** request mỗi 10 giây | Với 30 máy: phía `ml-service` chỉ thấy **1** request mỗi chu kỳ (đếm log) |
| N4-08 | Async call | Gọi bất đồng bộ (`CompletableFuture`) | Trong lúc gọi ML chậm, Server vẫn xử lý HEARTBEAT/LOGIN bình thường (đo độ trễ HEARTBEAT_ACK không tăng) |
| N4-09 | Timeout | Timeout 3 giây (từ `config/ml.properties`) | Cho `ml-service` ngủ 5 giây: gọi bị hủy sau ~3 giây, có log |
| N4-10 | Fallback | Lỗi/timeout/ml-service tắt → bỏ qua ML, hệ thống vẫn chạy; đáng ngờ khi ngoài q0.1–q0.9 đủ 3 lần liên tiếp | Tắt `ml-service` giữa ca thi: Server không crash, mọi chức năng khác chạy bình thường, log ghi "bỏ qua ML"; bật lại thì ML tự hoạt động trở lại; test cho quy tắc 3 lần liên tiếp |
| N4-11 | Recorder | Ghi trace thật từ `MetricsSource` ra `data/traces/*.csv` (dùng BASELINE của Nguoi1) | Ghi 10 phút trên một máy ra file CSV 8 cột có tiêu đề; mở bằng Excel được |
| N4-12 | Simulator | Sinh trace mô phỏng bình thường và gian lận có nhãn | Sinh được ≥ 30 máy × 30 phút; mỗi kịch bản gian lận có nhãn; sinh lại bằng cùng seed ra file giống hệt |

**Tuần 3 (21/10 – 28/10)**

| # | Chức năng | Việc cụ thể | Xong khi |
|---|---|---|---|
| N4-13 | ExperimentRunner | Chạy từng `ml.mode` trên cùng bộ trace, tính precision/recall/F1 và thời gian phát hiện | Một lệnh duy nhất chạy hết 5 chế độ và ghi bảng kết quả vào `statics/results/`; chạy lại ra kết quả giống nhau |
| N4-14 | Model comparison | So sánh IF vs Chronos vs BOTH_OR vs BOTH_AND vs NONE, kèm biểu đồ/bảng | Bảng so sánh + nhận xét (mô hình nào tốt ở kịch bản nào, vì sao) sẵn sàng đưa vào báo cáo |
| N4-15 | Chọn `ml.mode` | Chọn giá trị mặc định dựa trên số liệu | `config/ml.properties` đặt giá trị mặc định mới, kèm lý do dựa trên số liệu ở N4-14; Server chạy với giá trị đó không lỗi |

### (d) Phần báo cáo

**Related Work**, **Experimental Setup**, **Results**, **Novelty** (kết hợp luật + ML client tự học + ML server dự báo chuỗi thời gian, thống kê phòng, rate control).

### (e) Câu hỏi vấn đáp cần chuẩn bị

1. Isolation Forest hoạt động thế nào? Vì sao điểm bất thường lại có độ dài đường đi ngắn?
2. Vì sao học từ chính máy đó trong 5 phút đầu? Nếu trong 5 phút đầu sinh viên đã gian lận thì sao?
3. Threshold `max(training score) + 0.05` có ý nghĩa gì? Vì sao cần 3 lần liên tiếp?
4. Chronos-Bolt là gì? Vì sao dự báo chuỗi thời gian lại phát hiện được bất thường (giá trị thật ngoài q0.1–q0.9)?
5. Vì sao Chronos chỉ chấm 6 metric (không gồm số process, mất focus)?
6. Vì sao gom batch và gọi bất đồng bộ có timeout? Nếu `ml-service` chết thì sao?
7. Đo hiệu quả thế nào (precision/recall/F1)? Dữ liệu mô phỏng có đại diện cho dữ liệu thật không? Hạn chế?
8. Vì sao chọn `ml.mode` này? Dựa trên số liệu nào?

---

## Đề xuất điều chỉnh khối lượng (chưa áp dụng – chờ cả nhóm quyết)

Chưa đổi quyền sở hữu nào. Nhận xét từ lúc dựng skeleton (vẫn giữ nguyên để cả nhóm quyết):

1. **Nguoi2 nặng nhất.** Gánh toàn bộ nghiệp vụ thi (15 mục) **và** hai giao diện Swing, trong đó Tuần 3 dồn nhiều (Student UI, Teacher UI, SUBMIT, chấm điểm, xuất CSV). Đề xuất: Nguoi1 nhận **Student UI phần kết nối** (nhận `EXAM_START`/`TIME_SYNC`/`EXAM_END`, gửi `ANSWER`/`SUBMIT`, hàng đợi `ANSWER` khi mất mạng) vì liên quan trực tiếp đến mạng; Nguoi2 giữ phần hiển thị.
2. **Nguoi1 nhẹ hơn sau Tuần 1** vì skeleton đã có sẵn TCP, codec, LOGIN, HEARTBEAT. Ngoài mục 1, đề xuất Nguoi1 nhận: viết **chương trình tạo tải nhiều client giả** (phục vụ số liệu 50/500 connection, hỗ trợ đánh giá), viết phần **Testing/Evaluation** của báo cáo, và làm "người tích hợp" cuối Tuần 3.
3. **Nguoi3 có rủi ro kỹ thuật cao:** OSHI khác nhau giữa Windows/Linux và cần quyền với một số thông tin (kết nối, USB). Đề xuất chốt sớm hệ điều hành demo (Windows hay Linux) trong Tuần 1; Nguoi4 hỗ trợ viết test cho Median/MAD (N3-10/11) nếu Nguoi3 trễ.
4. **Nguoi4 dồn việc Tuần 3 và có rủi ro thời gian:** thực nghiệm chỉ còn ~8 ngày, phụ thuộc dữ liệu từ `Recorder`/BASELINE. Đề xuất bắt đầu `Simulator` ngay Tuần 1 (không phụ thuộc ai) để có dữ liệu sớm. Chronos có thể nặng; nếu đo thấy chậm trên máy demo thì báo sớm để nhóm cân nhắc (không tự đổi thiết kế).
5. **Phần báo cáo chưa có người phụ trách rõ:** Introduction, Testing, Conclusion, References (Related Work đã giao Nguoi4). Đề xuất: Nguoi1 phụ trách Testing + Conclusion, Nguoi2 phụ trách Introduction; mỗi người tự ghi nguồn tham khảo của mình rồi gộp.
6. **Cần làm rõ N3-13 "Client-score comparison":** yêu cầu gốc chỉ ghi tên mục. Skeleton hiểu là "so sánh số liệu/điểm của từng máy với trung vị + MAD của phòng". Nếu cả nhóm hiểu khác, cần thống nhất trước khi Tuần 3 bắt đầu.
