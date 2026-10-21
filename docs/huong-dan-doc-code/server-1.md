# Server 1: lõi mạng và phòng thi

Phần chạy trên máy giáo viên nhưng không có giao diện: nhận kết nối, giữ trạng thái phòng thi, ghi hồ sơ kỳ thi. Giao diện giám thị và file dữ liệu là của Server 2; `ExamRoom` chỉ đưa dữ liệu ra qua `rows()`, `summary()`, `history()`.

> Mỗi `File.hàm` trong tài liệu này đều có thật trong code.

## 1. Bảng file

Tất cả trong `src/main/java/exam/server/`, trừ `Protocol`.

| File | Một câu |
|---|---|
| `ExamServer` | Mở cổng TCP/UDP, mỗi kết nối tạo một luồng `ClientHandler`, chạy watchdog và lịch chấm ML, ghi log, `applyRules`. |
| `ClientHandler` | Một luồng cho một máy: đọc từng dòng JSON, `switch` theo `type`, mỗi loại gọi một hàm `handleXxx` rồi chuyển cho `ExamRoom`. |
| `ExamRoom` | Luật phòng thi: đăng nhập, gửi đề, nộp bài, mất kết nối, bắt đầu/kết thúc/kỳ thi mới; hàm đổi trạng thái đều `synchronized`. |
| `Session` | Dữ liệu của một sinh viên (socket, cờ PRECHECK/nộp, vi phạm, mẫu `FEATURES`, điểm, app đang mở) và các chuỗi trạng thái; không có luật. |
| `ExamLog` | Giữ sự kiện và dòng log, tạo thư mục `ket-qua/<giờ>_<tên đề>/` và ghi ngay `events.csv`, `server.log`. |
| `common/Protocol` | Hằng số và hàm dùng chung cho tin nhắn JSON một dòng; `featureInterval` đọc chu kỳ `FEATURES`. |

## 2. Luồng chạy

**1. Kết nối → đăng nhập → PRECHECK → nhận đề → nộp bài**

- `ExamServer.acceptLoop` → `ClientHandler.run` → `ClientHandler.handleLine` → `ClientHandler.handleMessage`
- `LOGIN`: `handleMessage` → `ClientHandler.handleLogin` (đọc `studentId`, `password`) → `ExamRoom.login` (có danh sách thì `StudentList.find` + so mật khẩu, sai thì `ExamRoom.loginFailed` gửi `LOGIN_FAIL` chung một lý do và ghi sự kiện; rồi `ExamRoom.loginError` kiểm tra đăng nhập trùng) → `Protocol.send(LOGIN_OK)` kèm `name` → `ExamRoom.trySendExam` (chưa PRECHECK nên chưa gửi đề)
- `PRECHECK_RESULT`: `ClientHandler.handlePrecheckResult` → `ExamRoom.precheckResult` → `ExamRoom.trySendExam` (gửi `EXAM_START` nếu giám thị đã bấm Bắt đầu)
- Giám thị bấm Bắt đầu: `TeacherWindow` → `ExamRoom.startExam` → `ExamLog.attach` → `ExamRoom.trySendExam` cho từng máy đã PRECHECK đạt
- `VIOLATION`: `ClientHandler.handleViolation` → `ExamRoom.violation` → `ExamLog.record(VIOLATION)`
- `FEATURES` (mỗi `featureIntervalSeconds` giây, cũng là heartbeat): `ClientHandler.handleFeatures` → `ExamRoom.features`
- `APPS`: `ClientHandler.handleApps` (bỏ phần tử sai, tối đa 100 dòng) → `ExamRoom.apps` lưu vào `Session.apps` kèm giờ; giám thị đọc bằng `ExamRoom.appsOf`
- `SUBMIT`: `ClientHandler.handleSubmit` → `ExamRoom.submit` → `ExamFile.grade` → `ExamRoom.writeResults` → `ExamFile.writeResults` → `ExamRoom.sendResult`

**1b. Đổi luật giữa giờ thi:** `RulesDialog.save` → `ExamServer.applyRules` → `RulesFile.save` → `ExamRoom.updateRules` → `Protocol.send(RULES_UPDATE)` cho mọi `Session.connected()`; máy vào sau nhận luật mới trong `LOGIN_OK`.

**2. Máy rớt mạng rồi vào lại**

