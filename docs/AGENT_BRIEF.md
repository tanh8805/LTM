# Tài liệu giao việc cho agent: Hệ thống thi trắc nghiệm có giám sát

> Dành cho agent lập trình. Đọc **toàn bộ** tài liệu trước khi làm. Thực hiện các Task ở Phần 5 **theo đúng thứ tự**. Mỗi Task là **một nhánh Git riêng**, làm xong thì mở Pull Request, merge vào `main`, rồi mới sang Task tiếp theo. Không tự ý thêm tính năng ngoài tài liệu. Gặp điều tài liệu không nói rõ thì chọn cách **đơn giản nhất** và ghi lại lựa chọn đó trong mô tả PR.

Ngày: 2026-10-12 · Repo: `https://github.com/tanh8805/LTM` · Môn: Lập trình mạng (bài tập lớn nhóm 4 người)

---

## Phần 1. Quy tắc Git và GitHub (bắt buộc)

### 1.1 Tài khoản và tác giả commit

Mọi commit phải có tác giả là tài khoản GitHub **tanh8805**. Trước khi làm bất cứ việc gì, chạy:

```bash
gh auth status
```

Kết quả phải cho thấy đang đăng nhập `github.com` bằng tài khoản `tanh8805`. **Nếu không phải, DỪNG LẠI** và báo người dùng chạy `gh auth login`. Không tự đăng nhập, không dùng token khác.

Sau khi clone repo, cấu hình tác giả **trong repo** (không dùng `--global`):

```bash
gh repo clone tanh8805/LTM
cd LTM
git config user.name "tanh8805"
git config user.email "$(gh api user --jq .id)+tanh8805@users.noreply.github.com"
git config user.name && git config user.email   # kiểm tra lại
```

Quy tắc commit:
- **Không** thêm dòng `Co-Authored-By`, `Signed-off-by`, hay bất kỳ dòng ghi công AI/agent nào vào commit message hoặc mô tả PR.
- Commit message theo dạng `feat: ...`, `test: ...`, `fix: ...`, `docs: ...`, `chore: ...`, viết tiếng Việt hoặc tiếng Anh đều được, ngắn gọn.
- Commit nhỏ, thường xuyên: ít nhất một commit cho test và một commit cho phần cài đặt trong mỗi Task.

### 1.2 Kiểm tra trạng thái repo ban đầu

Sau khi clone, chạy `git ls-files`.
- Nếu repo **rỗng** (chưa có commit): Task 1 sẽ tạo commit đầu tiên trên `main` (xem Task 1).
- Nếu repo chỉ có `README.md`, `.gitignore`, `LICENSE` hoặc thư mục `docs/`: làm tiếp bình thường, được phép ghi đè `README.md` và `.gitignore`.
- Nếu repo có **code khác** (file `.java`, `.py`, `pom.xml`...): **DỪNG LẠI** và hỏi người dùng có được xoá không. Không tự xoá.

### 1.3 Quy trình cho mỗi Task (mỗi Task = một nhánh = một chức năng)

```bash
git checkout main && git pull
git checkout -b <tên-nhánh>            # tên nhánh ghi ở đầu mỗi Task
# ... làm các bước, commit thường xuyên ...
mvn -q test                             # toàn bộ test Java phải xanh (từ Task 2 trở đi)
cd ml && python -m pytest -q && cd ..   # từ Task 8 trở đi
git push -u origin <tên-nhánh>
gh pr create --base main --head <tên-nhánh> --title "<tiêu đề Task>" --body "<tóm tắt thay đổi + cách test>"
gh pr merge <tên-nhánh> --merge         # merge commit, KHÔNG squash, KHÔNG xoá nhánh
git checkout main && git pull
```

- **Không** dùng `--squash`, `--rebase` hay `--delete-branch`: giữ lại các nhánh và lịch sử commit để thầy xem được quá trình làm.
- Chỉ merge khi toàn bộ test xanh. Test đỏ thì sửa trên chính nhánh đó.
- Không push thẳng lên `main`, trừ commit đầu tiên của repo rỗng (Task 1).

---

## Phần 2. Đặc tả hệ thống

### 2.1 Mục tiêu

App thi trắc nghiệm trong mạng LAN. Giáo viên chọn file đề và bắt đầu thi, sinh viên làm bài trên client. Trong lúc thi, client giám sát bằng **luật** (app ngoài whitelist, kết nối tới địa chỉ bị cấm, cắm USB, rời cửa sổ thi) và gửi dữ liệu về server. Server gửi dữ liệu cả phòng sang một **service Python dùng Isolation Forest** để tìm máy có hành vi khác biệt so với số đông.

Tiêu chí thành công:
- Chạy trọn kịch bản demo ở mục 2.13 trên 3 laptop Windows (client) + 1 laptop Linux Fedora (server + ML).
- Mỗi thành viên đọc một module và giải thích, sửa được phần của mình khi thầy hỏi vấn đáp.

### 2.2 Ràng buộc

- **Code ngắn, ít file**, mỗi file **dưới 250 dòng**. Không dùng framework (không Spring, không JavaFX). Thư viện Java duy nhất là **Gson** (cộng JUnit 5 cho test).
- Không có dataset → ML **không giám sát**, fit trên chính dữ liệu của buổi thi.
- Laptop cấu hình khác nhau → feature chỉ là **các giá trị đếm**, không dùng CPU% hay RAM.
- Client **chỉ chạy trên Windows**. Server và service ML chạy trên Linux (cũng phải chạy được trên Windows, không dùng gì riêng của Linux).
- Client **chỉ phát hiện và báo cáo**: không chặn mạng, không kill tiến trình, không cần quyền admin.
- Chú thích (comment) trong code viết **tiếng Việt**, ngắn, giải thích *vì sao* hơn là *làm gì*. Đầu mỗi file có một comment 1–2 dòng nói file làm gì. Chuỗi hiển thị trên giao diện viết tiếng Việt có dấu.

### 2.3 Công nghệ

- Java **17** (`maven.compiler.release=17`), Maven, Gson **2.11.0**, JUnit Jupiter **5.10.2**, giao diện **Swing**. Build ra một file `target/exam.jar` (dùng `maven-shade-plugin` để gói kèm Gson).
- Python **3.10+**: `fastapi`, `uvicorn`, `scikit-learn`, `numpy`, `httpx` và `pytest` (để test).
- Cổng: TCP **5000** (server), UDP **9999** (tìm server), HTTP **8000** (service ML, chỉ server gọi qua `http://localhost:8000`).

### 2.4 Cấu trúc thư mục và phân công thành viên

```
pom.xml
README.md
.gitignore
docs/AGENT_BRIEF.md                               # chính tài liệu này
config/rules.json                                 # luật giám sát, nằm ở server
exams/sample_exam.json                            # đề mẫu 10 câu
src/main/java/exam/common/Protocol.java           # cả nhóm đọc
src/main/java/exam/client/StudentApp.java         # Người 1
src/main/java/exam/client/ServerConnection.java   # Người 1
src/main/java/exam/client/ServerFinder.java       # Người 1
src/main/java/exam/client/Monitor.java            # Người 2
src/main/java/exam/server/ExamServer.java         # Người 3
src/main/java/exam/server/ClientHandler.java      # Người 3
src/main/java/exam/server/TeacherWindow.java      # Người 3
src/main/java/exam/server/MlClient.java           # Người 4
src/test/java/exam/ProtocolTest.java
src/test/java/exam/GradingTest.java
src/test/java/exam/ExamServerTest.java
src/test/java/exam/DiscoveryTest.java
src/test/java/exam/MonitorTest.java
src/test/java/exam/MlClientTest.java
ml/requirements.txt
ml/ml_service.py                                  # Người 4
ml/simulate_clients.py                            # Người 4
ml/test_ml_service.py
```

