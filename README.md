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

| Người | File | Mảng phụ trách | Cần hiểu |
|---|---|---|---|
| Cả nhóm | `exam/common/Protocol.java` | Giao thức chung | - Mỗi tin nhắn là một dòng JSON, luôn có `type`<br>- Gson tự escape ký tự xuống dòng nên không bao giờ vỡ dòng |
| 1 | `StudentApp`, `StudentScreens` (+ `Theme`, `TopBar`, `LoginPanel`, `PrecheckPanel`, `ExamPanel`, `NumberButton`, `OptionRow`, `ResultPanel`), `ServerConnection`, `ServerFinder` | Client sinh viên: kết nối TCP, tìm server UDP, giao diện làm bài (FlatLaf) | - Luồng màn hình: đăng nhập, PRECHECK, chờ, làm bài, kết quả<br>- `StudentApp` chỉ lo logic, `StudentScreens` ghép các panel; vì sao dùng callback<br>- Tự kết nối lại mỗi 5 giây và đăng nhập lại; vì sao chỉ tính "rời cửa sổ" khi `getOppositeWindow() == null`<br>- Tìm server bằng UDP broadcast tới từng card mạng |
| 2 | `Monitor`, `AppRules`, `WindowsProbe`, `Diagnose`, `TeacherWindow`, `config/rules.json` | Giám sát: phát hiện vi phạm ở client và hiển thị cho giám thị | - Baseline lấy ở PRECHECK, so sánh mỗi 3 giây; vì sao chỉ xét app có cửa sổ và cần `SYSTEM_PROCESSES`<br>- 6 feature mỗi 30 giây và cách reset cửa sổ<br>- `WindowsProbe` chạy lệnh Windows; `Monitor` chỉ chứa luật nên test được trên Linux |
| 3 | `ExamServer`, `ExamRoom`, `Session`, `ExamFile`, `ClientHandler` | Server: nhận kết nối, luật phòng thi, chấm điểm, lưu kết quả | - `ExamRoom` giữ mọi trạng thái và mọi phương thức đổi trạng thái đều `synchronized`; `trySendExam` là điểm gửi đề duy nhất<br>- Chống nhầm session cũ và mới (so sánh socket); watchdog 90 giây<br>- Chấm điểm, `ket-qua/<kỳ thi>/` gồm `results.csv`, `events.csv`, `server.log` (UTF-8 có BOM cho hai file CSV) |
| 4 | `ml/ml_service.py`, `ml/simulate_clients.py`, `MlClient` | ML: Isolation Forest, giả lập phòng thi, nối Java với Python | - Isolation Forest không giám sát, fit trên chính dữ liệu buổi thi; điểm chuẩn hoá min-max và 3 cửa sổ gần nhất<br>- Vì sao `anomaly` có thêm điều kiện điểm ≥ 0.7 (xem mục 9)<br>- Gọi HTTP ngoài khối `synchronized` để ML chậm không treo server |

Cấu trúc mã nguồn (mỗi file Java trong `src/main` dài khoảng 280 dòng trở xuống; cần thì tách file mới):