- Rớt hẳn: `ClientHandler.run` (khối `finally`) → `ExamRoom.disconnected` → `ExamRoom.markDisconnected` → `ExamLog.record(DISCONNECT)`
- Rớt "chết lặng": `ExamServer.checkIdle` (mỗi 10 giây) → `ExamRoom.dropIdle` → `Session.close` → `ExamRoom.markDisconnected`
- Vào lại: `ClientHandler.handleLogin` → `ExamRoom.login` (session cũ không còn `connected()` nên được nhận, gắn socket mới, ghi `RECONNECT`) → `ExamRoom.trySendExam` (gửi lại đề với thời gian còn lại) hoặc `ExamRoom.sendResult` nếu đã nộp

**3. Giám thị bấm Kết thúc, rồi bấm Kỳ thi mới**

- Kết thúc: `TeacherWindow` → `ExamRoom.endExam` → `ExamRoom.sendEnd` (máy chưa nộp) → `ExamLog.record(EXAM_END)` → `ExamRoom.writeResults`. Hết giờ thì `scheduler` gọi cùng hàm này.
- Kỳ thi mới: `TeacherWindow` → `ExamRoom.newSession` → `ExamRoom.sendEnd` → `ExamRoom.closeAll` → `sessions.clear()`, `epoch++` → `ExamLog.reset`. Đề đang nạp được giữ; phòng trống nên mã SV cũ đăng nhập lại là người mới (client phải tắt app mở lại); luồng cũ kết thúc muộn bị bỏ qua nhờ `ExamRoom.live`; điểm ML trả về muộn bị bỏ nhờ `epoch` trong `ExamRoom.applyScores`.

## 3. Thầy bảo sửa gì → mở đâu, đổi gì

| Thầy bảo | File → hàm | Đổi gì |
|---|---|---|
| Đổi timeout watchdog | `ExamServer` → trường `idleTimeoutMs` | Đổi `90_000`. Phải lớn hơn chu kỳ `featureIntervalSeconds` (tối đa 60 giây). Chu kỳ quét 10 giây nằm ở `ExamServer.start`. |
| Đổi cổng | `ExamServer.openTeacherWindow` | Đổi tham số `server.start(...)` (TCP). Máy sinh viên tự biết cổng TCP mới vì UDP trả cổng trong `EXAM_SERVER <cổng>`. Cổng UDP nằm ở `Protocol.UDP_PORT`, client phải khớp. |
| Thêm loại tin nhắn mới (vd `HELP`) | 1) `Protocol` thêm hằng. 2) `ClientHandler.handleMessage` thêm `case`. 3) `ClientHandler` thêm `handleHelp`. 4) `ExamRoom` thêm hàm `synchronized` (đầu hàm `if (!live(s)) return;`, rồi `log.accept`, `event`). 5) `Session` thêm trường nếu cần lưu. 6) Muốn hiện trên bảng giám thị: `ExamRoom.Row` + `rows()` (Server 2 lo giao diện). 7) Client gửi/nhận tin đó. 8) Viết test. | Quên bước 2 thì server chỉ log "Bỏ qua type không biết". |
| Cho nộp lại bài | `ExamRoom.submit` | Bỏ `if (s.result == null)` để lần nào cũng `ExamFile.grade` lại và `writeResults`. Client đang tự đóng kết nối sau `RESULT`, phải sửa phía client nữa. |
| Khoá vào thi muộn sau N phút | `ExamRoom.loginError` | Thêm: `if (existing == null && started && System.currentTimeMillis() - startMillis > N * 60_000) return "Đã quá N phút";`. Máy đã có session vẫn vào lại được. |
| Giới hạn số máy | `ExamRoom.loginError` | Thêm: `if (existing == null && sessions.size() >= MAX) return "Phòng đã đủ máy";`. |
| Thêm cột vào `results.csv` | `Session` + `ExamRoom` (Server 1), `ExamFile.RESULTS_HEADER` + `resultsRow` (Server 2) | Thêm trường ở `Session`, gán ở `ExamRoom` (vd `submit`); Server 2 thêm tên cột và giá trị cùng thứ tự. |
| Thêm loại sự kiện vào `events.csv` | `ExamRoom` (chỗ xảy ra sự kiện) | Gọi `event(s, "LOAI_MOI", "chi tiết")`; `ExamLog.record` tự ghi file. Muốn hiện trong nhật ký bên giám thị thì Server 2 thêm tên vào `Roster.NOTABLE` và `Roster.eventText`. |
| Gửi thông báo tới mọi máy | `ExamRoom` (hàm mới `broadcast`) | `synchronized`, `Protocol.msg("NOTICE")` rồi vòng `for` qua `sessions.values()`, máy `connected()` thì `Protocol.send(s.out, m)`. Thêm nút ở `TeacherWindow` (Server 2); client xử lý `NOTICE` như một loại tin mới. |
| Tự kết thúc khi mọi máy đã nộp | `ExamRoom.submit` | Cuối nhánh chấm bài, nếu không còn session nào `!s.submitted` thì `endExam("Mọi máy đã nộp")`. |
| Kick một máy | `ExamRoom` (hàm mới `kick`) | `synchronized`; lấy `Session s = sessions.get(id)`; `s.close(); markDisconnected(s, "giám thị đá");`. Client sẽ tự vào lại, muốn chặn thì thêm `Set<String> kicked` và kiểm tra trong `loginError`. |
| Ghi IP máy vào kết quả | `Session` + `ExamRoom.login` (+ `ExamFile` của Server 2) | `Session` thêm `String ip`; trong `login` gán `s.ip = socket.getInetAddress().getHostAddress()` (kiểm tra `socket != null`, test truyền `null`); thêm cột như dòng "Thêm cột". |
| Đổi tin báo sai mật khẩu | `ExamRoom.login` | Chuỗi `Sai mã sinh viên hoặc mật khẩu` (cố ý không nói rõ sai cái nào); test `loginWithWrongPasswordFails` kiểm tra đúng chuỗi này. |
| Đổi chu kỳ FEATURES và chấm ML | `config/rules.json` | Đổi `featureIntervalSeconds` (10 đến 60, sai thì 30). Server gửi trong `LOGIN_OK`, `ExamServer.start` chấm ML cùng chu kỳ. |

