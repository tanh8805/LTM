# Người 3: server và phòng thi

Mảng của bạn: phần chạy trên máy giáo viên, nhận kết nối từ các máy sinh viên, giữ trạng thái phòng thi, chấm điểm và lưu kết quả.

> Các đoạn code trong tài liệu này **chỉ để minh hoạ**, không áp dụng vào code thật.

## 1. Bạn phụ trách gì

| File | Làm gì |
|---|---|
| `server/ExamServer.java` | Mạng và các luồng: `main`, `ServerSocket` + vòng `accept`, luồng UDP trả lời tìm server, watchdog, lịch gọi ML mỗi 30 giây. |
| `server/ExamRoom.java` | Luật của phòng thi: giữ các `Session`, đề đang dùng, bắt đầu/kết thúc, đăng nhập, nộp bài. Mọi phương thức đổi trạng thái đều `synchronized`. |
| `server/Session.java` | Chỉ là dữ liệu của một sinh viên (socket, cờ PRECHECK/nộp bài, vi phạm, mẫu `FEATURES`, điểm...) và các chuỗi trạng thái. |
| `server/ExamFile.java` | Mọi thứ liên quan đến file: nạp và kiểm tra đề, bỏ đáp án khi gửi cho sinh viên, chấm điểm, ghi `results.csv`. |
| `server/ExamLog.java` | Hồ sơ của kỳ thi: giữ sự kiện và dòng log trong bộ nhớ, tạo thư mục `ket-qua/<thời gian>_<tên đề>/` khi bắt đầu thi và ghi ngay `events.csv`, `server.log`. |
| `server/ClientHandler.java` | Một luồng cho mỗi kết nối: đọc từng dòng JSON, kiểm tra trường rồi gọi `ExamRoom`. |

## 2. Thứ tự đọc

1. `ExamServer.start` và `acceptLoop`: mở cổng 5000, mỗi kết nối mới tạo một `ClientHandler` chạy trên luồng riêng.
2. `ClientHandler.run` và `handle`: vòng đọc dòng, parse JSON, `switch` theo `type`; dòng hỏng hay `type` lạ chỉ bị bỏ qua.
3. `ExamRoom.login`: đăng nhập, từ chối trùng, vào lại gắn socket mới vào session cũ.
4. `ExamRoom.trySendExam`: **điểm gửi đề duy nhất**, được gọi khi bấm Bắt đầu, khi PRECHECK đạt và khi đăng nhập.
5. `ExamRoom.submit` cùng `ExamFile.grade`: nộp bài, chấm điểm, ghi lại `results.csv`.
6. `ExamRoom.disconnected`, `ExamRoom.dropIdle` (gọi từ `ExamServer.checkIdle`): mất kết nối và watchdog.
7. `ExamFile.load` và `ExamFile.writeResults`: kiểm tra đề và xuất CSV.
8. `ExamLog` và các chỗ `ExamRoom.event(...)`: mỗi sự kiện (`LOGIN`, `VIOLATION`, `DISCONNECT`...) được ghi vào bộ nhớ và, khi đã có thư mục, vào `events.csv`.
9. `ExamRoom.newSession`: làm mới phòng sau khi kết thúc để thi kỳ mới mà không tắt server.

## 3. Một luồng chạy thật: rớt mạng giữa giờ rồi vào lại