```
src/main/java/exam/
  common/   Protocol
  client/   StudentApp  StudentScreens  Theme  TopBar  LoginPanel  PrecheckPanel  ExamPanel  NumberButton  OptionRow  ResultPanel
            ServerConnection  ServerFinder  Monitor  AppRules  WindowsProbe  Diagnose  Debug
  server/   ExamServer  ExamRoom  Session  ExamFile  ClientHandler  TeacherWindow  MlClient
ml/         ml_service.py  simulate_clients.py  test_ml_service.py
config/     rules.json        exams/   sample_exam.json
```

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
- **Thư viện Java** (Maven tự tải, shade plugin gói hết vào `exam.jar` nên client chỉ cần một file): Gson 2.11.0 (JSON) và **FlatLaf 3.7.2** (giao diện Swing hiện đại; `Theme.install()` ở client và `FlatLightLaf.setup()` ở server).
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
Mỗi kỳ thi có một thư mục riêng `ket-qua/<yyyyMMdd-HHmmss>_<tên đề không dấu>/` (tạo khi giáo viên bấm Bắt đầu thi, nằm ở thư mục chạy server), gồm `results.csv`, `events.csv` và `server.log`. Hai file CSV là UTF-8 có BOM nên Excel đọc được tiếng Việt. Không còn `results.csv` ở thư mục gốc.
`events.csv` có các cột `time,studentId,name,type,detail`, mỗi sự kiện ghi và flush ngay một dòng; sự kiện xảy ra trước khi bấm Bắt đầu (đăng nhập, PRECHECK) được giữ trong bộ nhớ và ghi vào file khi thư mục được tạo. Các `type`: `LOGIN`, `RECONNECT`, `LOGIN_FAIL`, `PRECHECK_FAIL` (detail là danh sách vấn đề), `PRECHECK_OK`, `EXAM_SENT`, `VIOLATION`, `ANOMALY_ON`, `ANOMALY_OFF`, `DISCONNECT`, `SUBMIT`, `EXAM_END` (sự kiện của cả phòng, `studentId` rỗng). `server.log` là toàn bộ log của cửa sổ giám thị.
Trên cửa sổ giám thị, nhấp đúp vào một dòng để xem lịch sử của thí sinh đó; nút **Mở thư mục kết quả** mở thư mục của kỳ thi (không mở được thì hiện đường dẫn).
`results.csv` có thêm cột `samples` (số FEATURES server đã nhận từ máy đó) và `flaggedCount` (số lần chấm mà máy bị gắn cờ).
Bảng giám thị có cột **Mẫu**; log ghi `Bất thường: <mã SV> (<điểm>)` / `Hết bất thường: <mã SV>` khi đổi trạng thái và `ML: chưa đủ dữ liệu (n/20 mẫu)` khi n đổi.
Debug client: thêm `-Dexam.debug=true` (`java -Dexam.debug=true -cp exam.jar exam.client.StudentApp`) để in thời gian mỗi lần quét và nội dung mỗi FEATURES ra stderr.

### Luật ứng dụng và khi bị báo vi phạm oan

Client chỉ kiểm soát **app có cửa sổ**; tiến trình nền không bao giờ là vi phạm (chỉ được đếm vào feature `newProcesses`). Tên so khớp không phân biệt hoa thường và bỏ đuôi `.exe` (`Antigravity.exe` khớp `antigravity`).

| Thời điểm | Trường hợp | Kết quả |
|---|---|---|
| PRECHECK | App trong `blacklistApps` đang chạy | Không đạt: `Đang mở ứng dụng bị cấm: <tên>` |
| PRECHECK | App có cửa sổ, không thuộc `whitelistApps`/`allowedWindowApps` | Không đạt: `Hãy đóng ứng dụng: <tên> (<tiêu đề>)` |
| Trong giờ thi | App trong blacklist xuất hiện | Vi phạm `APP` |
| Trong giờ thi | Tiến trình mới có cửa sổ, không được phép | Vi phạm `APP`, detail `<tên> (<tiêu đề>)` |
| Trong giờ thi | Tiến trình mới không có cửa sổ | Không vi phạm, chỉ đếm vào `newProcesses` |
| Mọi lúc | Thuộc `ignoreProcesses` hoặc `SYSTEM_PROCESSES` | Bỏ qua hoàn toàn |

Luật nằm trong `config/rules.json` **ở server** và được gửi xuống client lúc đăng nhập, nên chỉnh luật **không cần build lại hay gửi lại jar**; chỉ cần khởi động lại server. Các khoá: `blacklistApps`, `whitelistApps`, `allowedWindowApps`, `ignoreProcesses`, `blockedDomains`.

**Khi bị báo vi phạm oan:**
1. Trên máy Windows bị báo, chạy `java -cp exam.jar exam.client.Diagnose` (khi máy đang ở trạng thái thi bình thường). Công cụ ghi file `chan-doan-<tên máy>.txt`.
2. Mở file: mục "App đang có cửa sổ" cho biết app nào làm PRECHECK không đạt hoặc bị báo `APP`; mục "Tiến trình KHÔNG có cửa sổ" có sẵn một dòng JSON các tiến trình nền.
3. Thêm tên (chỉ những tên chắc chắn là phần mềm lành) vào `ignoreProcesses` (bỏ qua hẳn) hoặc `allowedWindowApps` (cho phép có cửa sổ) trong `config/rules.json` ở server.
4. Khởi động lại server; sinh viên đăng nhập lại là nhận luật mới.