Không tạo thêm file Java nào ngoài danh sách trên. Class phụ nhỏ (ví dụ `Session`, `Row`) đặt làm **class lồng** (`static class` hoặc `record`) bên trong file sở hữu nó.

Cách chạy (chạy từ thư mục gốc repo):
- Service ML: `cd ml && uvicorn ml_service:app --host 127.0.0.1 --port 8000`
- Server: `java -cp target/exam.jar exam.server.ExamServer`
- Client: `java -cp target/exam.jar exam.client.StudentApp`

### 2.5 Giao thức

#### 2.5.1 Tìm server qua UDP (cổng 9999)

- Client gửi chuỗi UTF-8 `EXAM_DISCOVER` tới: `255.255.255.255`, địa chỉ broadcast của **từng** card mạng đang bật và không phải loopback (duyệt `NetworkInterface.getNetworkInterfaces()`, lấy `InterfaceAddress.getBroadcast()` khác null), và `127.0.0.1` (để chạy client và server trên cùng một máy khi dev). Lý do: máy cài ZeroTier có nhiều card mạng, nếu chỉ gửi `255.255.255.255` thì Java có thể đẩy gói ra nhầm card.
- Server lắng nghe UDP trên `0.0.0.0:9999`. Nhận đúng chuỗi `EXAM_DISCOVER` (sau khi `trim`) thì trả lời `EXAM_SERVER <cổng TCP>` (ví dụ `EXAM_SERVER 5000`) về đúng địa chỉ và cổng của người gửi. Chuỗi khác thì bỏ qua.
- Client lấy IP từ **gói trả lời hợp lệ đầu tiên** (`packet.getAddress()`) và cổng từ nội dung. Gói trả lời sai định dạng thì bỏ qua và tiếp tục chờ. Hết **3 giây** không có trả lời hợp lệ thì trả về `null`.

#### 2.5.2 Tin nhắn TCP (cổng 5000)

Mỗi tin nhắn là **một dòng JSON** kết thúc bằng `\n`, mã hoá **UTF-8**, luôn có trường `type`.

| Chiều | type | Trường | Ghi chú |
|---|---|---|---|
| C→S | `LOGIN` | `studentId`, `name` | |
| S→C | `LOGIN_OK` | `rules` (object, nội dung `config/rules.json`) | Luật do server gửi xuống, sinh viên không sửa được |
| S→C | `LOGIN_FAIL` | `reason` | |
| C→S | `PRECHECK_RESULT` | `ok` (bool), `issues` (mảng chuỗi) | Có thể gửi nhiều lần nếu sinh viên kiểm tra lại |
| S→C | `EXAM_START` | `title`, `remainingSeconds` (int), `questions` (mảng, mỗi câu `id`, `text`, `options`; **không có** `answer`) | |
| C→S | `VIOLATION` | `kind` (`APP`/`NET`/`USB`), `detail`, `time` (`HH:mm:ss`) | |
| C→S | `FEATURES` | `window` (int, bắt đầu từ 1), `values` (mảng đúng 6 số, mục 2.8.2) | Gửi mỗi 30 giây, đồng thời là heartbeat |
| C→S | `SUBMIT` | `answers` (object: khoá = id câu dạng chuỗi, giá trị = chỉ số lựa chọn, bắt đầu từ 0) | |
| S→C | `EXAM_END` | `reason` | Client nhận được thì tự gửi `SUBMIT` |
| S→C | `RESULT` | `score` (số), `correct` (int), `total` (int) | |

Quy tắc chung:
- Dòng không phải JSON object hợp lệ: bỏ qua, ghi log, **không** đóng kết nối.
- `type` không biết: bỏ qua, ghi log.
- Mọi tin nhắn (trừ `LOGIN`) gửi trước khi đăng nhập thành công: bỏ qua.

#### 2.5.3 Service ML (HTTP)

`POST /score`, body:

```json
{"samples": [{"studentId": "B21DCCN001", "window": 3, "values": [0, 1, 4, 0, 0, 2]}]}
```

Trả về `200`:

```json
{"status": "ok", "results": [{"studentId": "B21DCCN001", "score": 0.82, "anomaly": true}]}
```

hoặc `{"status": "not_enough_data", "results": []}` khi có **dưới 20 mẫu**. Body sai định dạng (ví dụ `values` không đủ 6 số) thì FastAPI tự trả `422`.

### 2.6 Luồng một buổi thi

1. Giáo viên chạy server. `TeacherWindow` mở ra, giáo viên chọn file đề, server nạp đề vào bộ nhớ. Đáp án chỉ nằm ở server.
2. Sinh viên mở client, nhập mã SV, họ tên, và IP server (để trống = tự tìm qua UDP). Client kết nối TCP, gửi `LOGIN`, nhận `LOGIN_OK` kèm luật.
3. Client chạy PRECHECK và gửi `PRECHECK_RESULT`. Nếu không đạt, giao diện hiện danh sách vấn đề và nút "Kiểm tra lại".
4. Giáo viên bấm "Bắt đầu thi". Server ghi lại thời điểm bắt đầu và hẹn giờ tự kết thúc sau `durationMinutes`.
5. **Quy tắc gửi đề duy nhất** (hàm `trySendExam`): server gửi `EXAM_START` cho một session khi *bài thi đã bắt đầu* **và** *session đang kết nối* **và** *session đã PRECHECK đạt* **và** *chưa nộp bài*. Gọi hàm này tại ba thời điểm: lúc bấm Bắt đầu (cho mọi session), lúc nhận `PRECHECK_RESULT` đạt, và lúc đăng nhập (kể cả đăng nhập lại). Nhờ vậy vào thi muộn và vào lại sau khi rớt mạng dùng chung một đoạn code. `remainingSeconds = max(0, durationMinutes*60 − số giây đã trôi qua)`.
6. Trong giờ thi: client quét mỗi **3 giây**, gửi `VIOLATION` khi có vi phạm mới, gửi `FEATURES` mỗi **30 giây**. Server lưu mọi mẫu `FEATURES` trong bộ nhớ. Mỗi 30 giây server gửi toàn bộ mẫu sang service ML và cập nhật cột "Bất thường".
7. Kết thúc: sinh viên bấm Nộp bài (`SUBMIT`). Khi hết giờ hoặc giáo viên bấm "Kết thúc", server gửi `EXAM_END` cho mọi session đang kết nối mà chưa nộp; client nhận được thì tự nộp. Client cũng tự nộp khi đồng hồ của nó về 0.
8. Server nhận `SUBMIT` thì chấm điểm, gửi `RESULT`, và **ghi lại toàn bộ `results.csv` sau mỗi lần có bài nộp** (để bài nộp muộn sau `EXAM_END` vẫn có trong file). `SUBMIT` được chấp nhận bất cứ lúc nào sau khi thi đã bắt đầu, kể cả sau khi đã kết thúc, miễn là session đó chưa nộp. Nộp lần hai thì không chấm lại, chỉ gửi lại `RESULT` cũ.

Chấm điểm: `score = correct / total * 10`, làm tròn 2 chữ số thập phân; `total = 0` thì `score = 0`. Câu không trả lời, hoặc giá trị đáp án không phải số nguyên, tính là sai.

### 2.7 Đăng nhập trùng và mất kết nối

