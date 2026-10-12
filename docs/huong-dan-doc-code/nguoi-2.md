# Người 2: giám sát và giao diện giám thị

Mảng của bạn: phát hiện vi phạm trên máy sinh viên và hiển thị chúng cho giám thị. Client **chỉ phát hiện và báo cáo**, không chặn gì.

> Các đoạn code trong tài liệu này **chỉ để minh hoạ**, không áp dụng vào code thật.

## 1. Bạn phụ trách gì

| File | Làm gì |
|---|---|
| `client/Monitor.java` | Luật giám sát: baseline lúc PRECHECK, so sánh mỗi 3 giây để ra vi phạm APP/USB/NET, đếm focus và đáp án, gom 6 feature mỗi 30 giây. Không chạy lệnh Windows nên test được trên Linux. |
| `client/WindowsProbe.java` | Nói chuyện với Windows: chạy `tasklist`, `netstat`, PowerShell, đọc kết quả, phân giải DNS. Không biết gì về luật. |
| `server/TeacherWindow.java` | Cửa sổ giám thị: chọn đề, bắt đầu/kết thúc, bảng sinh viên tô đỏ dòng có vi phạm hoặc bất thường, ô log. |
| `config/rules.json` | Luật: app bị cấm, whitelist, tên miền bị cấm. Nằm ở server và được gửi xuống client khi đăng nhập. |

## 2. Thứ tự đọc

1. `Monitor.precheck`: kiểm tra trước giờ thi (app bị cấm, USB) và **lưu baseline**.
2. `Monitor.processSnapshot`: một lần quét: so với baseline, quyết định tiến trình nào là vi phạm.
3. `Monitor.violate`: tạo tin `VIOLATION`, mỗi cặp (`kind`, `detail`) chỉ gửi một lần.
4. `Monitor.buildFeatures`: ghép 6 số của cửa sổ 30 giây rồi reset bộ đếm.
5. `WindowsProbe.parseTasklist`, `parseNetstat`, `run`: đọc kết quả lệnh và chạy lệnh có timeout 10 giây.
6. `Monitor.start` rồi `scan`: lịch quét 3 giây và gửi `FEATURES` 30 giây.
7. `TeacherWindow.refresh` cùng bộ tô màu dòng trong constructor.

## 3. Một luồng chạy thật: cắm USB đến khi dòng tô đỏ

1. Lúc PRECHECK, `Monitor.precheck` gọi `WindowsProbe.roots()` và lưu vào `baselineRoots` (ví dụ chỉ có `C:\`).
2. Trong giờ thi, `Monitor.start` hẹn `scan` chạy mỗi 3 giây trên luồng daemon. `scan` gọi `processSnapshot(WindowsProbe.processes(), WindowsProbe.roots(), WindowsProbe.remoteIps())`.
3. Sinh viên cắm USB nên `roots()` có thêm `E:\`. Trong `processSnapshot`, `E:\` không có trong `baselineRoots` → `violate("USB", "E:\")`.
4. `violate` kiểm tra `sentViolations` (chưa gửi cặp này), tăng `violationsInWindow`, tạo tin `{"type":"VIOLATION","kind":"USB","detail":"E:\\","time":"..."}` và gọi `sender` = `conn::send` của `StudentApp`.
5. Trên server, `ClientHandler.handle` nhận tin, gọi `ClientHandler.violation` rồi `ExamRoom.violation`: tăng `Session.violations`, đặt `lastViolation = "USB: E:\"`.
6. `TeacherWindow` có `Timer` 1 giây gọi `refresh`: lấy `room.rows()`, nạp vào bảng. Bộ tô màu thấy `row.violations() > 0` nên tô nền đỏ nhạt dòng đó.

## 4. Kiến thức cần giải thích được với thầy

- **`ProcessBuilder`:** chạy một lệnh ngoài (`tasklist`, `netstat`, `powershell`), gộp stderr vào stdout (`redirectErrorStream`), đọc output bằng một luồng riêng và chờ tối đa 10 giây (`waitFor`), hết giờ thì `destroyForcibly`. Lỗi thì trả chuỗi rỗng để việc quét không làm sập client.
- **Baseline là gì và vì sao cần:** ảnh chụp trạng thái máy ở lần PRECHECK đạt cuối cùng (tiến trình, ổ đĩa, IP đang kết nối). Trong giờ thi chỉ cái **mới** so với baseline mới đáng chú ý; nếu không, mọi tiến trình Windows đang chạy sẵn đều bị coi là vi phạm.
- **Vì sao feature là số đếm:** laptop cấu hình khác nhau nên CPU%/RAM không so sánh được với nhau; số lần/số lượng thì công bằng giữa các máy.
- **Vì sao chỉ phát hiện, không chặn:** không cần quyền admin, đơn giản và ít rủi ro làm hỏng máy sinh viên; việc kết luận gian lận thuộc về giám thị.
- **`SYSTEM_PROCESSES`:** `tasklist` liệt kê cả chính nó, và Windows bật/tắt nhiều tiến trình nền; không loại thì máy nào cũng báo vi phạm.
- **Giới hạn của luật NET:** tên miền chỉ được phân giải một lần lúc bắt đầu thi; trang dùng CDN đổi IP sẽ lọt; không đọc cột trạng thái của `netstat` nên kết nối đã đóng (`TIME_WAIT`) cũng được tính.

## 5. 3 câu thầy có thể hỏi và cách sửa

**a) "Thêm một app vào danh sách cấm."** Không cần sửa code Java, chỉ sửa `config/rules.json` ở máy server rồi chạy lại server (luật đọc lúc `ExamServer.main` khởi động, client nhận qua `LOGIN_OK`).

```json
"blacklistApps": ["chrome.exe", "msedge.exe", "firefox.exe", "wechat.exe"]
```

**b) "Thêm cột 'Thời gian rời cửa sổ' vào bảng giám thị."** Dữ liệu có sẵn trong `FEATURES` (giá trị thứ 3 là số giây rời cửa sổ của cửa sổ đó); cộng dồn ở server rồi hiện lên bảng.

```java
// Session.java
int focusLostSeconds;
// ExamRoom.features(Session s, JsonObject sample): cộng dồn giá trị thứ 3
s.focusLostSeconds += sample.getAsJsonArray("values").get(2).getAsInt();
// ExamRoom.Row: thêm trường int focusLostSeconds ở cuối; trong rows() truyền s.focusLostSeconds
// TeacherWindow: thêm "Rời cửa sổ (giây)" vào COLUMNS và trong getValueAt:
case 7 -> row.focusLostSeconds();   // nhớ đổi default hiện tại (điểm) thành case 6
```

**c) "Đổi chu kỳ quét từ 3 giây thành 5 giây."** Sửa `Monitor.start`:

```java
// trước: scheduler.scheduleWithFixedDelay(() -> safely(this::scan), 3, 3, TimeUnit.SECONDS);
scheduler.scheduleWithFixedDelay(() -> safely(this::scan), 5, 5, TimeUnit.SECONDS);
```