1. Máy sinh viên đang thi (`Đang thi`) bị mất Wi-Fi. Luồng `ClientHandler.run` của máy đó nhận lỗi đọc (hoặc `readLine` trả `null`), thoát vòng lặp và vào khối `finally`: `room.disconnected(session, socket)`.
2. `ExamRoom.disconnected` chỉ đánh dấu khi `s.socket == socket` (socket đang gắn vào session chính là socket của luồng vừa kết thúc), rồi `markDisconnected` xoá `socket` và `out`. Bảng giám thị hiện `Mất kết nối`; mọi dữ liệu khác (PRECHECK, vi phạm, mẫu, kết quả) vẫn còn trong `Session`.
3. Phía client, `ServerConnection.readLoop` kết thúc nên gọi `onDisconnected` rồi `reconnect`: cứ 5 giây thử `connect()`. Bật lại Wi-Fi thì kết nối được và `onReconnected` gửi lại `LOGIN` cùng mã SV.
4. Trên server có một `ClientHandler` mới nhận `LOGIN`, gọi `ExamRoom.login`. Session cũ có `connected() == false` nên **không** bị từ chối; socket và `PrintWriter` mới được gắn vào session cũ, rồi gửi `LOGIN_OK`.
5. Cuối `login` gọi `trySendExam(s)`: bài đã bắt đầu, session đang kết nối, đã PRECHECK đạt, chưa nộp, nên gửi `EXAM_START` với `remainingSeconds = max(0, durationMinutes*60 − số giây đã trôi qua)`.
6. Client nhận `EXAM_START` khi đã có đề nên chỉ cập nhật thời gian còn lại, giữ nguyên đáp án và câu đang xem. Trạng thái trên bảng giám thị trở lại `Đang thi`.

Trường hợp khác: kết nối "chết lặng" (không có tin nào). Nếu máy `Đang thi` quá 90 giây không gửi gì (kể cả heartbeat `FEATURES` 30 giây), `ExamServer.checkIdle` (chạy mỗi 10 giây) gọi `ExamRoom.dropIdle` để đóng socket và đánh dấu `Mất kết nối`. Mốc tính là `Session.lastSeen`, được đặt lại mỗi khi nhận tin **và đúng lúc `trySendExam` gửi `EXAM_START`**: client chỉ gửi `FEATURES` sau khi nhận đề, nên nếu không đặt lại thì máy chờ trước giờ thi quá 90 giây sẽ bị đá ngay khi giáo viên bấm Bắt đầu.

Mỗi bước trên đều để lại một dòng trong `events.csv` của kỳ thi: `DISCONNECT` (chi tiết ghi lý do: socket đóng hay watchdog), rồi `RECONNECT`, rồi `EXAM_SENT`. Giám thị nhấp đúp vào dòng của máy đó để xem lịch sử.

**Kết thúc rồi thi kỳ mới:** `endExam` ghi `results.csv` và sự kiện `EXAM_END`. Giáo viên bấm **Kỳ thi mới** thì `ExamRoom.newSession` gửi `EXAM_END` cho máy chưa nộp, đóng socket, xoá `sessions`, tạo `MlScores` mới, tăng `epoch` (để bỏ điểm ML trả về muộn của kỳ cũ) và `ExamLog.reset()`; đề đang nạp được giữ. Luồng đọc của socket cũ kết thúc sau đó bị bỏ qua vì `live(session)` sai.

## 4. Kiến thức cần giải thích được với thầy

- **`ServerSocket` và một luồng cho mỗi client:** `accept()` chặn đến khi có kết nối, trả về một `Socket`; vì đọc `readLine` cũng chặn nên mỗi client cần một luồng riêng để các máy không chờ nhau.
- **Vì sao các hàm của `ExamRoom` phải `synchronized`:** nhiều luồng client cùng đọc/ghi `Map<String, Session>` và cờ bài thi, cộng với luồng giao diện đọc `rows()` và luồng lập lịch gọi `endExam`. Không khoá thì dữ liệu hỏng hoặc đọc lệch. `ExamRoom` là nơi duy nhất giữ trạng thái chung nên chỉ cần một monitor.
- **Gọi HTTP ngoài khối `synchronized`:** `ExamServer.scoreRoomNow` lấy mẫu trong khối khoá, gọi service ML ngoài khối, rồi mới khoá lại để áp điểm. Nếu giữ khoá trong lúc chờ HTTP thì ML chậm sẽ làm cả phòng thi đứng.
- **Heartbeat và watchdog:** `FEATURES` mỗi 30 giây vừa là dữ liệu vừa là tín hiệu "máy còn sống"; quá 90 giây (3 chu kỳ) không có tin nào thì coi là mất kết nối.
- **Vì sao watchdog chỉ tính sau khi gửi đề?** Trước giờ thi client chưa chạy `Monitor` nên không gửi `FEATURES`; máy PRECHECK xong rồi chờ giáo viên vài phút vẫn là máy khoẻ. Nếu tính 90 giây từ tin cuối (PRECHECK) thì ngay khi bấm Bắt đầu, máy nào chờ quá 90 giây bị đá hàng loạt (đã gặp: 16 máy giả cùng "Mất kết nối" và không có mẫu ML). Vì vậy `trySendExam` đặt lại `lastSeen` lúc gửi `EXAM_START`; máy nhận đề rồi im lặng thật sự vẫn bị đá như cũ.
- **Ghi sự kiện để không mất dữ liệu:** `events.csv` ghi và flush từng dòng, nên server sập giữa chừng vẫn còn lịch sử; sự kiện xảy ra trước khi có thư mục (đăng nhập, PRECHECK) nằm trong bộ nhớ và được ghi nốt lúc `ExamLog.attach`.
- **Luật gửi đề duy nhất (`trySendExam`):** vào thi muộn, vào lại sau rớt mạng và bấm Bắt đầu đều dùng chung một đoạn code với bốn điều kiện: đã bắt đầu, đang kết nối, PRECHECK đạt, chưa nộp.
- **Chống đá nhầm session:** luồng đọc cũ kết thúc chậm không được đánh dấu mất kết nối của máy đã vào lại bằng socket mới, nên so sánh socket trước khi đánh dấu.
- **Đáp án chỉ nằm ở server:** `ExamFile.questionsForClient` bỏ trường `answer` trước khi gửi; việc chấm điểm làm ở `ExamFile.grade`.