### Giao diện

Ảnh chụp trong `statics/results/ui/` (chụp bằng Xvfb trên Linux; ảnh PRECHECK, đang tìm server và mất kết nối dùng dữ liệu mẫu).

| Đăng nhập | PRECHECK không đạt |
|---|---|
| ![Đăng nhập](statics/results/ui/01-dang-nhap.png) | ![PRECHECK](statics/results/ui/02-precheck-khong-dat.png) |

| Chờ bắt đầu | Làm bài |
|---|---|
| ![Chờ](statics/results/ui/03-cho-bat-dau.png) | ![Làm bài](statics/results/ui/04-lam-bai.png) |

| Mất kết nối | Kết quả |
|---|---|
| ![Mất kết nối](statics/results/ui/05-mat-ket-noi.png) | ![Kết quả](statics/results/ui/07-ket-qua.png) |

Hộp xác nhận nộp bài: [06-xac-nhan-nop-bai.png](statics/results/ui/06-xac-nhan-nop-bai.png). Cửa sổ giám thị (dòng bị gắn cờ tô đỏ nhạt, dòng có vi phạm tô cam nhạt): [08-giam-thi.png](statics/results/ui/08-giam-thi.png).
Phím tắt khi làm bài: `1`-`9` hoặc `A`-`I` chọn đáp án, mũi tên trái/phải chuyển câu.

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
9. Kết thúc thi, mở thư mục `ket-qua/<kỳ thi>/` xem `results.csv` và `events.csv`.

## 8. Checklist test thủ công trên Windows

Phần quét tiến trình, mạng, USB chỉ chạy trên Windows nên chưa kiểm tra tự động được. Cần thử trên máy thật:

- [ ] PRECHECK với Zalo mở: báo `Đang mở ứng dụng bị cấm: zalo.exe`; đóng Zalo rồi Kiểm tra lại thì đạt.
- [ ] PRECHECK với Chrome mở: báo `Đang mở ứng dụng bị cấm: chrome.exe`.
- [ ] PRECHECK khi đang cắm USB: báo `Đang cắm USB: <ổ>` (kiểm tra lệnh PowerShell `Get-CimInstance Win32_LogicalDisk` chạy được).
- [ ] Chạy `java -cp exam.jar exam.client.Diagnose` trên mỗi máy Windows và gửi file `chan-doan-<tên máy>.txt` (thời gian `tasklist`/`netstat`/PowerShell, app có cửa sổ, tiến trình nền).
- [ ] Máy sạch (không mở app lạ) làm bài 5 phút: **không** có vi phạm APP nào do tiến trình nền của Windows hay của chính client.
- [ ] App micro, app nền (OneDrive, driver âm thanh, Phone Link...) đang chạy: không bị báo vi phạm.
- [ ] Antigravity mở **trước** lúc đăng nhập: PRECHECK không đạt; mở **trong** giờ thi: vi phạm `APP`.
- [ ] Calculator vẫn dùng được (không vi phạm, cửa sổ UWP nằm dưới `ApplicationFrameHost`).
- [ ] Cột **Mẫu** của máy Windows tăng đều 30 giây một lần; có điểm Bất thường sau khi cả phòng đủ 20 mẫu (bật `-Dexam.debug=true` để xem thời gian quét).
- [ ] Giao diện mới hiển thị đúng tiếng Việt có dấu, không vỡ bố cục ở độ phóng to 125%/150% của Windows.
- [ ] Cắm USB khi đang thi: dòng tô đỏ, vi phạm `USB`.
- [ ] Vào chatgpt.com khi đang thi: vi phạm `NET` (và `APP` nếu mở bằng trình duyệt mới).
- [ ] Chuyển qua lại với Calculator: không có vi phạm, nhưng đếm `focusLostCount` và `focusLostSeconds`.
- [ ] Hộp xác nhận "Nộp bài?" **không** bị tính là rời cửa sổ.
- [ ] Tắt Wi-Fi rồi bật lại: client báo "Mất kết nối, đang thử lại...", tự vào lại, giữ đáp án, thời gian còn lại đúng.
- [ ] Tìm server qua UDP (để trống ô IP), kể cả máy có ZeroTier (nhiều card mạng).
- [ ] Nhập IP server thủ công.
- [ ] Windows ngôn ngữ khác tiếng Anh: luật NET vẫn bắt được kết nối tới tên miền bị cấm (parser không đọc cột trạng thái, chỉ cần `netstat -no` có đúng 5 cột).

