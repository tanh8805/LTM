# SPEC – Đặc tả hệ thống

Bài tập lớn môn **Lập trình mạng** – nhóm 4 người – hạn nộp **31/10/2026**.

> Tài liệu này chỉ mô tả những gì đã được duyệt. **Không tự thêm chức năng** ngoài tài liệu này;
> muốn thêm hoặc đổi thì hỏi cả nhóm và cập nhật tài liệu trước khi code.

## 1. Problem

Thi trắc nghiệm trực tuyến khó đảm bảo công bằng: sinh viên có thể mở ứng dụng nhắn tin, điều khiển máy từ xa,
hỏi chatbot AI, cắm USB hoặc phát hotspot để nhận trợ giúp. Giám sát thủ công trong phòng thi nhiều máy là không xuể.

## 2. Objective

Xây dựng **hệ thống thi trắc nghiệm trực tuyến có giám sát gian lận** theo mô hình Client/Server qua mạng:

- Giáo viên tạo đề, tạo ca thi, theo dõi realtime và xem điểm.
- Sinh viên làm bài trên máy mình; mỗi sinh viên nhận đề đã trộn riêng.
- Hệ thống phát hiện gian lận bằng **luật kết hợp ML phát hiện bất thường**.

Về mặt môn học, project thể hiện: giao tiếp mạng (TCP socket), định nghĩa protocol, xử lý nhiều client đồng thời
(virtual thread), xử lý lỗi, kiểm thử và đánh giá thực nghiệm.

## 3. Scope

**Trong phạm vi:** mọi mục trong tài liệu này.

**Công nghệ (quyết định thiết kế của nhóm):**

```text
Java 21 → TCP Socket → Virtual Thread → Gson/JSON → JDBC + SQLite → Swing
```

Nhóm **chủ động chọn Java TCP Socket thuần thay vì framework backend** để tập trung thể hiện kiến thức
Lập trình mạng. Đây là quyết định thiết kế của project, không phải quy định môn học cấm framework.
Muốn thêm thư viện/framework ngoài danh sách dưới đây: **hỏi cả nhóm trước**.

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ, build | Java 21, Maven (multi-module) |
| Mạng | TCP Socket, Virtual Thread (mỗi connection một virtual thread) |
| Giao diện | Swing |
| JSON | Gson |
| Test | JUnit 5 |
| Giám sát hệ thống | OSHI |
| Database | SQLite, `sqlite-jdbc`, JDBC |
| ML phía client | Isolation Forest **tự cài bằng Java** (không dùng thư viện ML Java) |
| ML phía server | Python 3.10+, FastAPI, uvicorn, `chronos-forecasting`, `amazon/chronos-bolt-tiny` (CPU) |

> Khi viết README gốc: phải liệt kê các thư viện ngoài ở trên (Gson, sqlite-jdbc, OSHI, JUnit, FastAPI, uvicorn, chronos-forecasting) theo yêu cầu môn học.

## 4. Teacher (`TeacherMain`)

1. Đăng nhập.
2. Ngân hàng câu hỏi 4 đáp án: thêm / sửa / xóa; nhập từ CSV.
3. Tạo đề: chọn câu thủ công hoặc chọn ngẫu nhiên N câu; đặt thời gian.
4. Tạo ca thi: chọn đề, giờ bắt đầu, thời lượng; danh sách thí sinh (CSV); cấu hình luật giám sát; bắt đầu / kết thúc ca.
5. Màn hình giám sát realtime: danh sách máy, online/offline, cảnh báo **đỏ/vàng** kèm lý do, xem chi tiết một máy.
6. Gửi `NOTICE` tới một máy hoặc tới cả phòng.
7. Xem điểm và xuất CSV.

## 5. Student (`StudentMain`)