- Server giữ `Map<String, Session>` theo mã SV. `Session` gồm: mã SV, họ tên, trạng thái, socket và `PrintWriter` hiện tại (null nếu mất kết nối), cờ PRECHECK đạt, cờ đã nộp, số vi phạm, vi phạm gần nhất, danh sách mẫu `FEATURES`, điểm bất thường hiện tại, điểm bất thường cao nhất, cờ `anomaly`, kết quả chấm (`score`, `correct`, `total`, null nếu chưa nộp), `lastSeen` (cập nhật mỗi khi nhận bất kỳ tin nhắn nào).
- Trạng thái hiển thị (đúng các chuỗi này): `Đã đăng nhập`, `PRECHECK lỗi`, `Sẵn sàng`, `Đang thi`, `Đã nộp`, `Mất kết nối`.
- `LOGIN` với mã SV hoặc họ tên rỗng → `LOGIN_FAIL` lý do `Thiếu mã SV hoặc họ tên`.
- `LOGIN` với mã SV mà session cũ **đang kết nối** → `LOGIN_FAIL` lý do `Mã SV này đang đăng nhập ở máy khác`.
- Session cũ đã mất kết nối → gắn socket mới vào session cũ (giữ nguyên PRECHECK, vi phạm, mẫu, kết quả) rồi gọi `trySendExam`.
- Phát hiện mất kết nối: socket đóng hoặc lỗi đọc; **hoặc** session đang ở trạng thái `Đang thi` mà quá **90 giây** không nhận tin nhắn nào (server kiểm tra mỗi 10 giây, khi đó server đóng socket của session để luồng đọc kết thúc). Trạng thái chuyển thành `Mất kết nối` (trừ khi đã nộp thì giữ `Đã nộp`) và ghi log.
- **Chống nhầm session:** khi một luồng đọc kết thúc, chỉ đánh dấu mất kết nối nếu socket của session **vẫn là socket của luồng đó** (tránh trường hợp máy đã kết nối lại bằng socket mới).
- Phía client: khi mất kết nối (và chưa tự đóng), client hiện "Mất kết nối, đang thử lại...", cứ **5 giây** thử kết nối lại tới cùng địa chỉ, thành công thì gửi lại `LOGIN` với cùng mã SV và họ tên. Đáp án chỉ lưu trong bộ nhớ client: rớt mạng mà không tắt app thì vẫn giữ; tắt hẳn app thì mất (đánh đổi có chủ đích để code gọn). Tin nhắn gửi khi đang mất kết nối thì bỏ.

### 2.8 Giám sát ở client (`Monitor.java`)

#### 2.8.1 Luật (`config/rules.json`)

```json
{
  "blacklistApps": ["chrome.exe", "msedge.exe", "firefox.exe", "opera.exe", "brave.exe", "zalo.exe", "discord.exe", "teams.exe", "ms-teams.exe", "telegram.exe", "messenger.exe"],
  "whitelistApps": ["calc.exe", "calculatorapp.exe", "notepad.exe"],
  "blockedDomains": ["chatgpt.com", "chat.openai.com", "gemini.google.com", "claude.ai", "copilot.microsoft.com", "chat.deepseek.com"]
}
```

So sánh tên tiến trình luôn dùng **chữ thường**.

Ngoài whitelist trong `rules.json`, `Monitor` có một danh sách cứng `SYSTEM_PROCESSES` gồm các tiến trình do chính client sinh ra hoặc Windows tự bật/tắt định kỳ, **không bao giờ** tính là vi phạm hay tiến trình mới: `tasklist.exe`, `netstat.exe`, `powershell.exe`, `conhost.exe`, `cmd.exe`, `java.exe`, `javaw.exe`, `backgroundtaskhost.exe`, `runtimebroker.exe`, `searchprotocolhost.exe`, `searchfilterhost.exe`, `wmiprvse.exe`, `dllhost.exe`, `audiodg.exe`, `taskhostw.exe`, `smartscreen.exe`, `wermgr.exe`, `consent.exe`, `svchost.exe`. Lý do: `tasklist` liệt kê cả chính nó, và Windows sinh các tiến trình nền này liên tục; thiếu danh sách này thì máy nào cũng bị báo vi phạm.

#### 2.8.2 Hoạt động

API công khai (đúng chữ ký này):

```java
public Monitor()                                  // tự xác định có phải Windows không qua os.name
public List<String> precheck(JsonObject rules)     // danh sách vấn đề, rỗng = đạt; gọi ngoài luồng giao diện
public void start(Consumer<JsonObject> sender)     // bắt đầu quét 3s và gửi FEATURES 30s
public void stop()
public synchronized void recordAnswer()            // StudentApp gọi khi chọn hoặc đổi đáp án
public synchronized void focusLost()               // StudentApp gọi khi cửa sổ thi mất focus sang app khác
public synchronized void focusGained()
```

- **PRECHECK** (trên Windows):
  - Chạy `tasklist /fo csv /nh`, lấy cột đầu (tên tiến trình). Mỗi app thuộc `blacklistApps` đang chạy → vấn đề `Đang mở ứng dụng bị cấm: <tên>`.
  - Chạy `powershell -NoProfile -Command "Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=2' | ForEach-Object { $_.DeviceID }"` để lấy ổ đĩa rời. Mỗi ổ → vấn đề `Đang cắm USB: <ổ>`. (Không dùng `wmic` vì đã bị gỡ trên Windows 11 bản mới.)
  - Khi gọi `precheck`, **luôn** lưu baseline từ kết quả lần chạy đó: tập tên tiến trình, tập ổ đĩa từ `File.listRoots()`, tập IP đích từ `netstat -no`. Lần precheck đạt cuối cùng là baseline được dùng trong giờ thi.
