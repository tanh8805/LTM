# Người 1: client sinh viên

Mảng của bạn: phần chạy trên máy sinh viên, từ lúc mở app đến lúc xem điểm. Phần giám sát (`Monitor`) là của Người 2, bạn chỉ gọi nó.

> Các đoạn code trong tài liệu này **chỉ để minh hoạ**, không áp dụng vào code thật.

## 1. Bạn phụ trách gì

| File | Làm gì |
|---|---|
| `client/StudentApp.java` | Logic: kết nối, xử lý từng loại tin từ server, chuyển màn hình, lưu đáp án, đồng hồ, nộp bài, gọi `Monitor`. |
| `client/StudentScreens.java` | Ghép giao diện: `CardLayout` 5 màn hình, thanh trên (`TopBar`), thanh cam "Mất kết nối" và thanh trạng thái; hộp xác nhận nộp bài và thoát khi chưa nộp (qua `Sheet`); nút bấm gọi ngược về `StudentApp`. |
| `client/Theme.java` | Font, `Theme.install()` cài FlatLaf, vài hàm dựng nút/nhãn. Màu và biểu tượng tự vẽ dùng chung với giám thị nằm ở `common/Palette.java` và `common/Icons.java`; điểm hiển thị kiểu Việt Nam (dấu phẩy) ở `common/Format.java`. |
| `client/TopBar.java` | Tên kỳ thi bên trái; ngoài giờ thi bên phải là mã SV - họ tên; trong giờ thi có đồng hồ ở giữa (đổi cam dưới 5 phút, đỏ dưới 1 phút), chấm kết nối và nút Nộp bài. |
| `client/LoginPanel.java`, `PrecheckPanel.java`, `ResultPanel.java` | Màn đăng nhập; PRECHECK (danh sách việc cần làm, nút xử lý, dòng đã đạt, tự kiểm tra lại sau 5 giây) + màn chờ; kết quả (giờ nộp, Điểm, Số câu đúng, lời cảm ơn, Thoát ứng dụng). Không có nút thi lại: thi kỳ mới thì tắt app rồi mở lại; `StudentApp.onResult` chuyển sang màn kết quả ở bất kỳ màn nào khi nhận `RESULT`. |
| `client/Sheet.java` | Hộp xác nhận kiểu "tấm" (tiêu đề, nội dung, hai nút) dùng cho Nộp bài ngay? và Thoát khi bài chưa nộp?. |
| `client/ExamPanel.java`, `NumberButton.java`, `OptionRow.java` | Màn làm bài: lưới số câu, hàng lựa chọn bấm được, phím tắt. |
| `client/ServerConnection.java` | Kết nối TCP, luồng đọc từng dòng JSON, tự nối lại mỗi 5 giây khi rớt mạng. |
| `client/ServerFinder.java` | Tìm server trong LAN bằng UDP broadcast. |

## 2. Thứ tự đọc

1. `StudentApp.show`: dựng `StudentScreens`, truyền các callback (đăng nhập, câu trước/sau, nộp bài), gắn listener focus.
2. `StudentApp.login` rồi `StudentApp.connect`: đọc ô nhập, tìm server (UDP hoặc IP gõ tay) trên luồng riêng để không treo giao diện.
3. `ServerFinder.find`: gửi `EXAM_DISCOVER` tới nhiều địa chỉ rồi chờ gói trả lời hợp lệ đầu tiên.
4. `ServerConnection.connect`, `readLoop`, `reconnect`: luồng đọc và vòng nối lại.
5. `StudentApp.onMessage`: một `switch` theo `type`, mỗi nhánh gọi một phương thức ngắn (`onLoginOk`, `onExamStart`, `onResult`...).
6. `StudentApp.onExamStart`, `tick`, `submit`: nhận đề, đếm ngược, nộp bài.
7. `StudentScreens.showQuestion` → `ExamPanel.showQuestion`: dựng một câu hỏi và các `OptionRow`; `StudentApp.showAnswered` báo câu nào đã làm để tô lưới số câu.
8. `Theme` rồi từng panel (`LoginPanel`, `PrecheckPanel`, `ExamPanel`): chỉ vẽ, không có logic mạng.

## 3. Một luồng chạy thật: từ "Đăng nhập" đến khi đề hiện

1. Sinh viên bấm **Đăng nhập**: `LoginPanel` gọi `onLogin` = `StudentApp.login`. Hàm này gọi `screens.setLoginBusy` (khoá nút, hiện thanh tiến trình "Đang tìm server..."), rồi chạy `connect(host)` trên một luồng mới.
2. `StudentApp.connect`: ô IP trống nên gọi `ServerFinder.find(9999, 3000)`. Hàm này gửi `EXAM_DISCOVER` tới `255.255.255.255`, broadcast từng card mạng và `127.0.0.1`, rồi `waitForReply` nhận `EXAM_SERVER 5000` và trả `InetSocketAddress`.
3. Tạo `ServerConnection`, gọi `conn.connect()` (timeout 5 giây, bật luồng `readLoop`), rồi `sendLogin` gửi `LOGIN` (một dòng JSON).
4. Server trả `LOGIN_OK` kèm `rules`. `readLoop` parse dòng JSON và gọi `onMessage` qua `invokeLater` → `onLoginOk` → `runPrecheck`.
5. `runPrecheck` chạy `Monitor.precheck` trên luồng riêng. Không có vấn đề nào thì xếp việc "hiện màn Chờ" vào hàng đợi giao diện rồi gửi `PRECHECK_RESULT`. Có vấn đề thì hiện màn PRECHECK với nút "Kiểm tra lại".
6. Giáo viên bấm Bắt đầu, server gửi `EXAM_START`. `onMessage` → `onExamStart` tính `deadlineMillis`, lưu `questions`, gọi `monitor.start(conn::send)`, bật `countdown`, rồi `showCurrentQuestion` → `StudentScreens.showQuestion` và `showExam`. Đề hiện ra.