1. Đăng nhập bằng mã sinh viên + mật khẩu + mã ca thi.
2. Nhận đề; **câu hỏi và đáp án được trộn riêng cho từng sinh viên**.
3. Làm bài: chuyển câu, chọn đáp án, đánh dấu câu; đồng hồ đếm ngược theo **đồng hồ Server**.
4. Gửi `ANSWER` ngay khi chọn; `SUBMIT` khi nộp bài; **Server tự chốt bài khi hết giờ**.
5. Reconnect bằng token khi mất kết nối và khôi phục bài làm.
6. Giám sát chạy nền (mục 7).

## 6. Server

TCP, mỗi message một dòng JSON, xác thực, token, heartbeat, reconnect, quản lý ca thi, đồng hồ Server,
chấm điểm, giám sát, thống kê phòng, cổng ML, điều khiển tần suất, lưu SQLite.
Chi tiết: `ARCHITECTURE.md` và `PROTOCOL.md`.

## 7. Monitoring (5 luật, chạy ở client)

| # | Luật | Vi phạm khi |
|---|---|---|
| 1 | **Process** | Lúc bắt đầu chụp danh sách process (allowlist). Process mới không nằm trong allowlist, hoặc process thuộc denylist (Zalo, Telegram, Messenger, TeamViewer, AnyDesk) |
| 2 | **Domain/IP** | Các domain `chatgpt.com`, `api.openai.com`, `gemini.google.com` được resolve ra IP lúc bắt đầu. Có kết nối tới các IP đó |
| 3 | **USB** | Có thiết bị USB mới |
| 4 | **Network card** | Có network card mới (hotspot điện thoại, VPN, ...) |
| 5 | **Focus** | Đếm số lần cửa sổ thi mất focus; vượt ngưỡng thì cảnh báo (WARNING) |

Vi phạm được gửi lên Server bằng `VIOLATION`.

Chi tiết cài đặt (đã chốt khi code):

- Process thuộc **denylist** được báo ngay lần đầu thấy. Process **mới ngoài allowlist** chỉ được báo khi thấy ở **2 lần kiểm tra liên tiếp**,
  để tiến trình sống chớp nhoáng (ví dụ `kworker` của Linux, tiến trình con tự thoát) không gây báo động giả.
- Mỗi cặp (loại vi phạm, bằng chứng) chỉ được báo **một lần** trong một ca thi, để không spam Server mỗi chu kỳ.
- Mức cảnh báo ở Server: luật 1-4 → **ĐỎ**, `FOCUS_LOSS` → **VÀNG**.
- Nếu OSHI không đọc được một nguồn số liệu thì client ghi log và bỏ qua luật đó ở lần này, các luật khác vẫn chạy.

**Client metrics:** mỗi 10 giây đo một vector 8 chiều:
KB gửi, KB nhận, số kết nối, số địa chỉ đích khác nhau, % CPU, % RAM, số tiến trình, số lần mất focus.
`KB gửi` và `KB nhận` là **tốc độ KB/giây** (không phải tổng) để so sánh được giữa các chế độ NORMAL / HIGH / BASELINE.

## 8. Client ML – Isolation Forest

- Tự cài bằng Java, nằm ở `exam.common.ml.IsolationForest` (+ `IsolationTree`): 100 cây, mẫu con tối đa 256, seed cố định 42,
  giới hạn chiều cao `ceil(log2 ψ)`, điểm `2^(-E[h]/c(n))`. `common` không import `client`.
- Adapter nằm ở `exam.client.ml` (`IsolationForestScorer` cài `AnomalyScorer`, `AnomalyDetector` quản lý giai đoạn học và chấm).
- **Học từ chính máy đó** trong **5 phút đầu** (cấu hình được: `ml.client.training.seconds`, gửi xuống client qua `RULES_CONFIG`).
- Threshold = `max(training anomaly score) + 0.05`.
- Cảnh báo khi điểm vượt threshold **3 lần liên tiếp**.
- Trong chế độ `BASELINE` Isolation Forest bị tắt (đúng yêu cầu "không ML").
- Kết quả (`anomalyScore`, `anomalous`) gửi kèm `HEARTBEAT` / `METRICS_DETAIL`; Server ghép theo `ml.mode`.

