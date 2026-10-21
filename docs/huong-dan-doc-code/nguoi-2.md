# Người 2: giám sát và giao diện giám thị

Mảng của bạn: phát hiện vi phạm trên máy sinh viên và hiển thị chúng cho giám thị. Client **chỉ phát hiện và báo cáo**, không chặn gì.

> Các đoạn code trong tài liệu này **chỉ để minh hoạ**, không áp dụng vào code thật.

## 1. Bạn phụ trách gì

| File | Làm gì |
|---|---|
| `client/Monitor.java` | Luật giám sát: baseline lúc PRECHECK, so sánh mỗi 3 giây để ra vi phạm APP/USB/NET, đếm focus và đáp án, gom 6 feature mỗi 30 giây (luồng riêng với luồng quét). Không chạy lệnh Windows nên test được trên Linux. |
| `client/AppRules.java` | Luật về tên app: chuẩn hoá tên (chữ thường, bỏ `.exe`), blacklist/whitelist/`allowedWindowApps`/`ignoreProcesses`, ra các vấn đề PRECHECK về app. |
| `client/WindowsProbe.java` | Nói chuyện với Windows: chạy `tasklist` (tên + PID), `netstat`, PowerShell (app có cửa sổ), phân giải DNS. Không biết gì về luật. |
| `client/Diagnose.java` | Công cụ chẩn đoán chạy tay trên máy Windows, ghi `chan-doan-<tên máy>.txt`. |
| `client/Debug.java` | Log gỡ lỗi khi chạy với `-Dexam.debug=true` (thời gian mỗi lần quét, nội dung `FEATURES`). |
| `server/TeacherWindow.java` | Cửa sổ giám thị (FlatLaf): thanh trên (Mở đề, đồng hồ, địa chỉ, nút Bắt đầu/Kết thúc/Kỳ thi mới), tab lọc và ô tìm kiếm, nhật ký; nhấp đúp một dòng mở lịch sử. |
| `server/RosterTable.java`, `Roster.java`, `DetailPanel.java` | Bảng thí sinh (biểu tượng cảnh báo, nhãn trạng thái, thanh Bất thường, dòng cần chú ý tô cam nhạt), logic thuần của bảng (lọc theo tab, tìm kiếm không dấu, chữ hiển thị, sự kiện đáng chú ý) và khung chi tiết bên phải. |
| `config/rules.json` | Luật: `blacklistApps`, `whitelistApps`, `allowedWindowApps`, `ignoreProcesses`, `blockedDomains`. Nằm ở server, gửi xuống client khi đăng nhập, nên chỉnh không cần build lại hay gửi lại jar. |

## 2. Thứ tự đọc

1. `AppRules` (ngắn): chuẩn hoá tên và các tập luật. Đọc trước để hiểu "bị cấm", "được phép có cửa sổ", "bỏ qua".
2. `Monitor.precheck` → `precheckFrom`: kiểm tra trước giờ thi (app bị cấm, app có cửa sổ không được phép, USB) và **lưu baseline**.
3. `Monitor.processSnapshot`: một lần quét: tiến trình mới nào bị cấm thì vi phạm ngay; tiến trình mới khác chỉ được hỏi "có cửa sổ không" (PowerShell) rồi mới quyết định.
4. `Monitor.violate`: tạo tin `VIOLATION`, mỗi cặp (`kind`, `detail`) chỉ gửi một lần.
5. `Monitor.buildFeatures`: ghép 6 số của một cửa sổ (mặc định 30 giây, server cấp trong `LOGIN_OK`) rồi reset bộ đếm.
6. `WindowsProbe.parseTasklistProcs`, `parseWindows`, `parseNetstat`, `run`: đọc kết quả lệnh và chạy lệnh có timeout 10 giây.
7. `Monitor.start` rồi `scan`: hai luồng riêng, quét 3 giây và gửi `FEATURES` theo chu kỳ server cấp (`Monitor.useIntervalFrom`).
8. `Diagnose.processSections`: các mục báo cáo chẩn đoán.
9. `TeacherWindow.refresh` cùng `Roster.filter` và các lớp vẽ trong `RosterTable`.

## 3. Một luồng chạy thật: cắm USB đến khi dòng được tô cam