## 4. Câu hỏi thường gặp

**1. Vì sao mỗi client một thread?** `readLine()` chặn cho tới khi có dữ liệu, nên nếu một luồng phục vụ nhiều máy thì máy im lặng sẽ làm các máy khác phải chờ. Phòng thi chỉ vài chục máy nên vài chục luồng là rẻ, và code chỉ là một vòng `while` đọc dòng, dễ hiểu hơn NIO/Selector.

**2. Vì sao dùng `synchronized`?** Nhiều luồng cùng đụng vào `sessions` và các cờ `started`/`ended`: luồng của từng máy, luồng giao diện (`rows()`), luồng lập lịch (hết giờ, watchdog, ML). Mọi hàm đổi trạng thái của `ExamRoom` khoá cùng một monitor nên mỗi lúc chỉ một luồng sửa, không lệch (vd hai máy cùng đăng nhập một mã SV). Chỉ một khoá chung nên không lo deadlock; `ExamLog` có khoá riêng và luôn được gọi sau khoá của phòng.

**3. Vì sao gọi ML ngoài khối `synchronized`?** Gọi HTTP có thể mất vài giây hoặc treo; giữ khoá phòng lúc đó thì đăng nhập, nộp bài, bảng giám thị đều đứng. Nên `ExamServer.scoreRoomNow` lấy bản sao mẫu và `epoch` trong khoá, gọi ML ngoài khoá, rồi mới khoá lại để `applyScores`; nếu giữa chừng đã sang kỳ mới thì `epoch` khác và điểm bị bỏ.

**4. Watchdog hoạt động thế nào?** `ExamServer.start` lập lịch `checkIdle` mỗi 10 giây, gọi `ExamRoom.dropIdle(idleTimeoutMs)`. Máy đang "Đang thi" mà `now - lastSeen` quá 90 giây thì bị `Session.close()` và đánh dấu "Mất kết nối". `lastSeen` cập nhật khi nhận bất kỳ tin nào (kể cả `FEATURES`) và đúng lúc gửi `EXAM_START`, vì trước giờ thi client chưa gửi gì và không được tính là im lặng. Cần watchdog vì rút mạng đột ngột thì TCP không báo lỗi, `readLine` treo mãi.

**5. Vì sao TCP cho nghiệp vụ còn UDP chỉ để tìm server?** Đăng nhập, đề, nộp bài, kết quả không được mất hay đảo thứ tự, và cần biết khi nào mất kết nối, nên dùng TCP. Máy sinh viên chưa biết IP server nên broadcast `EXAM_DISCOVER` bằng UDP (TCP không broadcast được); mất gói thì hỏi lại cũng chẳng sao. Server trả `EXAM_SERVER <cổng TCP>` đúng người hỏi, rồi client mở TCP.

**6. Chống nhầm session cũ và mới thế nào?** Luồng đọc của kết nối cũ kết thúc chậm không được đánh dấu mất kết nối của máy đã vào lại bằng socket mới, nên `ExamRoom.disconnected` so sánh socket trước khi đánh dấu; tin của session thuộc kỳ trước bị bỏ nhờ `ExamRoom.live`.