> **Giới hạn đã đo được:** với đúng luật trên (30 mẫu học, threshold = max + 0.05, 3 lần liên tiếp), Isolation Forest rất thận trọng:
> precision ≈ 1.0, FPR ≈ 0 nhưng recall thấp (≈ 0.03–0.22 trên dữ liệu mô phỏng), vì điểm bão hòa quanh mức của các điểm học cực đoan.
> Xem `statics/results/README.md`.

## 9. Server ML – Chronos-Bolt tiny

- Chấm **6 metric**: KB gửi, KB nhận, số kết nối, số địa chỉ đích khác nhau, CPU, RAM. **Không** gồm số tiến trình và số lần mất focus.
- Mỗi 10 giây: lấy 60 điểm gần nhất của **mọi máy**, gom thành **một batch**, gọi `ml-service` **một lần**, **bất đồng bộ**, timeout **3 giây**.
- Lỗi hoặc timeout → **bỏ qua ML**, hệ thống vẫn chạy bình thường (ml-service chưa nạp xong model trả `503` → Server coi như lỗi, bỏ qua ML).
- Đáng ngờ khi giá trị thật nằm ngoài khoảng **q0.1 – q0.9** đủ **3 lần liên tiếp** (`ChronosJudge`).
- `ml-service` dùng `amazon/chronos-bolt-tiny`. Máy cần truy cập được `huggingface.co` lần đầu để tải trọng số
  (hoặc đặt `LTM_ML_MODEL` trỏ tới thư mục model đã tải sẵn). Backend `naive` (`LTM_ML_BACKEND=naive`) chỉ để kiểm thử, không phải Chronos.

## 10. Room statistics

Mỗi `monitor.interval.seconds` (10 giây) Server tính **trung vị (median)** và **MAD** cho 7 metric của cả phòng
(không gồm `focusLostCount`; chỉ tính khi phòng có ít nhất `room.min.machines` = 3 máy online) rồi gửi `ROOM_STATS` xuống sinh viên.

Công thức "lệch quá ngưỡng" (`RoomStats.check`), chỉ xét lệch **lên trên** (gửi/nhận/CPU/... cao hơn phòng):

1. `ratio = giá trị máy / max(median, room.min.median)`.
2. Giá trị phải cách median hơn `room.mad.threshold` (3.5) lần `1.4826 × MAD` (bỏ qua dao động bình thường khi cả phòng vốn phân tán).
3. `ratio ≥ room.warning.multiplier` (3.0) → `YELLOW`, lý do dạng "kbSent gấp 5.2 lần trung vị phòng"; `ratio ≥ room.critical.multiplier` (6.0) → `RED`.

Mọi ngưỡng nằm trong `config/server.properties`.

## 11. Rate control

| Chế độ | Hành vi |
|---|---|
| `NORMAL` | Summary nằm trong `HEARTBEAT`, mỗi 10 giây |
| `HIGH` | Gửi `METRICS_DETAIL` mỗi 2 giây. Tự về `NORMAL` sau **60 giây yên** (không có vi phạm mới; `rate.high.quiet.seconds`) |
| `BASELINE` | Mọi máy gửi detail mỗi 1 giây, **không ML** |

Server đổi chế độ bằng `SET_RATE`.

## 12. Baseline

`BASELINE` dùng để thu dữ liệu nền phục vụ thực nghiệm (ghi vào `data/traces/` bằng `exam.tools.Recorder`),
làm mốc so sánh các chế độ ML.

## 13. `ml.mode`

File `config/ml.properties`, khóa `ml.mode`:

| Giá trị | Ý nghĩa |
|---|---|
| `NONE` | Không dùng ML (**mặc định hiện tại**) |
| `IF` | Chỉ Isolation Forest (client) |
| `CHRONOS` | Chỉ Chronos-Bolt (ml-service) |
| `BOTH_OR` | Đáng ngờ nếu IF **hoặc** Chronos báo bất thường |
| `BOTH_AND` | Đáng ngờ nếu IF **và** Chronos cùng báo bất thường |

