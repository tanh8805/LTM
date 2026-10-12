# LTM - Hệ thống thi trắc nghiệm có giám sát

Ứng dụng thi trắc nghiệm trong mạng LAN (bài tập lớn môn Lập trình mạng). Giáo viên chọn file đề và bắt đầu thi, sinh viên làm bài trên client Windows.
Trong giờ thi, client giám sát bằng luật (app ngoài whitelist, kết nối tới địa chỉ bị cấm, cắm USB, rời cửa sổ thi) và gửi dữ liệu về server.
Server gửi dữ liệu cả phòng sang service Python dùng Isolation Forest để tìm máy có hành vi khác biệt so với số đông; giám thị là người quyết định có gian lận hay không.

Đặc tả đầy đủ: [docs/AGENT_BRIEF.md](docs/AGENT_BRIEF.md).

## 1. Kiến trúc

```
  Client Windows (StudentApp + Monitor)  x N
        |   ^
        |   |  UDP 9999: EXAM_DISCOVER / EXAM_SERVER <cổng>   (tìm server)
        v   |  TCP 5000: mỗi tin nhắn là một dòng JSON UTF-8
  +------------------------------------------+
  | Server (ExamServer + TeacherWindow)      |  Linux hoặc Windows
  +------------------------------------------+
        |   ^
        v   |  HTTP 8000 (chỉ localhost): POST /score
  +------------------------------------------+
  | Service ML (FastAPI + Isolation Forest)  |
  +------------------------------------------+
```

Client chỉ phát hiện và báo cáo: không chặn mạng, không kill tiến trình, không cần quyền admin. Đáp án đúng chỉ nằm ở server.

## 2. Phân công

| Người | File | Cần hiểu |
|---|---|---|
| Cả nhóm | `src/main/java/exam/common/Protocol.java` | - Mỗi tin nhắn là một dòng JSON, luôn có `type`<br>- Gson tự escape ký tự xuống dòng nên không bao giờ vỡ dòng |
| Người 1 | `StudentApp.java`, `ServerConnection.java`, `ServerFinder.java` | - Luồng màn hình: đăng nhập, PRECHECK, chờ, làm bài, kết quả<br>- Tự kết nối lại mỗi 5 giây và đăng nhập lại; vì sao chỉ tính "rời cửa sổ" khi `getOppositeWindow() == null`<br>- Tìm server bằng UDP broadcast tới từng card mạng |
| Người 2 | `Monitor.java` | - Baseline lấy ở PRECHECK, so sánh mỗi 3 giây; vì sao cần `SYSTEM_PROCESSES`<br>- 6 feature mỗi 30 giây và cách reset cửa sổ<br>- Tách phần lấy dữ liệu (chạy lệnh) khỏi `processSnapshot` để test được trên Linux |
| Người 3 | `ExamServer.java`, `ClientHandler.java`, `TeacherWindow.java` | - `Session` giữ trạng thái khi mất kết nối; `trySendExam` là điểm gửi đề duy nhất<br>- Chống nhầm session cũ và mới (so sánh socket); watchdog 90 giây<br>- Chấm điểm, `results.csv` (UTF-8 có BOM) |
| Người 4 | `MlClient.java`, `ml/ml_service.py`, `ml/simulate_clients.py` | - Isolation Forest không giám sát, fit trên chính dữ liệu buổi thi; điểm chuẩn hoá min-max và 3 cửa sổ gần nhất<br>- Vì sao `anomaly` có thêm điều kiện điểm ≥ 0.7 (xem mục 9)<br>- Gọi HTTP ngoài khối `synchronized` để ML chậm không treo server |

## 3. Giao thức

**UDP 9999 (tìm server):** client gửi `EXAM_DISCOVER` tới `255.255.255.255`, broadcast từng card mạng và `127.0.0.1`; server trả `EXAM_SERVER <cổng TCP>` về đúng người gửi. Hết 3 giây không có trả lời hợp lệ thì client báo không tìm thấy.

**TCP 5000:** một dòng JSON UTF-8 mỗi tin nhắn.

