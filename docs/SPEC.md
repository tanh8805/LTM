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

**Client metrics:** mỗi 10 giây đo một vector 8 chiều:
KB gửi, KB nhận, số kết nối, số địa chỉ đích khác nhau, % CPU, % RAM, số tiến trình, số lần mất focus.

## 8. Client ML – Isolation Forest

- Tự cài bằng Java, nằm ở `exam.common.ml.IsolationForest`.
- **Học từ chính máy đó** trong **5 phút đầu** (cấu hình được: `ml.client.training.seconds`).
- Threshold = `max(training anomaly score) + 0.05`.
- Cảnh báo khi điểm vượt threshold **3 lần liên tiếp**.

## 9. Server ML – Chronos-Bolt tiny

- Chấm **6 metric**: KB gửi, KB nhận, số kết nối, số địa chỉ đích khác nhau, CPU, RAM. **Không** gồm số tiến trình và số lần mất focus.
- Mỗi 10 giây: lấy 60 điểm gần nhất của **mọi máy**, gom thành **một batch**, gọi `ml-service` **một lần**, **bất đồng bộ**, timeout **3 giây**.
- Lỗi hoặc timeout → **bỏ qua ML**, hệ thống vẫn chạy bình thường.
- Đáng ngờ khi giá trị thật nằm ngoài khoảng **q0.1 – q0.9** đủ **3 lần liên tiếp**.

## 10. Room statistics

Server tính **trung vị (median)** và **MAD** cho từng metric của cả phòng.
Máy nào lệch quá ngưỡng → `WARNING`, lý do dạng "gấp X lần trung vị phòng".
Server gửi `ROOM_STATS` xuống client để client so sánh điểm của mình với phòng.

> TODO(Nguoi3): chốt công thức và ngưỡng "lệch quá ngưỡng" (ví dụ dựa trên MAD) và ghi vào đây.

## 11. Rate control

| Chế độ | Hành vi |
|---|---|
| `NORMAL` | Summary nằm trong `HEARTBEAT`, mỗi 10 giây |
| `HIGH` | Gửi `METRICS_DETAIL` mỗi 2 giây. Tự về `NORMAL` sau **60 giây yên** |
| `BASELINE` | Mọi máy gửi detail mỗi 1 giây, **không ML** |

Server đổi chế độ bằng `SET_RATE`.

## 12. Baseline

`BASELINE` dùng để thu dữ liệu nền phục vụ thực nghiệm (ghi vào `data/traces/` bằng `exam.tools.Recorder`),
làm mốc so sánh các chế độ ML.

## 13. `ml.mode`

File `config/ml.properties`, khóa `ml.mode`:

| Giá trị | Ý nghĩa |
|---|---|
| `NONE` | Không dùng ML (mặc định ở skeleton) |
| `IF` | Chỉ Isolation Forest (client) |
| `CHRONOS` | Chỉ Chronos-Bolt (ml-service) |
| `BOTH_OR` | Đáng ngờ nếu IF **hoặc** Chronos báo bất thường |
| `BOTH_AND` | Đáng ngờ nếu IF **và** Chronos cùng báo bất thường |

Nguoi4 chọn giá trị mặc định tốt nhất sau thực nghiệm (`exam.tools.ExperimentRunner`).

## 14. Dữ liệu mẫu (SQLite)

Server tự tạo `data/exam.db` (đã `.gitignore`) và nạp dữ liệu mẫu khi database còn trống:
**1 giáo viên, 10 sinh viên, 30 câu hỏi kiến thức chung.**

| Vai trò | Tài khoản | Mật khẩu | Ghi chú |
|---|---|---|---|
| Giáo viên | `gv01` | `teacher123` | Giảng viên Mẫu |
| Sinh viên | `SV001` … `SV010` | `123456` (chung) | SV001 = Nguyễn Văn An, SV002 = Trần Thị Bình, ... |

Sinh viên đăng nhập kèm **mã ca thi**. Ở skeleton chưa có ca thi trong database và Server chưa kiểm tra mã ca,
nên dùng tạm `CA001`. Xóa `data/exam.db` rồi chạy lại Server để nạp lại dữ liệu mẫu.

File dữ liệu: `source/server/src/main/resources/schema.sql` và `sample_data.sql` (Nguoi2).

## 15. Hạn chế của skeleton (phiên dựng khung này)

Phiên này **chỉ dựng khung**. Đã chạy được: Server mở TCP + SQLite + dữ liệu mẫu; LOGIN/LOGIN_OK/LOGIN_FAIL;
HEARTBEAT/HEARTBEAT_ACK (Server log); phát hiện client im lặng 30 giây; chuyển NOTICE của giáo viên; ml-service `/health` và `/score` giả.

**Chưa triển khai** (có `TODO(NguoiX)` tại chỗ cần làm):

- Nghiệp vụ thi: ngân hàng câu hỏi, tạo đề, ca thi, trộn câu/đáp án, chấm điểm, tự chốt bài, xuất CSV.
- `RECONNECT`, tự nối lại phía client, khôi phục bài.
- Giám sát thật: OSHI, 5 luật, focus, `VIOLATION`, `METRICS_DETAIL`, `ROOM_STATS`, `ALERT`.
- `RateController` thật (`SET_RATE`, về `NORMAL` sau 60 giây).
- Isolation Forest, Chronos, `MlGateway`; `/score` chỉ trả dữ liệu giả cố định.
- `Recorder`, `Simulator`, `ExperimentRunner`.
- Giao diện Swing chỉ hiện trạng thái kết nối và đăng nhập.
- Mật khẩu đang lưu dạng chữ thường trong dữ liệu mẫu (TODO(Nguoi2): đổi sang hash).