## 4. Kiến thức cần giải thích được với thầy

- **Socket TCP phía client:** `Socket.connect(address, timeout)` rồi đọc/ghi qua `BufferedReader`/`PrintWriter` UTF-8; mỗi tin nhắn là một dòng JSON kết thúc bằng `\n`, nên `readLine` đọc đúng một tin.
- **Vì sao cần luồng đọc riêng:** `readLine` chặn cho tới khi có dữ liệu. Nếu đọc trên luồng giao diện thì cửa sổ đứng; nên luồng đọc nhận tin rồi chuyển sang giao diện bằng `invokeLater`.
- **UDP broadcast:** client chưa biết IP server nên gửi gói tới địa chỉ quảng bá; server trả lời thẳng về địa chỉ người gửi. Gửi tới broadcast của **từng card mạng** vì máy có ZeroTier/VPN có nhiều card, chỉ gửi `255.255.255.255` thì Java có thể đẩy gói ra nhầm card.
- **Vì sao Swing phải cập nhật qua `invokeLater`:** các thành phần Swing không an toàn đa luồng, chỉ luồng giao diện (EDT) được sửa chúng. Mọi tin từ mạng đều đi qua `SwingUtilities.invokeLater` trước khi đụng tới giao diện.
- **Tự nối lại:** khi luồng đọc kết thúc mà chưa `close()`, `ServerConnection` báo mất kết nối, 5 giây thử `connect()` một lần, thành công thì `onReconnected` gửi lại `LOGIN`; server nhận ra mã SV cũ và cho vào lại. Đáp án nằm trong bộ nhớ nên vẫn còn.
- **Đếm "rời cửa sổ":** chỉ tính khi `getOppositeWindow() == null` (focus sang app khác). Hộp xác nhận nộp bài được tạo với cửa sổ chính làm cha (`Sheet.ask(frame, ...)`, một `JDialog` có cửa sổ chính làm cha) nên không bị tính là gian lận. Đóng cửa sổ khi đang thi mà chưa nộp thì `StudentApp.requestExit` hỏi "Thoát khi bài chưa nộp?": Nộp bài và thoát (thoát khi nhận `RESULT`, tối đa 8 giây) hoặc Tiếp tục làm bài.
- **FlatLaf và `Theme`:** FlatLaf là Look and Feel của Swing; `FlatLightLaf.setup()` phải chạy trước khi tạo cửa sổ. Màu, font, biểu tượng gom về `Theme` để đổi giao diện chỉ sửa một chỗ; các nút số câu, hàng lựa chọn tự vẽ bằng `paintComponent`.
- **Tách giao diện khỏi logic:** `StudentScreens` và các panel không biết gì về mạng; `StudentApp` gọi `screens.setConnection(...)`, `setAnswered(...)` và nhận sự kiện qua callback (`Runnable`, `IntConsumer`). Nhờ vậy xem giao diện được bằng một harness nhỏ mà không cần server.
- **Mất kết nối không bật hộp thoại:** thanh cam trên cùng + chấm trạng thái; người dùng vẫn xem câu và chọn đáp án (đáp án nằm trong bộ nhớ).

## 5. 3 câu thầy có thể hỏi và cách sửa

**a) "Hiện số câu chưa làm."** Sửa `StudentApp`: tính lại mỗi khi chọn đáp án hoặc đổi câu.

```java
// StudentApp.java
private void showUnanswered() {
    int left = questions.size() - answers.size();
    screens.setStatus(left == 0 ? "Đã làm hết các câu" : "Còn " + left + " câu chưa làm");
}
// gọi ở cuối onAnswer(...) và showCurrentQuestion()
```

**b) "Cảnh báo khi còn 1 phút."** Đồng hồ đã tự chuyển cam (dưới 5 phút) và đỏ (dưới 1 phút) nhờ `Theme.timerColor`; muốn thêm dòng chữ cảnh báo thì sửa `StudentApp.tick`, còn đổi ngưỡng màu thì sửa `Theme.timerColor`.

```java
// StudentApp.tick(): sau khi tính left
if (left == 60) { screens.setStatus("Chỉ còn 1 phút!"); }

// Theme.timerColor: đổi ngưỡng đỏ từ 60 giây thành 120 giây
return secondsLeft < 120 ? ERROR : secondsLeft < 300 ? WARNING : SUCCESS;
```

**c) "Cho đánh dấu câu để xem lại."** Cần một nút mới trong `ExamPanel` (phần nút điều hướng ở dưới) và một tập id câu trong `StudentApp`.

```java
// StudentApp.java
private final Set<Integer> flagged = new HashSet<>();
private void toggleFlag() {
    int id = questions.get(current).getAsJsonObject().get("id").getAsInt();
    if (!flagged.remove(id)) { flagged.add(id); }
    showCurrentQuestion();     // trong đó thêm "★ " trước nội dung nếu flagged.contains(id)
}
// ExamPanel.main(...): JButton flag = new JButton("Đánh dấu"); flag.addActionListener(e -> onFlag.run());
// rồi thêm tham số Runnable onFlag vào constructor của ExamPanel và StudentScreens, truyền this::toggleFlag từ StudentApp.show
```