Mặc định vẫn là `NONE` vì chưa có số liệu Chronos-Bolt thật để chọn (xem `statics/results/README.md`).
Sau khi chạy `exam.tools.ExperimentRunner` với Chronos thật, Nguoi4 chọn giá trị tốt nhất và sửa `config/ml.properties` (`TODO(Nguoi4)` ở file đó).
Khi đáng ngờ, Server đặt cảnh báo **VÀNG** với lý do bắt đầu bằng `ML (<mode>)` cho giáo viên.

## 14. Dữ liệu mẫu (SQLite)

Server tự tạo `data/exam.db` (đã `.gitignore`) và nạp dữ liệu mẫu khi database còn trống:
**1 giáo viên, 10 sinh viên, 30 câu hỏi kiến thức chung.**

| Vai trò | Tài khoản | Mật khẩu | Ghi chú |
|---|---|---|---|
| Giáo viên | `gv01` | `teacher123` | Giảng viên Mẫu |
| Sinh viên | `SV001` … `SV010` | `123456` (chung) | SV001 = Nguyễn Văn An, SV002 = Trần Thị Bình, ... |

Mật khẩu được lưu dạng hash PBKDF2 (không có chuỗi mật khẩu rõ trong database). Sinh viên đăng nhập kèm **mã ca thi**;
Server kiểm tra ca có tồn tại, chưa kết thúc và sinh viên có trong danh sách thí sinh. Dữ liệu mẫu **không** có ca thi:
giáo viên tạo đề và ca thi (mã ca tự sinh hoặc tự đặt, ví dụ `CA001`) trong `TeacherMain`.

> Schema đã đổi so với skeleton. Nếu còn file `data/exam.db` cũ, **xóa nó** rồi chạy lại Server để tạo lại và nạp dữ liệu mẫu.

File dữ liệu: `source/server/src/main/resources/schema.sql` và `sample_data.sql` (Nguoi2).

## 15. Trạng thái triển khai và hạn chế đã biết

**Đã triển khai và kiểm thử:** TCP + virtual thread, đăng nhập hash, token/RECONNECT, heartbeat và timeout, ngân hàng câu hỏi + CSV,
tạo đề (tay / ngẫu nhiên), ca thi, trộn đề riêng từng sinh viên, đồng hồ Server, tự chốt bài, chấm điểm, xuất CSV, giám sát 5 luật bằng OSHI,
thống kê phòng median/MAD, ALERT, rate control NORMAL/HIGH/BASELINE, Isolation Forest, ml-service Chronos + fallback, `MlGateway`,
Recorder/Simulator/ExperimentRunner, giao diện Swing Teacher/Student, kịch bản end-to-end 20 bước.

**Hạn chế đã biết (không phải lỗi chưa làm):**

- **Chưa chạy với trọng số Chronos-Bolt thật** (môi trường phát triển chặn `huggingface.co`). Đường ống đã kiểm thử đầy đủ với model Chronos khởi tạo ngẫu nhiên
  và với backend `naive`; số liệu Chronos trong `statics/results/` là của backend `naive`, chỉ để kiểm thử đường ống.
- Isolation Forest theo đúng luật của đề có recall thấp trên dữ liệu mô phỏng (mục 8).
- `ml.mode` mặc định `NONE` cho tới khi có số liệu Chronos thật (`TODO(Nguoi4)`).
- Đường truyền là TCP thuần (không TLS) theo quyết định thiết kế của nhóm; mật khẩu chỉ được bảo vệ ở phía lưu trữ (hash).
- Các nhận diện GitHub `@REPLACE-ME-NGUOI1/3/4` trong `.github/CODEOWNERS` chưa điền vì chưa biết tài khoản.
