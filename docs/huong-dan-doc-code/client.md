# Client: máy sinh viên và giám sát

Toàn bộ `src/main/java/exam/client/`: từ lúc mở app đến lúc xem điểm, cộng với phần giám sát (phát hiện vi phạm) chạy ngầm trên máy sinh viên. Client **chỉ phát hiện và báo cáo**, không chặn gì, không cần quyền admin. Luật nằm ở `config/rules.json` phía server và được gửi xuống khi đăng nhập.

> Các đoạn code trong tài liệu này **chỉ để minh hoạ**, không áp dụng vào code thật.

## 1. Bảng file

| File | Một câu |
|---|---|
| `StudentApp` | Logic: kết nối, xử lý từng loại tin từ server, chuyển màn hình, lưu đáp án, đồng hồ, nộp bài, gọi `Monitor`. |
| `StudentScreens`, `TopBar`, `Sheet` | Ghép giao diện bằng `CardLayout` 5 màn; thanh trên (tên kỳ thi, đồng hồ đổi cam/đỏ, chấm kết nối, nút Nộp bài); hộp xác nhận kiểu "tấm". Nút bấm gọi ngược về `StudentApp`. |
| `LoginPanel`, `PrecheckPanel`, `ResultPanel` | Đăng nhập (Mã SV, Mật khẩu, IP server); PRECHECK (danh sách việc cần làm, tự kiểm tra lại sau 5 giây) và màn chờ; kết quả (giờ nộp, Điểm, Số câu đúng, lời cảm ơn, Thoát). Không có nút thi lại: thi kỳ mới thì tắt app rồi mở lại. |
| `ExamPanel`, `NumberButton`, `OptionRow` | Màn làm bài: lưới số câu, hàng lựa chọn bấm được, phím tắt. |
| `Theme` | Font, `Theme.install()` (gọi `common/Look.install`), vài hàm dựng nút/nhãn; màu và bo góc dùng chung nằm ở `common/Palette`, `Card`, `Look` (Server 2 quản lý). |
| `ServerConnection`, `ServerFinder` | Kết nối TCP + luồng đọc dòng JSON + tự nối lại mỗi 5 giây; tìm server trong LAN bằng UDP broadcast. |
| `Monitor` | Luật giám sát: baseline lúc PRECHECK, quét mỗi 3 giây ra vi phạm APP/USB/NET, đếm focus và đáp án, gom 6 feature mỗi chu kỳ server cấp, báo danh sách app (`APPS`), `updateRules` áp luật mới. Không chạy lệnh Windows nên test được trên Linux. |
| `AppRules` | Luật về tên app: chuẩn hoá (chữ thường, bỏ `.exe`), blacklist/whitelist/`allowedWindowApps`/`ignoreProcesses`, cờ `-Dexam.allowTerminal` cho dev. |
| `WindowsProbe` | Nói chuyện với Windows: `tasklist`, `netstat -no` (kèm PID), PowerShell (app có cửa sổ), phân giải DNS. Không biết gì về luật. |
| `Diagnose`, `Debug` | Chẩn đoán tay trên máy Windows (ghi `chan-doan-<tên máy>.txt`); log gỡ lỗi khi chạy `-Dexam.debug=true`. |
| `config/rules.json` | Luật giám sát và `featureIntervalSeconds`; do Server 2 sửa qua giao diện, client đọc qua `LOGIN_OK`. |

## 2. Luồng chạy

**1. Từ "Đăng nhập" đến khi đề hiện**

1. `LoginPanel` → `StudentApp.login` → `screens.setLoginBusy` rồi chạy `StudentApp.connect(host)` trên luồng mới.
2. Ô IP trống thì `ServerFinder.find(9999, 3000)`: gửi `EXAM_DISCOVER` tới `255.255.255.255`, broadcast từng card mạng và `127.0.0.1`, `waitForReply` nhận `EXAM_SERVER 5000`.
3. `new ServerConnection` → `ServerConnection.connect` (timeout 5 giây, bật `readLoop`) → `StudentApp.sendLogin` (`studentId`, `password`, `os`).
4. `LOGIN_OK` (kèm `rules`, `name`, `featureIntervalSeconds`) → `readLoop` → `invokeLater` → `StudentApp.onMessage` → `onLoginOk` → `Monitor.useIntervalFrom` → `runPrecheck`. Sai thì `LOGIN_FAIL` → `onLoginFail`.
5. `runPrecheck` chạy `Monitor.precheck` → `precheckFrom` trên luồng riêng, gửi `PRECHECK_RESULT`; đạt thì `screens.showWaiting` và `Monitor.reportApps`.
6. `EXAM_START` → `onExamStart` → `monitor.start(conn::send)` → `countdown` → `showCurrentQuestion`. Nộp: `submit` → `finish` → `sendSubmit`; `RESULT` → `onResult` → `screens.showResult`.