| Chiều | type | Trường | Ghi chú |
|---|---|---|---|
| C→S | `LOGIN` | `studentId`, `name` | |
| S→C | `LOGIN_OK` | `rules` | Luật do server gửi xuống |
| S→C | `LOGIN_FAIL` | `reason` | |
| C→S | `PRECHECK_RESULT` | `ok`, `issues` | Có thể gửi nhiều lần |
| S→C | `EXAM_START` | `title`, `remainingSeconds`, `questions` | Câu hỏi không có `answer` |
| C→S | `VIOLATION` | `kind` (`APP`/`NET`/`USB`), `detail`, `time` | |
| C→S | `FEATURES` | `window`, `values` (đúng 6 số) | Mỗi 30 giây, đồng thời là heartbeat |
| C→S | `SUBMIT` | `answers` | Khoá = id câu, giá trị = chỉ số lựa chọn từ 0 |
| S→C | `EXAM_END` | `reason` | Client nhận được thì tự `SUBMIT` |
| S→C | `RESULT` | `score`, `correct`, `total` | |

Dòng không phải JSON object, `type` lạ, và mọi tin (trừ `LOGIN`) trước khi đăng nhập đều bị bỏ qua, không đóng kết nối.

**HTTP 8000:** `POST /score` với `{"samples": [{"studentId", "window", "values"}]}` trả `{"status": "ok", "results": [{"studentId", "score", "anomaly"}]}`, hoặc `{"status": "not_enough_data", "results": []}` khi dưới 20 mẫu. `values` sai độ dài thì trả 422.

Sáu giá trị của `values`: số tiến trình mới, số lần rời cửa sổ, số giây rời cửa sổ, số IP đích mới, số vi phạm, số lần chọn/đổi đáp án.

## 4. Cài đặt

- **Máy server:** JDK 17+ và Maven; Python 3.10+.
- **Máy client (Windows):** chỉ cần Java 17+ (JRE là đủ), chép `exam.jar` sang.
- **Python:**
  ```bash
  python3 -m venv ml/.venv
  source ml/.venv/bin/activate          # Windows: ml\.venv\Scripts\activate
  pip install -r ml/requirements.txt
  ```

## 5. Build và chạy

Chạy từ thư mục gốc repo, theo thứ tự ML, server, client:

```bash
mvn -q package                                                   # tạo target/exam.jar (đã gói kèm Gson)
cd ml && uvicorn ml_service:app --host 127.0.0.1 --port 8000     # 1. service ML
java -cp target/exam.jar exam.server.ExamServer                  # 2. server (đọc config/rules.json)
java -cp target/exam.jar exam.client.StudentApp                  # 3. client
```

Chạy test: `mvn -q test` và `cd ml && python -m pytest -q`.
Giả lập thêm máy để demo: `cd ml && python simulate_clients.py --count 5 --cheater` (thêm `--interval 3` để thấy kết quả nhanh).
Kết quả thi được ghi vào `results.csv` ở thư mục chạy server (mở bằng Excel được tiếng Việt).

## 6. Mạng

- **Làm ở nhà:** ZeroTier (gói Personal miễn phí), 4 máy join cùng một network. Không dùng Tailscale (không hỗ trợ broadcast) hay Radmin VPN (không có bản Linux). Nếu UDP không tìm được server, nhập IP của máy server (hiện ở thanh trên cùng của cửa sổ giám thị).
- **Hôm demo:** cả 4 máy bắt chung một hotspot điện thoại.
- **Firewall Fedora:** `sudo firewall-cmd --add-port=5000/tcp --add-port=9999/udp` (thêm `--permanent` rồi `--reload` nếu muốn giữ).
- **Firewall Windows:** cho phép Java qua tường lửa (cả mạng Private và Public) khi được hỏi.

## 7. Kịch bản demo (khoảng 10 phút)