## 9. Giới hạn đã biết

- **Luật app chỉ xét tiến trình mới có cửa sổ:** app đã chạy sẵn lúc PRECHECK (đạt) rồi mới mở cửa sổ trong giờ thi thì không bị bắt; tiến trình nền có thể chạy ngầm mà không bị báo. App UWP (Calculator, Settings, app từ Store) hiện cửa sổ dưới tiến trình `ApplicationFrameHost` nên chỉ phân biệt được qua tiêu đề cửa sổ. Cửa sổ dòng lệnh (`cmd`, `powershell`, Windows Terminal) được phép mở vì sinh viên dùng nó để chạy client. Cửa sổ chỉ được hỏi trong khoảng 10 lần quét (khoảng 30 giây) sau khi tiến trình xuất hiện.
- **Luật NET không bắt được 100%:** tên miền được phân giải một lần lúc bắt đầu thi; trang dùng CDN đổi IP sẽ lọt. Cột trạng thái của `netstat` không được đọc (để chạy được trên Windows mọi ngôn ngữ) nên cả kết nối đã đóng (`TIME_WAIT`) cũng được tính.
- **Isolation Forest chỉ phát hiện "khác số đông"**, không biết ai gian lận. Giao diện chỉ ghi "Bất thường"; giám thị quyết định.
- **Điều kiện `anomaly` khác đặc tả gốc:** `predict() == -1` của Isolation Forest gắn cờ 25-88% sinh viên bình thường khi feature là số đếm nhỏ, nên service chỉ đánh dấu khi điểm chuẩn hoá cũng ≥ 0.7 (`FLAG_MIN_SCORE = 0.7` trong `ml/ml_service.py`; chi tiết và số liệu ở PR Task 8). Điểm chuẩn hoá là tương đối theo phòng, nên phòng không có ai lệch vẫn có thể có vài máy ở dải cao nhất bị đánh dấu.
- **Điểm Bất thường cần ít nhất 20 mẫu trong cả phòng** (`MIN_SAMPLES` trong `ml/ml_service.py`). Mỗi máy gửi 1 mẫu / 30 giây, nên phòng ít máy phải đợi lâu mới có điểm (4 máy: khoảng 150 giây, điểm đầu tiên xuất hiện ở lần chấm 30 giây kế tiếp). Cột Mẫu và log `ML: chưa đủ dữ liệu` cho biết đang ở đâu. Chạy thêm `simulate_clients.py` để đủ mẫu sớm hơn.
- **Đáp án chỉ lưu trong bộ nhớ client:** rớt mạng mà không tắt app thì vẫn giữ; tắt hẳn app thì mất.
- **Chưa có xác thực và mã hoá:** mã SV không có mật khẩu, kết nối TCP/UDP không mã hoá. Chỉ dùng trong mạng tin cậy.
- **Phụ thuộc cấu hình Windows:** cần PowerShell và `tasklist`/`netstat` có sẵn (mặc định đều có).

## 10. Hướng dẫn đọc code

Mỗi người có một file hướng dẫn riêng (phụ trách gì, thứ tự đọc, lần theo một luồng chạy thật, kiến thức để trả lời thầy, 3 câu thầy có thể hỏi và cách sửa):

- [Người 1: client sinh viên](docs/huong-dan-doc-code/nguoi-1.md)
- [Người 2: giám sát và giao diện giám thị](docs/huong-dan-doc-code/nguoi-2.md)
- [Người 3: server và phòng thi](docs/huong-dan-doc-code/nguoi-3.md)
- [Người 4: ML và giả lập](docs/huong-dan-doc-code/nguoi-4.md)