## 5. 4 câu thầy có thể hỏi và cách sửa

**a) "Cho phép nộp lại bài."** Sửa `ExamRoom.submit`: bỏ điều kiện chỉ chấm một lần, để lần nộp nào cũng chấm lại và ghi lại CSV.

```java
// ExamRoom.submit(): thay khối "if (s.result == null) { ... }" bằng
s.result = ExamFile.grade(exam, answers);
s.submitted = true;
log.accept("Nộp bài: " + s.id + " - điểm " + s.result.get("score").getAsString());
writeResults();
// Lưu ý: client hiện gọi conn.close() sau RESULT; muốn nộp lại ngay thì phải bỏ dòng đó ở StudentApp.onResult
```

**b) "Không cho vào thi sau khi đã bắt đầu 10 phút."** Sửa điều kiện từ chối trong `ExamRoom.login` (chỉ áp dụng với sinh viên mới, người đã có session vẫn được vào lại).

```java
String fail = id.isEmpty() || name.isEmpty() ? "Thiếu mã SV hoặc họ tên"
    : s != null && s.connected() ? "Mã SV này đang đăng nhập ở máy khác"
    : s == null && started && System.currentTimeMillis() - startMillis > 10 * 60_000
        ? "Đã quá 10 phút kể từ khi bắt đầu thi" : null;
```

**c) "Thêm thời gian nộp vào `results.csv`."** Thêm một trường vào `Session`, ghi lúc nộp, rồi thêm một cột ở `ExamFile.writeResults`.

```java
// Session.java
String submittedAt = "";
// ExamRoom.submit(), trong khối "if (s.result == null)":
s.submittedAt = java.time.LocalTime.now().withNano(0).toString();
// ExamFile.writeResults: thêm ",submittedAt" vào cuối dòng tiêu đề, và sau cột status của mỗi dòng:
//     .append(',').append(s.submittedAt)
```

**d) "Vì sao watchdog chỉ tính sau khi gửi đề?"** Vì trước giờ thi client không gửi gì (chỉ gửi `FEATURES` sau khi nhận `EXAM_START`), nên một máy khoẻ đã chờ quá 90 giây sẽ bị coi là "chết lặng" ngay lúc bấm Bắt đầu. Cách sửa nằm ở `ExamRoom.trySendExam`: đặt `s.lastSeen = System.currentTimeMillis()` ngay sau khi gửi `EXAM_START`, vì đó là điểm gửi đề duy nhất (bấm Bắt đầu, vào thi muộn, vào lại sau rớt mạng đều đi qua đây). Test `longWaitBeforeStartIsNotDropped` chứng minh máy chờ lâu không bị đá, `silentAfterReceivingExamIsStillDropped` chứng minh máy nhận đề rồi im lặng vẫn bị đá.