- **Trong giờ thi**, mỗi 3 giây (trên Windows):
  - **APP:** tiến trình không có trong baseline, không thuộc `SYSTEM_PROCESSES` → được tính là *tiến trình mới* của cửa sổ 30 giây hiện tại. Nếu thêm điều kiện không thuộc `whitelistApps` → vi phạm `APP`, `detail` = tên tiến trình.
  - **USB:** ổ trong `File.listRoots()` không có trong baseline → vi phạm `USB`, `detail` = tên ổ (ví dụ `E:\`).
  - **NET:** lúc `start`, phân giải mỗi tên miền trong `blockedDomains` bằng `InetAddress.getAllByName` (lỗi thì bỏ qua tên miền đó), lưu bảng IP → tên miền. Mỗi lần quét chạy `netstat -no`, lấy IP đích của các dòng `TCP` ở trạng thái `ESTABLISHED` hoặc `SYN_SENT`, bỏ qua `0.0.0.0`, `127.*`, `::`, `::1`. IP thuộc bảng → vi phạm `NET`, `detail` = `<tên miền> (<IP>)`. IP không có trong baseline → được tính là *host mới* của cửa sổ hiện tại. Giới hạn đã biết, ghi trong README: trang dùng CDN đổi IP nên luật này không bắt được 100%.
  - **Chống spam:** mỗi cặp (`kind`, `detail`) chỉ gửi `VIOLATION` **một lần** trong cả buổi thi.
- **Máy không phải Windows:** `precheck` luôn trả danh sách rỗng và in một dòng cảnh báo ra `System.err`; không quét tiến trình, mạng, USB. Focus và số câu trả lời vẫn đếm thật, `FEATURES` vẫn gửi. Nhờ vậy dev được trên Linux.
- Chạy lệnh hệ thống: `ProcessBuilder` + `redirectErrorStream(true)`, chờ tối đa 10 giây, lỗi thì trả chuỗi rỗng (không ném exception ra ngoài).
- Quét và gửi `FEATURES` chạy trên **một** `ScheduledExecutorService` một luồng, luồng daemon. Các phương thức gọi từ luồng giao diện là `synchronized`.

Vector feature mỗi 30 giây, **đúng thứ tự**, sau khi gửi thì reset bộ đếm của cửa sổ:

1. `newProcesses`: số tên tiến trình mới (theo định nghĩa ở trên) xuất hiện trong cửa sổ
2. `focusLostCount`: số lần rời cửa sổ thi
3. `focusLostSeconds`: tổng số giây rời cửa sổ thi, làm tròn số nguyên. Nếu đang rời cửa sổ lúc chốt, tính phần thời gian đến thời điểm chốt, phần còn lại sang cửa sổ sau (không tăng `focusLostCount` lần nữa).
4. `newRemoteHosts`: số IP đích mới trong cửa sổ
5. `violations`: số `VIOLATION` đã gửi trong cửa sổ
6. `answers`: số lần chọn hoặc đổi đáp án trong cửa sổ

`focusLost` gọi hai lần liên tiếp chỉ tính một lần; `focusGained` khi không đang rời cửa sổ thì không làm gì.

### 2.9 ML

- Service (`ml_service.py`):
  - Dưới 20 mẫu → `not_enough_data`.
  - Fit `IsolationForest(n_estimators=100, random_state=42)` (`contamination` để mặc định) trên **tất cả** mẫu nhận được.
  - Điểm thô = `-model.score_samples(X)` (càng cao càng bất thường), chuẩn hoá min–max về 0–1 trên toàn bộ mẫu. Nếu `max − min < 1e-9` (mọi máy hành xử giống hệt nhau) thì mọi điểm = 0 và mọi `anomaly = false`.
  - Mỗi studentId: lấy **3 mẫu có `window` lớn nhất**; `score` = điểm lớn nhất trong 3 mẫu đó, làm tròn 3 chữ số; `anomaly = true` nếu `model.predict` trả `-1` ở bất kỳ mẫu nào trong 3 mẫu đó.
  - Logic tính nằm trong hàm thuần `score_samples(samples: list[Sample]) -> dict` để test không cần HTTP.
- `MlClient.java`: dùng `java.net.http.HttpClient`, timeout kết nối và request 5 giây. Mã trạng thái khác 200 → ném `IOException`.
- Server: mỗi 30 giây, nếu bài thi đang diễn ra, gom tất cả mẫu của mọi session thành mảng `samples` (lấy dữ liệu trong khối `synchronized`, **gọi HTTP ngoài khối `synchronized`**), gọi ML, rồi cập nhật điểm bất thường. ML lỗi hoặc tắt → ghi log `ML lỗi: ...`, giữ nguyên điểm cũ, server không được sập.
- Giao diện ghi **"Bất thường"**, không bao giờ ghi "Gian lận": mô hình chỉ biết máy khác số đông, giám thị mới là người quyết định.

### 2.10 Giao diện

**StudentApp** (Swing, `CardLayout`, cửa sổ 800×600, tiêu đề `Thi trắc nghiệm`):
1. **Đăng nhập:** ô Mã SV, ô Họ tên, ô `IP server (để trống = tự tìm)`, nút Đăng nhập. Kết nối chạy ngoài luồng giao diện. Không tìm thấy server → thông báo `Không tìm thấy server, hãy nhập IP`.
2. **PRECHECK:** danh sách vấn đề + nút Kiểm tra lại.
3. **Chờ:** `Đã sẵn sàng. Chờ giáo viên bắt đầu...`
4. **Làm bài:** tiêu đề đề thi, đồng hồ đếm ngược `mm:ss`, `Câu i/n`, nội dung câu hỏi (dùng `JLabel` với HTML để tự xuống dòng), các lựa chọn là `JRadioButton` trong một `ButtonGroup`, nút Câu trước / Câu sau / Nộp bài (Nộp bài có hộp xác nhận).
5. **Kết quả:** `Điểm: x/10 (đúng c/t câu)`.
- Thanh trạng thái dưới cùng hiện trạng thái kết nối.
- Mọi cập nhật giao diện đi qua `SwingUtilities.invokeLater`.
- Đếm rời cửa sổ bằng `WindowFocusListener`: chỉ gọi `monitor.focusLost()` khi `e.getOppositeWindow() == null` (focus sang **app khác**), để hộp thoại của chính app (ví dụ hộp xác nhận nộp bài) không bị tính là rời cửa sổ. Tương tự cho `focusGained`.
- Nhận `EXAM_START` khi **đã** có đề (trường hợp vào lại): chỉ cập nhật thời gian còn lại, **giữ nguyên** đáp án đã chọn và câu đang xem.
- Nhận `LOGIN_OK` khi chưa có đề: chạy PRECHECK. Khi đã có đề (vào lại giữa giờ): không làm gì, server sẽ gửi lại `EXAM_START`.

**TeacherWindow** (Swing, tiêu đề `Giám thị - Server thi`):
- Hàng nút: `Chọn file đề...` (`JFileChooser` mở sẵn thư mục `exams`), nhãn tên đề, `Bắt đầu thi` (chỉ bật khi đã nạp đề và chưa bắt đầu), `Kết thúc thi` (chỉ bật khi đang thi), nhãn thời gian còn lại, nhãn địa chỉ IP của máy server (liệt kê IPv4 không phải loopback).
- `JTable` các cột: Mã SV · Họ tên · Trạng thái · Số vi phạm · Vi phạm gần nhất · Bất thường (0–1, hiện `-` khi chưa có) · Điểm (hiện `-` khi chưa nộp). Sắp xếp theo mã SV. Dòng có vi phạm > 0 hoặc `anomaly = true` tô nền đỏ nhạt.
- Ô log (`JTextArea`, chỉ đọc, tự cuộn) ở dưới, mỗi dòng có giờ `HH:mm:ss`.
- Một `javax.swing.Timer` 1 giây làm mới bảng và đồng hồ.

### 2.11 File dữ liệu

`exams/sample_exam.json` (10 câu về Lập trình mạng, `durationMinutes: 15`):

```json
{"title": "Kiểm tra Lập trình mạng", "durationMinutes": 15,
 "questions": [{"id": 1, "text": "TCP là giao thức...", "options": ["Không kết nối", "Hướng kết nối", "Tầng ứng dụng", "Tầng vật lý"], "answer": 1}]}
```

Nạp đề phải kiểm tra: có `title` (chuỗi), `durationMinutes` (số nguyên > 0), `questions` (mảng không rỗng), mỗi câu có `id` (số nguyên, không trùng), `text`, `options` (ít nhất 2), `answer` (chỉ số hợp lệ trong `options`). Sai thì ném `IllegalArgumentException` với thông báo tiếng Việt nói rõ câu nào sai; giao diện hiện thông báo đó.

`results.csv` (ghi ở thư mục chạy server, UTF-8 **có BOM** để Excel đọc đúng tiếng Việt), cột: `studentId,name,score,correct,total,violations,maxAnomaly,status`. Giá trị có dấu phẩy hoặc dấu nháy kép thì bọc trong nháy kép và nhân đôi nháy kép bên trong. Ghi tất cả session, kể cả chưa nộp (điểm để trống).

### 2.12 Mạng và triển khai

- Làm ở nhà: **ZeroTier** (gói Personal miễn phí), 4 máy join cùng một network. Không dùng Tailscale (không hỗ trợ broadcast) hay Radmin VPN (không có bản Linux).
- Hôm demo: cả 4 máy bắt chung một hotspot điện thoại.
- Fedora: `sudo firewall-cmd --add-port=5000/tcp --add-port=9999/udp` (thêm `--permanent` rồi `--reload` nếu muốn giữ). Windows: cho phép Java qua tường lửa (cả mạng Private và Public) khi được hỏi.
- Máy client cần Java 17 trở lên (JRE là đủ). Chỉ cần chép `exam.jar` sang.

### 2.13 Kịch bản demo (khoảng 10 phút)

1. Máy Linux chạy service ML, server, và `python simulate_clients.py --count 5`.
2. Máy A, B, C (Windows) đăng nhập. Máy B để Zalo mở → PRECHECK lỗi → đóng Zalo → Kiểm tra lại → đạt.
3. Giáo viên bấm Bắt đầu, đề hiện trên cả 3 máy.
4. Máy A làm bài bình thường.
5. Máy B cắm USB → dòng của máy B tô đỏ, vi phạm USB.
6. Máy C mở Chrome vào chatgpt.com → vi phạm APP và NET.
7. Máy C chuyển qua lại với Calculator (nằm trong whitelist) liên tục rồi trả lời dồn dập. Không luật nào bắt được, nhưng sau 1–2 phút cột Bất thường của máy C cao hẳn so với cả phòng.
8. Tắt Wi-Fi máy A → `Mất kết nối` → bật lại → máy A tự vào lại, làm tiếp với thời gian còn lại.
9. Kết thúc thi, mở `results.csv`.

### 2.14 Ngoài phạm vi (không làm)

Database, mật khẩu/xác thực, CRUD ngân hàng câu hỏi, chụp màn hình/webcam, chặn mạng hoặc kill tiến trình, mã hoá TLS, nhiều kỳ thi cùng lúc, client trên Linux/macOS, lưu đáp án khi tắt app.

---

## Phần 3. Quy tắc viết code

- Ưu tiên code **dễ đọc với sinh viên năm 3** hơn code "thông minh". Không dùng stream lồng nhau phức tạp, không reflection, không generic tự định nghĩa.
- Mỗi phương thức ngắn (khoảng ≤ 40 dòng). Tên biến tiếng Anh, rõ nghĩa.
- Luồng (thread) chỉ dùng: một luồng nhận kết nối + một luồng cho mỗi client ở server, một luồng UDP, một `ScheduledExecutorService` cho các việc định kỳ ở server; một luồng đọc + một luồng kết nối lại ở client; một `ScheduledExecutorService` trong `Monitor`. Mọi thay đổi trạng thái chung ở server đi qua phương thức `synchronized` của `ExamServer`.
- Không nuốt exception im lặng: hoặc ghi log, hoặc có comment giải thích vì sao bỏ qua.
- TDD: với mỗi Task, viết test trước (khi Task có test), chạy thấy đỏ, rồi cài đặt cho xanh.
- Test không được phụ thuộc mạng ngoài. Test dùng cổng `0` (để hệ điều hành chọn cổng trống) và timeout ngắn (tối đa vài giây).

---

## Phần 4. Danh sách điểm dễ hỏng (phải có test)

Những trường hợp đặc tả ngụ ý nhưng dễ bị bỏ sót. Mỗi dòng đã có test tương ứng trong Task sở hữu.

1. **Tiến trình của chính client và tiến trình nền Windows** bị báo vi phạm (thiếu `SYSTEM_PROCESSES`) → Task 5, test `systemProcessesNeverCount`.
2. **Hộp thoại của chính app bị tính là rời cửa sổ thi** → Task 6, xử lý bằng `getOppositeWindow() == null`; Task 5 test `focusLostTwiceCountsOnce`.
3. **Bài nộp sau `EXAM_END` hoặc sau khi giáo viên bấm Kết thúc bị mất khỏi `results.csv`** → Task 3, test `submitAfterEndIsAcceptedAndWritten`.
4. **Máy rớt mạng rồi vào lại bị đá ra (`LOGIN_FAIL`) hoặc mất trạng thái** → Task 3, test `reconnectGetsExamWithRemainingTime` và `disconnectOfOldSocketDoesNotKickNewOne`.
5. **Tin nhắn rác hoặc `FEATURES` sai định dạng làm sập server** → Task 3, test `garbageLineDoesNotCloseConnection` và `badFeaturesIgnored`; Task 8 test `wrong_length_rejected`.

---

## Phần 5. Các Task

Mỗi Task ghi: tên nhánh, file, giao diện công khai, yêu cầu, test bắt buộc (tên test và hành vi cần kiểm tra), và điều kiện hoàn thành. Tên test phải đặt đúng như ghi.

### Task 1 — Khởi tạo project · nhánh `feature/project-setup`

**Nếu repo rỗng:** trên `main`, tạo commit đầu tiên chỉ chứa `README.md` một dòng `# LTM - Hệ thống thi trắc nghiệm có giám sát`, push lên `main`, rồi mới tạo nhánh.

**File:** `pom.xml`, `.gitignore`, `README.md`, `docs/AGENT_BRIEF.md` (chép nguyên tài liệu này), `config/rules.json`, `exams/sample_exam.json`, `ml/requirements.txt`.

**Yêu cầu:**
- `pom.xml`: `groupId` `exam`, `artifactId` `exam`, `finalName` `exam`, `maven.compiler.release` 17, encoding UTF-8; dependency Gson 2.11.0 và `junit-jupiter` 5.10.2 (scope test); plugin `maven-surefire-plugin` 3.2.5 và `maven-shade-plugin` 3.5.1 (goal `shade` ở phase `package`, không cần cấu hình main class vì chạy bằng `-cp`).
- `.gitignore`: `target/`, `results.csv`, `__pycache__/`, `.venv/`, `.pytest_cache/`, `.idea/`, `.vscode/`, `*.iml`.
- `config/rules.json`: đúng nội dung mục 2.8.1.
- `exams/sample_exam.json`: 10 câu trắc nghiệm về Lập trình mạng (TCP/UDP, socket, cổng, IP, DNS, HTTP, broadcast...), mỗi câu 4 lựa chọn, đáp án phân bố đều ở các vị trí, `durationMinutes` 15.
- `ml/requirements.txt`: `fastapi`, `uvicorn`, `scikit-learn`, `numpy`, `httpx`, `pytest` (không ghim phiên bản).
- `README.md`: tiêu đề, mô tả 3 câu, bảng phân công 4 người (mục 2.4), các lệnh chạy (mục 2.4). Phần còn lại hoàn thiện ở Task 11.

**Hoàn thành khi:** `mvn -q package` chạy thành công (chưa có code Java vẫn được).

---

### Task 2 — Giao thức chung · nhánh `feature/protocol`

**File:** `src/main/java/exam/common/Protocol.java`, `src/test/java/exam/ProtocolTest.java`.

**Giao diện công khai:**

```java
public final class Protocol {
    public static final int TCP_PORT = 5000, UDP_PORT = 9999;
    public static final String DISCOVER = "EXAM_DISCOVER", SERVER_REPLY = "EXAM_SERVER";
    public static final String LOGIN, LOGIN_OK, LOGIN_FAIL, PRECHECK_RESULT, EXAM_START,
                               VIOLATION, FEATURES, SUBMIT, EXAM_END, RESULT;   // giá trị = chính tên hằng
    public static final Gson GSON;
    public static JsonObject msg(String type);                     // object có sẵn "type"
    public static String type(JsonObject m);                       // "" nếu thiếu
    public static String str(JsonObject m, String key);            // chuỗi đã trim, "" nếu thiếu/sai kiểu
    public static void send(PrintWriter out, JsonObject m);        // một dòng JSON + "\n", flush; synchronized(out)
    public static JsonObject parse(String line);                   // dòng hỏng → IllegalArgumentException
    public static BufferedReader reader(Socket s) throws IOException;   // UTF-8
    public static PrintWriter writer(Socket s) throws IOException;      // UTF-8
}
```

**Test bắt buộc (`ProtocolTest`):**
- `sendThenParseKeepsFieldsIncludingVietnamese`: gửi `LOGIN` có họ tên `Nguyễn Văn Á` vào `StringWriter`; chuỗi kết thúc bằng `\n`, đúng một dòng; parse lại được đúng `type` và `name`.
- `textWithNewlineStaysOnOneLine`: trường chứa `\n` vẫn chỉ tạo một dòng trên dây và parse lại đúng nội dung.
- `brokenLineThrowsIllegalArgument`: `{khong phai json` và `[1,2,3]` đều ném `IllegalArgumentException`.
- `missingFieldsGiveEmptyString`: `type` và `str` trả `""` khi thiếu trường.

---

### Task 3 — Lõi server · nhánh `feature/exam-server`

**File:** `src/main/java/exam/server/ExamServer.java`, `src/main/java/exam/server/ClientHandler.java`, `src/test/java/exam/GradingTest.java`, `src/test/java/exam/ExamServerTest.java`.

**Giao diện công khai của `ExamServer`** (Task 4, 7, 9 dùng đúng các tên này):

```java
public class ExamServer {
    public static final String ST_LOGGED_IN = "Đã đăng nhập", ST_PRECHECK_FAIL = "PRECHECK lỗi",
        ST_READY = "Sẵn sàng", ST_IN_EXAM = "Đang thi", ST_SUBMITTED = "Đã nộp", ST_DISCONNECTED = "Mất kết nối";

    public record Row(String studentId, String name, String status, int violations, String lastViolation,
                      Double anomalyScore, boolean anomaly, Double score) {}

    public ExamServer(JsonObject rules);
    public void setListener(Consumer<String> logListener);   // nhận mỗi dòng log; có thể null
    public void start(int tcpPort) throws IOException;       // mở ServerSocket (0 = cổng bất kỳ), luồng accept, watchdog 10s
    public int tcpPort();                                     // cổng thực tế đang nghe
    public void stop();                                       // đóng mọi socket, dừng các luồng
    public synchronized void loadExam(Path file) throws IOException;   // kiểm tra theo mục 2.11
    public synchronized String examTitle();                   // null nếu chưa nạp
    public synchronized boolean isStarted();
    public synchronized boolean isEnded();
    public synchronized void startExam();                     // ném IllegalStateException nếu chưa nạp đề hoặc đã bắt đầu
    public synchronized void endExam(String reason);          // gửi EXAM_END cho máy đang kết nối chưa nộp, ghi results.csv
    public synchronized int remainingSeconds();
    public synchronized List<Row> rows();                     // sắp theo mã SV
    public void setResultsFile(Path p);                       // mặc định Path.of("results.csv"); test đổi sang thư mục tạm
    static JsonObject grade(JsonObject exam, JsonObject answers);  // {score, correct, total}
}
```

Các phương thức nội bộ mà `ClientHandler` gọi (package-private, `synchronized`), ví dụ: `login(...)`, `precheckResult(...)`, `violation(...)`, `features(...)`, `submit(...)`, `disconnected(...)`, `touch(...)`. Đặt tên tuỳ ý nhưng mọi thay đổi trạng thái phải nằm trong `ExamServer`. `ClientHandler` chỉ đọc dòng, parse, kiểm tra trường, gọi `ExamServer`, và gửi trả lời. `Session` là `static class` lồng trong `ExamServer`.

**Yêu cầu:** toàn bộ mục 2.5.2, 2.6, 2.7, 2.11 (nạp đề, `results.csv`). Câu hỏi gửi trong `EXAM_START` là bản sao **không có** trường `answer`. Hẹn giờ tự kết thúc dùng `ScheduledExecutorService` với lý do `Hết giờ`. `FEATURES` chỉ được lưu khi `values` là mảng đúng 6 phần tử số và `window` là số nguyên; sai thì bỏ qua và ghi log. Mỗi `VIOLATION` tăng số vi phạm của session và cập nhật "vi phạm gần nhất" thành `<kind>: <detail>`, ghi log. Chưa làm UDP và ML ở Task này.

**Test bắt buộc (`GradingTest`)** (dùng một đề 4 câu tạo trong test):
- `allCorrectGivesTen`: score 10.0, correct 4, total 4.
- `allWrongGivesZero`.
- `blankAnswersCountAsWrong`: chỉ trả lời đúng 1 câu → score 2.5.
- `nonNumericAnswerIsWrong`: giá trị `"abc"` không làm ném exception, tính sai.
- `roundsToTwoDecimals`: đề 3 câu đúng 1 → 3.33.

**Test bắt buộc (`ExamServerTest`)** — chạy server thật trên cổng 0, đề tạm 2 câu `durationMinutes` 1 ghi vào thư mục tạm, `results.csv` trỏ vào thư mục tạm; client test là một lớp tiện ích lồng trong file test, mở `Socket` tới `localhost`, có `send(JsonObject)` và `JsonObject next(String type, int timeoutMs)` (đọc bỏ qua các tin nhắn khác loại cho tới khi gặp `type` cần tìm, quá giờ thì fail):
- `loginOkContainsRules`: `LOGIN` hợp lệ nhận `LOGIN_OK` có `rules`.
- `emptyIdIsRejected`: mã SV rỗng → `LOGIN_FAIL`.
- `duplicateLoginWhileConnectedFails`: hai kết nối cùng mã SV, kết nối thứ hai nhận `LOGIN_FAIL`.
- `examNotSentBeforePrecheck`: đăng nhập, `startExam()`, chưa gửi PRECHECK → không nhận `EXAM_START` trong 500 ms; gửi `PRECHECK_RESULT` đạt → nhận `EXAM_START`.
- `examQuestionsHaveNoAnswers`: mọi câu trong `EXAM_START` không có trường `answer`.
- `submitReturnsResult`: nộp đúng hết → `RESULT` có `score` 10.0, trạng thái trong `rows()` là `Đã nộp`.
- `reconnectGetsExamWithRemainingTime`: đang thi thì đóng socket; `rows()` chuyển `Mất kết nối` (chờ tối đa 2 giây); kết nối lại cùng mã SV → `LOGIN_OK` rồi `EXAM_START` với `remainingSeconds` trong khoảng (0, 60].
- `disconnectOfOldSocketDoesNotKickNewOne`: kết nối A đăng nhập, đóng A, ngay sau đó B đăng nhập cùng mã SV; sau 1 giây trạng thái **không** phải `Mất kết nối`.
- `submitAfterEndIsAcceptedAndWritten`: đang thi, gọi `endExam("test")`, client nhận `EXAM_END`, sau đó gửi `SUBMIT` → nhận `RESULT`, và `results.csv` có dòng của mã SV đó với điểm.
- `garbageLineDoesNotCloseConnection`: gửi dòng `abc{`, sau đó `LOGIN` vẫn nhận `LOGIN_OK`.
- `badFeaturesIgnored`: gửi `FEATURES` với `values` 3 phần tử → server không sập, kết nối vẫn sống (gửi tiếp `SUBMIT` vẫn nhận `RESULT`).
- `violationCountedAndShownInRows`: gửi `VIOLATION` `USB`/`E:\` → `rows()` có số vi phạm 1, vi phạm gần nhất chứa `USB`.
- `loadExamRejectsBadAnswerIndex`: đề có `answer` = 7 với 4 lựa chọn → `IllegalArgumentException`.

---

### Task 4 — Tìm server qua UDP · nhánh `feature/udp-discovery`

**File:** sửa `ExamServer.java`; tạo `src/main/java/exam/client/ServerFinder.java`, `src/test/java/exam/DiscoveryTest.java`.

**Giao diện:**

```java
// ExamServer: thêm
public void startDiscovery(int udpPort) throws SocketException;   // luồng daemon trả lời EXAM_DISCOVER
// ServerFinder
public final class ServerFinder {
    public static InetSocketAddress find(int udpPort, int timeoutMs);   // null nếu không tìm thấy
    static List<InetAddress> targets();                                 // 255.255.255.255, broadcast từng card, 127.0.0.1
}
```

**Yêu cầu:** đúng mục 2.5.1. `DatagramSocket` phía client bật `setBroadcast(true)`. Lỗi gửi tới một địa chỉ thì bỏ qua địa chỉ đó, tiếp tục các địa chỉ khác. `stop()` của server đóng cả socket UDP.

**Test bắt buộc (`DiscoveryTest`)** — dùng cổng UDP ngẫu nhiên trong khoảng 20000–29999 để tránh trùng:
- `findsLocalServer`: server `start(0)` + `startDiscovery(port)`; `find(port, 2000)` trả địa chỉ có cổng bằng `tcpPort()`.
- `returnsNullWhenNoServer`: không có server → `find` trả `null` sau khoảng `timeoutMs` (không treo).
- `targetsIncludeLoopbackAndGlobalBroadcast`: `targets()` chứa `127.0.0.1` và `255.255.255.255`.

---

### Task 5 — Giám sát ở client · nhánh `feature/monitor`

**File:** `src/main/java/exam/client/Monitor.java`, `src/test/java/exam/MonitorTest.java`.

**Giao diện:** đúng mục 2.8.2, thêm các phương thức package-private để test không cần Windows:

```java
static Set<String> parseTasklist(String output);     // tên tiến trình chữ thường
static Set<String> parseNetstat(String output);      // IP đích theo quy tắc mục 2.8.2
synchronized JsonObject buildFeatures();             // tạo tin FEATURES của cửa sổ hiện tại rồi reset bộ đếm
synchronized void processSnapshot(Set<String> processes, Set<String> roots, Set<String> remoteIps);
    // áp dụng một lần quét: cập nhật bộ đếm, gửi VIOLATION qua sender; scan() thật chỉ lấy dữ liệu rồi gọi hàm này
void setBaselineForTest(Set<String> processes, Set<String> roots, Set<String> remoteIps,
                        JsonObject rules, Map<String, String> blockedIpToDomain, Consumer<JsonObject> sender);
```

**Yêu cầu:** đúng mục 2.8. Tách phần *lấy dữ liệu* (chạy lệnh) khỏi phần *xử lý* (`processSnapshot`) để test được trên Linux.

**Test bắt buộc (`MonitorTest`):**
- `parseTasklistReadsFirstColumnLowercase`: chuỗi mẫu 3 dòng CSV kiểu `"Chrome.exe","1234","Console","1","100,000 K"` → tập `{chrome.exe, ...}`.
- `parseNetstatKeepsEstablishedRemoteIps`: chuỗi mẫu có dòng `ESTABLISHED`, `LISTENING`, `SYN_SENT`, IPv6 `[2001:db8::1]:443`, `127.0.0.1`, `0.0.0.0` → chỉ còn IP của `ESTABLISHED`/`SYN_SENT`, không có loopback hay `0.0.0.0`, IPv6 bỏ ngoặc vuông.
- `newBlacklistedProcessIsViolation`: baseline không có `chrome.exe`; snapshot có → đúng một `VIOLATION` kind `APP`.
- `whitelistedProcessIsNotViolationButCountsAsNew`: `calculatorapp.exe` mới → không có `VIOLATION`, nhưng `buildFeatures()` có `values[0] = 1`.
- `systemProcessesNeverCount`: `tasklist.exe`, `conhost.exe`, `runtimebroker.exe` mới → không `VIOLATION`, `values[0] = 0`.
- `newDriveIsUsbViolation`: snapshot có thêm `E:\` → `VIOLATION` kind `USB`.
- `blockedIpIsNetViolation`: IP nằm trong bảng IP bị cấm → `VIOLATION` kind `NET`, `detail` chứa tên miền.
- `sameViolationSentOnce`: cùng một snapshot vi phạm áp dụng 3 lần → chỉ 1 `VIOLATION`.
- `featuresHaveSixValuesAndReset`: sau vài sự kiện, `buildFeatures()` có `window` = 1 và 6 giá trị; gọi lần hai → `window` = 2 và các giá trị về 0.
- `focusLostTwiceCountsOnce`: `focusLost(); focusLost(); focusGained();` → `values[1] = 1`.
- `answersCounted`: gọi `recordAnswer()` 3 lần → `values[5] = 3`.

---

### Task 6 — Ứng dụng sinh viên · nhánh `feature/student-client`

**File:** `src/main/java/exam/client/ServerConnection.java`, `src/main/java/exam/client/StudentApp.java`.

**Giao diện `ServerConnection`:**

```java
public class ServerConnection {
    public ServerConnection(InetSocketAddress address, Consumer<JsonObject> onMessage,
                            Runnable onDisconnected, Runnable onReconnected);
    public void connect() throws IOException;   // timeout kết nối 5 giây; bật luồng đọc
    public void send(JsonObject m);             // bỏ qua nếu đang mất kết nối
    public void close();                        // đóng hẳn, không kết nối lại
}
```

Luồng đọc: mỗi dòng parse thành JSON rồi gọi `onMessage` (dòng hỏng thì bỏ qua). Khi luồng đọc kết thúc mà chưa `close()`: gọi `onDisconnected`, rồi cứ 5 giây thử `connect()` lại; thành công thì gọi `onReconnected`.

**StudentApp:** `public static void main(String[] args)`, đúng mục 2.6, 2.7 (phía client) và 2.10. Gọi `Monitor` theo API Task 5: `precheck` chạy ngoài luồng giao diện; `start(conn::send)` khi nhận `EXAM_START` lần đầu; `stop()` khi nộp bài; `recordAnswer()` khi chọn đáp án; `focusLost/focusGained` từ `WindowFocusListener` với điều kiện `getOppositeWindow() == null`. `onReconnected` gửi lại `LOGIN`. Nhận `RESULT` thì `close()` kết nối.

**Test:** không có test tự động cho giao diện. Điều kiện hoàn thành của Task này là `mvn -q test` xanh và `mvn -q package` thành công. Kiểm tra chạy thật giao diện sinh viên được làm cùng lúc với giao diện giám thị ở Task 7.

---

### Task 7 — Giao diện giám thị · nhánh `feature/teacher-ui`

**File:** `src/main/java/exam/server/TeacherWindow.java`; sửa `ExamServer.java` (thêm `main`).

**Yêu cầu:** đúng mục 2.10 (TeacherWindow).
- `ExamServer.main(String[] args)`: đọc `config/rules.json` (hoặc đường dẫn ở `args[0]` nếu có), tạo server, `start(Protocol.TCP_PORT)`, `startDiscovery(Protocol.UDP_PORT)`, mở `TeacherWindow`. Không đọc được file luật → in lỗi rõ ràng và thoát.
- `TeacherWindow` nhận `ExamServer` qua constructor, đăng ký `setListener` để thêm dòng vào ô log (qua `invokeLater`). Đóng cửa sổ thì `server.stop()` và thoát chương trình.
- Bấm "Kết thúc thi" có hộp xác nhận.

**Test:** thủ công trên Linux, ghi vào mô tả PR: chạy server và 2 client trên cùng máy (client tự tìm được server qua UDP), đi hết luồng đăng nhập → bắt đầu → làm bài → nộp → bảng cập nhật, `results.csv` có 2 dòng; tắt một client giữa giờ → `Mất kết nối`, mở lại đăng nhập cùng mã SV → vào lại được với thời gian còn lại.

---

### Task 8 — Service ML · nhánh `feature/ml-service`

**File:** `ml/ml_service.py`, `ml/test_ml_service.py`.

**Giao diện:**

```python
class Sample(BaseModel):      # pydantic
    studentId: str
    window: int
    values: list[float] = Field(min_length=6, max_length=6)

class ScoreRequest(BaseModel):
    samples: list[Sample]

MIN_SAMPLES = 20
RECENT = 3
def score_samples(samples: list[Sample]) -> dict: ...
app = FastAPI()
@app.post("/score")
def score(req: ScoreRequest) -> dict: ...
```

**Yêu cầu:** đúng mục 2.9 (phần service).

**Test bắt buộc (`test_ml_service.py`, dùng `fastapi.testclient.TestClient`):**
- `test_not_enough_data`: 19 mẫu → `status` = `not_enough_data`.
- `test_outlier_flagged`: 6 sinh viên "bình thường" × 5 cửa sổ (giá trị ngẫu nhiên nhỏ 0–1 với `random.seed(0)`, `answers` 0–3) + 1 sinh viên × 5 cửa sổ có `focusLostCount` 6, `focusLostSeconds` 20, `answers` 8 → sinh viên lệch có `anomaly` = true và `score` lớn nhất phòng.
- `test_identical_room_no_anomaly`: 25 mẫu giống hệt nhau → mọi `score` = 0 và mọi `anomaly` = false.
- `test_uses_three_latest_windows`: một sinh viên có cửa sổ 1 cực lệch nhưng cửa sổ 2–5 bình thường như cả phòng → `anomaly` = false.
- `test_wrong_length_rejected`: `values` 5 phần tử → HTTP 422.

---

### Task 9 — Nối server với ML · nhánh `feature/ml-integration`

**File:** `src/main/java/exam/server/MlClient.java`, `src/test/java/exam/MlClientTest.java`; sửa `ExamServer.java`.

**Giao diện:**

```java
public class MlClient {
    public MlClient(String baseUrl);                                  // ví dụ "http://localhost:8000"
    public JsonObject score(JsonArray samples) throws IOException, InterruptedException;
}
// ExamServer: thêm
public void setMlClient(MlClient ml);       // null = không chấm bất thường
void scoreRoomNow();                         // package-private, gọi được từ test; vòng 30 giây gọi hàm này
```

**Yêu cầu:** đúng mục 2.9 (phần Java). `ExamServer.main` tạo `MlClient("http://localhost:8000")`. Vòng 30 giây chỉ chạy khi bài thi đang diễn ra. Kết quả có `status` khác `ok` thì không cập nhật gì. `studentId` trong kết quả không có session tương ứng thì bỏ qua.

**Test bắt buộc (`MlClientTest`)** — dựng service giả bằng `com.sun.net.httpserver.HttpServer` trên cổng 0:
- `sendsSamplesAndParsesResult`: service giả kiểm tra body có mảng `samples`, trả kết quả cố định → `score()` trả đúng object.
- `non200ThrowsIOException`: service giả trả 500 → `IOException`.
- `serverAppliesScores`: server thi thật + 1 client test đã vào thi và gửi 1 `FEATURES`; ML giả trả `anomaly` true, `score` 0.9 cho mã SV đó; gọi `scoreRoomNow()` → `rows()` có `anomalyScore` 0.9 và `anomaly` true.
- `mlDownKeepsOldScore`: trỏ `MlClient` tới cổng không có ai nghe → `scoreRoomNow()` không ném exception, điểm cũ giữ nguyên.

---

### Task 10 — Giả lập client · nhánh `feature/simulator`

**File:** `ml/simulate_clients.py`.

**Yêu cầu:**
- Tham số: `--host` (mặc định `127.0.0.1`), `--port` (5000), `--count` (5), `--cheater` (thêm 1 client gian lận), `--interval` (30, số giây giữa hai `FEATURES`; đặt nhỏ để test nhanh).
- Mỗi client một luồng: kết nối TCP thật, mã SV `SIM01`, `SIM02`... (client gian lận là `SIM-X`), họ tên `Máy giả 1`... Đọc và ghi bằng hai `makefile` riêng (`"r"` và `"w"`, UTF-8), ghi có khoá vì hai luồng cùng ghi.
- Nhận `LOGIN_OK` → gửi `PRECHECK_RESULT` đạt. Nhận `EXAM_START` lần đầu → bật luồng gửi `FEATURES` mỗi `interval` giây. Nhận `EXAM_END` → gửi `SUBMIT` với đáp án ngẫu nhiên. Nhận `RESULT` → in điểm và kết thúc. Nhận `LOGIN_FAIL` → in lý do và kết thúc.
- Giá trị `FEATURES`: bình thường `[choice(0,0,0,1), choice(0,0,1), choice(0,0,1,2), choice(0,0,1), 0, randint(0,3)]`; gian lận `[randint(0,1), randint(4,8), randint(15,25), randint(0,2), 0, randint(5,10)]`.
- Mất kết nối thì in thông báo và kết thúc luồng đó (không kết nối lại).

**Test:** thủ công, ghi vào mô tả PR: chạy ML + server + `python simulate_clients.py --count 5 --cheater --interval 3`, bắt đầu thi; sau khoảng 30 giây `SIM-X` có cột Bất thường cao nhất và tô đỏ.

---

### Task 11 — Tài liệu và kiểm tra cuối · nhánh `feature/docs`

**File:** `README.md`.

**Nội dung README:**
1. Giới thiệu, kiến trúc (sơ đồ ASCII: 3 client ⇄ TCP/UDP ⇄ server ⇄ HTTP ⇄ ML service).
2. Bảng phân công (mục 2.4) kèm "người này cần hiểu những gì" (2–3 gạch đầu dòng mỗi người).
3. Bảng giao thức (mục 2.5).
4. Cài đặt: JDK 17 + Maven (máy server), Java 17 (máy client), Python venv + `pip install -r ml/requirements.txt`.
5. Build và chạy (thứ tự: ML → server → client), lệnh `mvn -q package`.
6. Mạng: ZeroTier, hotspot, mở firewall Fedora và Windows (mục 2.12).
7. Kịch bản demo (mục 2.13).
8. Checklist test thủ công trên Windows: PRECHECK với Zalo/Chrome mở; cắm USB khi đang thi; vào chatgpt.com; chuyển qua lại với Calculator; tắt Wi-Fi rồi bật lại; tìm server qua UDP; nhập IP thủ công.
9. Giới hạn đã biết: luật NET không bắt được 100% vì CDN; Isolation Forest chỉ phát hiện "khác số đông"; đáp án mất nếu tắt hẳn app; chưa có xác thực và mã hoá.

**Hoàn thành khi:** `mvn -q test` xanh, `pytest -q` trong `ml/` xanh, `mvn -q package` tạo `target/exam.jar`, README đủ 9 mục. Sau khi merge Task 11, trên `main` chạy lại toàn bộ test lần cuối và báo cho người dùng: danh sách PR đã merge, kết quả test, và những việc cần kiểm tra thủ công trên Windows.

---

## Phần 6. Báo cáo cho người dùng khi xong

Khi hoàn thành toàn bộ, gửi người dùng một báo cáo ngắn gồm:
- Danh sách 11 nhánh và link PR đã merge.
- Kết quả `mvn -q test` và `pytest -q` trên `main`.
- Những lựa chọn đã tự quyết khi tài liệu không nói rõ (lấy từ mô tả các PR).
- Những gì **chưa** kiểm tra được vì cần máy Windows thật (toàn bộ checklist mục 8 của README).