**2. Cắm USB đến khi giám thị thấy vi phạm**

1. `Monitor.precheck` lưu `baselineRoots` (vd `C:\`). Trong giờ thi `Monitor.start` hẹn `scan` mỗi 3 giây (luồng `monitor-scan`; `FEATURES` có luồng `monitor-features` riêng).
2. `scan` → `processSnapshot(WindowsProbe.processList(), roots(), remoteConnections())`. `E:\` không có trong baseline → `Monitor.violate("USB", "E:\")`.
3. `violate` kiểm tra `sentViolations` (mỗi cặp kind/detail một lần), tạo tin `VIOLATION`, gọi `sender` = `conn::send`. Server nhận ở `ClientHandler.handleViolation` (xem `server-1.md`).

**3. Rớt mạng rồi vào lại**

- `ServerConnection.readLoop` kết thúc → `onDisconnected` → `reconnect` mỗi 5 giây → `onReconnected` → `StudentApp.sendLogin`. Server nhận ra mã SV cũ và gửi lại `EXAM_START` (còn giữ đáp án và câu đang xem) hoặc `RESULT` nếu đã nộp.

## 3. Thầy bảo sửa gì → mở đâu, đổi gì

| Thầy bảo | File → hàm | Đổi gì |
|---|---|---|
| Hiện số câu chưa làm | `StudentApp.onAnswer`, `showCurrentQuestion` | Thêm hàm `showUnanswered` tính `questions.size() - answers.size()` rồi `screens.setStatus(...)`; gọi ở cuối hai hàm trên. |
| Cảnh báo khi còn 1 phút | `StudentApp.tick`, `Theme.timerColor` | Trong `tick`: `if (left == 60) screens.setStatus("Chỉ còn 1 phút!")`. Đổi ngưỡng màu đồng hồ ở `Theme.timerColor` (cam dưới 5 phút, đỏ dưới 1 phút). |
| Cho đánh dấu câu để xem lại | `StudentApp` + `ExamPanel` + `StudentScreens` | Thêm `Set<Integer> flagged` và `toggleFlag()` ở `StudentApp`; nút "Đánh dấu" trong `ExamPanel`, thêm tham số `Runnable onFlag` vào constructor `ExamPanel` và `StudentScreens`. |
| Đổi chu kỳ quét từ 3 giây thành 5 giây | `Monitor()` | Constructor mặc định `this(null, 3000, ...)`: đổi `3000` thành `5000`. |
| Đổi chu kỳ gửi FEATURES | `config/rules.json` | `featureIntervalSeconds` (10 đến 60). Client đọc ở `Monitor.useIntervalFrom`, không sửa code. |
| Thêm app vào danh sách cấm / cho phép | Không cần sửa code | Giám thị bấm **Ứng dụng** (Server 2) hoặc sửa `config/rules.json`. Khi báo oan: chạy `Diagnose` rồi thêm tên vào `ignoreProcesses` hoặc `allowedWindowApps`. |
| Thêm một loại vi phạm mới | `Monitor.processSnapshot`/`applySnapshot`, `violate` | Thêm kiểm tra trong `applySnapshot` rồi gọi `violate("LOẠI", "chi tiết")`; mỗi cặp chỉ gửi một lần. Server đã nhận mọi `kind`. |
| Chặn terminal / công cụ tải tài liệu | `config/rules.json`, `AppRules.TERMINALS` | Terminal mở trong giờ thi là vi phạm; `git`, `curl`, `ssh`... nằm trong `blacklistApps`. Dev dùng cờ `-Dexam.allowTerminal=true`. |
| Đổi màn bị đình chỉ | `StudentApp.onResult`, `ResultPanel.showTerminated` | `RESULT` có `terminated: true` thì dừng giám sát/đồng hồ và hiện màn đỏ (tiêu đề `TERMINATED_TITLE`, lý do `reasonText`, điểm 0). |
| Đổi thông báo / màu màn kết quả | `ResultPanel.showResult`, `Theme` | Sửa nhãn trong `showResult`; màu theo `Theme`/`Palette`. |
| Đổi tên miền bị chặn | `config/rules.json` → `blockedDomains` | Thêm tên miền; `Monitor.resolveBlockedDomains` phân giải IP một lần lúc bắt đầu thi. |

## 4. Câu hỏi thường gặp

**1. Socket TCP phía client hoạt động thế nào?** `Socket.connect(address, timeout)` rồi đọc/ghi qua `BufferedReader`/`PrintWriter` UTF-8; mỗi tin là một dòng JSON kết thúc bằng `\n`, nên `readLine` đọc đúng một tin.

**2. Vì sao cần luồng đọc riêng và `invokeLater`?** `readLine` chặn nên đọc trên luồng giao diện thì cửa sổ đứng. Luồng đọc nhận tin rồi chuyển sang `SwingUtilities.invokeLater`, vì Swing không an toàn đa luồng: chỉ luồng giao diện (EDT) được sửa thành phần.

**3. UDP broadcast tìm server ra sao, vì sao gửi từng card mạng?** Client chưa biết IP server nên gửi `EXAM_DISCOVER` tới địa chỉ quảng bá; server trả lời thẳng về người gửi. Máy có ZeroTier/VPN có nhiều card, chỉ gửi `255.255.255.255` thì Java có thể đẩy gói ra nhầm card.

**4. Tự nối lại thế nào, đáp án có mất không?** Luồng đọc kết thúc mà chưa `close()` thì `ServerConnection` báo mất kết nối, 5 giây thử một lần, xong `onReconnected` gửi lại `LOGIN`. Đáp án nằm trong bộ nhớ nên còn; tắt hẳn app thì mất.

**5. "Rời cửa sổ" được đếm thế nào?** Chỉ tính khi `getOppositeWindow() == null` (focus sang app khác). Hộp xác nhận nộp bài có cửa sổ chính làm cha nên không bị tính. Đóng cửa sổ khi chưa nộp thì `requestExit` hỏi "Nộp bài và thoát" hoặc "Tiếp tục làm bài".

**6. Baseline là gì, vì sao cần?** Ảnh chụp trạng thái máy ở PRECHECK đạt cuối cùng (tiến trình, ổ đĩa, IP). Trong giờ thi chỉ cái **mới** mới đáng chú ý; không có baseline thì mọi tiến trình Windows chạy sẵn đều bị báo.

**7. Vì sao chỉ xét app có cửa sổ?** Bản đầu coi mọi tiến trình mới là vi phạm nên Windows, driver, OneDrive... bị báo oan. Thứ sinh viên dùng để gian lận là app họ nhìn và gõ vào, tức có cửa sổ. Nên tiến trình nền không bao giờ vi phạm (chỉ đếm vào feature `newProcesses`), app có cửa sổ ngoài `whitelistApps`/`allowedWindowApps` thì vi phạm, app trong `blacklistApps` luôn vi phạm kể cả không cửa sổ. `AppRules.SYSTEM_PROCESSES` là danh sách cứng, `ignoreProcesses` chỉnh được ở server.

**8. Vì sao hỏi cửa sổ bằng PowerShell chỉ khi có tiến trình mới?** `Get-Process` tốn 0,3 đến 1 giây. Mỗi 3 giây chỉ chạy `tasklist` (rẻ); có tiến trình mới mới gọi `Get-Process -Id ...`, tối đa 10 lần quét cho mỗi tiến trình vì cửa sổ có thể hiện chậm.

**9. Vì sao `FEATURES` có luồng riêng và vì sao là số đếm?** Quét chậm (tới 10 giây) không được làm trễ `FEATURES`, vì server ngắt máy sau 90 giây im lặng. Dùng số đếm vì laptop khác cấu hình nên CPU%/RAM không so sánh được, còn số lần thì công bằng.

**10. Vì sao chỉ phát hiện, không chặn?** Không cần quyền admin, ít rủi ro làm hỏng máy, và việc kết luận gian lận thuộc về giám thị. Giới hạn của luật NET: tên miền phân giải một lần nên trang dùng CDN đổi IP sẽ lọt; không đọc cột trạng thái `netstat` nên kết nối `TIME_WAIT` cũng được tính (vì vậy mỗi tiến trình/IP mới chỉ đếm một lần).