1. Lúc PRECHECK, `Monitor.precheck` gọi `WindowsProbe.roots()` và lưu vào `baselineRoots` (ví dụ chỉ có `C:\`).
2. Trong giờ thi, `Monitor.start` hẹn `scan` chạy mỗi 3 giây trên luồng `monitor-scan` (còn `FEATURES` có luồng `monitor-features` riêng). `scan` gọi `processSnapshot(WindowsProbe.processList(), WindowsProbe.roots(), WindowsProbe.remoteIps())`.
3. Sinh viên cắm USB nên `roots()` có thêm `E:\`. Trong `processSnapshot`, `E:\` không có trong `baselineRoots` → `violate("USB", "E:\")`.
4. `violate` kiểm tra `sentViolations` (chưa gửi cặp này), tăng `violationsInWindow`, tạo tin `{"type":"VIOLATION","kind":"USB","detail":"E:\\","time":"..."}` và gọi `sender` = `conn::send` của `StudentApp`.
5. Trên server, `ClientHandler.handle` nhận tin, gọi `ClientHandler.violation` rồi `ExamRoom.violation`: tăng `Session.violations`, đặt `lastViolation = "USB: E:\"`.
6. `TeacherWindow` có `Timer` 1 giây gọi `refresh`: lấy `room.rows()`, nạp vào bảng. `Roster.needsAttention(row)` đúng (có vi phạm hoặc bị ML gắn cờ) nên `RosterTable` tô nền cam nhạt dòng đó, hiện biểu tượng cảnh báo và đếm vào tab "Cần chú ý"; cột "Vi phạm gần nhất" hiện `USB E:\ · 09:41`.

## 4. Kiến thức cần giải thích được với thầy

- **`ProcessBuilder`:** chạy một lệnh ngoài (`tasklist`, `netstat`, `powershell`), gộp stderr vào stdout (`redirectErrorStream`), đọc output bằng một luồng riêng và chờ tối đa 10 giây (`waitFor`), hết giờ thì `destroyForcibly`. Lỗi thì trả chuỗi rỗng để việc quét không làm sập client.
- **Baseline là gì và vì sao cần:** ảnh chụp trạng thái máy ở lần PRECHECK đạt cuối cùng (tiến trình, ổ đĩa, IP đang kết nối). Trong giờ thi chỉ cái **mới** so với baseline mới đáng chú ý; nếu không, mọi tiến trình Windows đang chạy sẵn đều bị coi là vi phạm.
- **Vì sao feature là số đếm:** laptop cấu hình khác nhau nên CPU%/RAM không so sánh được với nhau; số lần/số lượng thì công bằng giữa các máy.
- **Vì sao chỉ phát hiện, không chặn:** không cần quyền admin, đơn giản và ít rủi ro làm hỏng máy sinh viên; việc kết luận gian lận thuộc về giám thị.
- **`SYSTEM_PROCESSES` và `ignoreProcesses`:** `tasklist` liệt kê cả chính nó, và Windows bật/tắt nhiều tiến trình nền; `SYSTEM_PROCESSES` (trong `AppRules`) là danh sách cố định, `ignoreProcesses` trong `rules.json` là danh sách chỉnh được ở server.
- **Vì sao chỉ xét app có cửa sổ?** Bản đầu coi *mọi* tiến trình mới là vi phạm nên Windows, driver âm thanh/đồ hoạ, OneDrive, trình cập nhật... bị báo oan liên tục, và danh sách tên không bao giờ đủ. Cái sinh viên dùng để gian lận (trình duyệt, chat, IDE có AI) là app họ **nhìn và gõ vào**, nghĩa là có cửa sổ; tiến trình nền không có cửa sổ thì không thể là thứ họ đang dùng. Nên: tiến trình nền không bao giờ vi phạm (chỉ đếm vào feature `newProcesses` cho ML), còn app có cửa sổ mà không thuộc `whitelistApps`/`allowedWindowApps` thì vi phạm. App nằm trong `blacklistApps` luôn vi phạm, kể cả không có cửa sổ. Hạn chế: app UWP hiện cửa sổ dưới `ApplicationFrameHost`, và app đã chạy sẵn lúc PRECHECK rồi mới mở cửa sổ thì không bị bắt (xem README mục 9).
- **Vì sao hỏi cửa sổ bằng PowerShell chỉ khi có tiến trình mới:** `Get-Process` tốn 0,3–1 giây mỗi lần. Mỗi 3 giây chỉ chạy `tasklist` (rẻ); chỉ khi có tiến trình mới (không thuộc danh sách bỏ qua) mới gọi `Get-Process -Id <pid,...>`, và tối đa 10 lần quét cho mỗi tiến trình (cửa sổ có thể hiện chậm hơn tiến trình vài giây).
- **Luồng riêng cho `FEATURES`:** quét chậm (tasklist/PowerShell treo tới 10 giây) không được làm trễ `FEATURES`, vì server ngắt máy sau 90 giây im lặng.
- **Giới hạn của luật NET:** tên miền chỉ được phân giải một lần lúc bắt đầu thi; trang dùng CDN đổi IP sẽ lọt; không đọc cột trạng thái của `netstat` nên kết nối đã đóng (`TIME_WAIT`) cũng được tính.

## 5. 3 câu thầy có thể hỏi và cách sửa

**a) "Thêm một app vào danh sách cấm / cho phép / bỏ qua."** Không cần sửa code Java, chỉ sửa `config/rules.json` ở máy server rồi chạy lại server (luật đọc lúc `ExamServer.main` khởi động, client nhận qua `LOGIN_OK`). Tên không phân biệt hoa thường, có hay không `.exe` đều được. Khi bị báo vi phạm oan: chạy `Diagnose` trên máy đó rồi chép tên vào `ignoreProcesses` hoặc `allowedWindowApps`.

```json
"blacklistApps": ["chrome.exe", "msedge.exe", "firefox.exe", "wechat"]
```

**b) "Thêm cột 'Thời gian rời cửa sổ' vào bảng giám thị."** Dữ liệu có sẵn trong `FEATURES` (giá trị thứ 3 là số giây rời cửa sổ của cửa sổ đó); cộng dồn ở server rồi hiện lên bảng.

```java
// Session.java
int focusLostSeconds;
// ExamRoom.features(Session s, JsonObject sample): cộng dồn giá trị thứ 3
s.focusLostSeconds += sample.getAsJsonArray("values").get(2).getAsInt();
// ExamRoom.Row: thêm trường int focusLostSeconds ở cuối; trong rows() truyền s.focusLostSeconds
// RosterTable: thêm "Rời cửa sổ (giây)" vào COLUMNS và WIDTHS, rồi thêm một nhánh vào Model.getValueAt:
case 10 -> row.focusLostSeconds();   // cột mới nằm sau "Điểm" (chỉ số 9)
```

**c) "Đổi chu kỳ quét từ 3 giây thành 5 giây."** Sửa `Monitor.start`:

```java
// Monitor.start: trước (scanMs = 3000) -> sau
// scanner.scheduleWithFixedDelay(() -> safely(scanTask), scanMs, scanMs, TimeUnit.MILLISECONDS);
public Monitor() { this(null, 5000, 30_000); }     // đổi 3000 thành 5000 ở constructor mặc định
```