1. Máy Linux chạy service ML, server, và `python simulate_clients.py --count 5`.
2. Máy A, B, C (Windows) đăng nhập. Máy B để Zalo mở: PRECHECK lỗi, đóng Zalo, Kiểm tra lại, đạt.
3. Giáo viên bấm Bắt đầu, đề hiện trên cả 3 máy.
4. Máy A làm bài bình thường.
5. Máy B cắm USB: dòng của máy B tô đỏ, vi phạm USB.
6. Máy C mở Chrome vào chatgpt.com: vi phạm APP và NET.
7. Máy C chuyển qua lại với Calculator (nằm trong whitelist) liên tục rồi trả lời dồn dập. Không luật nào bắt được, nhưng sau 1-2 phút cột Bất thường của máy C cao hẳn so với cả phòng.
8. Tắt Wi-Fi máy A: `Mất kết nối`. Bật lại: máy A tự vào lại, làm tiếp với thời gian còn lại.
9. Kết thúc thi, mở `results.csv`.

## 8. Checklist test thủ công trên Windows

Phần quét tiến trình, mạng, USB chỉ chạy trên Windows nên chưa kiểm tra tự động được. Cần thử trên máy thật:

- [ ] PRECHECK với Zalo mở: báo `Đang mở ứng dụng bị cấm: zalo.exe`; đóng Zalo rồi Kiểm tra lại thì đạt.
- [ ] PRECHECK với Chrome mở: báo `Đang mở ứng dụng bị cấm: chrome.exe`.
- [ ] PRECHECK khi đang cắm USB: báo `Đang cắm USB: <ổ>` (kiểm tra lệnh PowerShell `Get-CimInstance Win32_LogicalDisk` chạy được).
- [ ] Máy sạch (không mở app lạ) làm bài 5 phút: **không** có vi phạm APP nào do tiến trình nền của Windows hay của chính client.
- [ ] Cắm USB khi đang thi: dòng tô đỏ, vi phạm `USB`.
- [ ] Vào chatgpt.com khi đang thi: vi phạm `NET` (và `APP` nếu mở bằng trình duyệt mới).
- [ ] Chuyển qua lại với Calculator: không có vi phạm, nhưng đếm `focusLostCount` và `focusLostSeconds`.
- [ ] Hộp xác nhận "Nộp bài?" **không** bị tính là rời cửa sổ.
- [ ] Tắt Wi-Fi rồi bật lại: client báo "Mất kết nối, đang thử lại...", tự vào lại, giữ đáp án, thời gian còn lại đúng.
- [ ] Tìm server qua UDP (để trống ô IP), kể cả máy có ZeroTier (nhiều card mạng).
- [ ] Nhập IP server thủ công.
- [ ] Windows ngôn ngữ khác tiếng Anh: `netstat -no` in trạng thái `ESTABLISHED` bằng chữ gì (luật NET đang đọc chữ tiếng Anh).

## 9. Giới hạn đã biết

- **Luật NET không bắt được 100%:** tên miền được phân giải một lần lúc bắt đầu thi; trang dùng CDN đổi IP sẽ lọt. Trạng thái kết nối được đọc theo chữ tiếng Anh của `netstat`.
- **Isolation Forest chỉ phát hiện "khác số đông"**, không biết ai gian lận. Giao diện chỉ ghi "Bất thường"; giám thị quyết định.
- **Điều kiện `anomaly` khác đặc tả gốc:** `predict() == -1` của Isolation Forest gắn cờ 25-88% sinh viên bình thường khi feature là số đếm nhỏ, nên service chỉ đánh dấu khi điểm chuẩn hoá cũng ≥ 0.7 (`FLAG_MIN_SCORE` trong `ml/ml_service.py`; chi tiết và số liệu ở PR Task 8). Điểm chuẩn hoá là tương đối theo phòng, nên phòng không có ai lệch vẫn có thể có vài máy ở dải cao nhất bị đánh dấu.
- **Đáp án chỉ lưu trong bộ nhớ client:** rớt mạng mà không tắt app thì vẫn giữ; tắt hẳn app thì mất.
- **Chưa có xác thực và mã hoá:** mã SV không có mật khẩu, kết nối TCP/UDP không mã hoá. Chỉ dùng trong mạng tin cậy.
- **Phụ thuộc cấu hình Windows:** cần PowerShell và `tasklist`/`netstat` có sẵn (mặc định đều có).
- **Kích thước file:** `ExamServer.java`, `Monitor.java`, `StudentApp.java` dài hơn giới hạn 250 dòng của đặc tả vì API bắt buộc nằm hết trong một file; cần nhóm quyết định có tách file hay chấp nhận.
