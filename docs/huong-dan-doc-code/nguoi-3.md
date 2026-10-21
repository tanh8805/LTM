# Người 3: server và phòng thi

Tất cả nằm trong `src/main/java/exam/server/`. Mỗi `File.hàm` dưới đây đều có thật trong code.

## (a) Các file

| File | Một câu |
|---|---|
| `ExamServer` | Mở cổng TCP/UDP, mỗi kết nối tạo một luồng `ClientHandler`, chạy watchdog và lịch chấm ML, ghi log. |
| `ClientHandler` | Một luồng cho một máy: đọc từng dòng JSON, `switch` theo `type`, mỗi loại gọi một hàm `handleXxx` rồi chuyển cho `ExamRoom`. |
| `ExamRoom` | Luật của phòng thi: đăng nhập, gửi đề, nộp bài, mất kết nối, bắt đầu/kết thúc/kỳ thi mới; hàm đổi trạng thái đều `synchronized`. |
| `Session` | Dữ liệu của một sinh viên (socket, cờ PRECHECK/nộp, vi phạm, mẫu `FEATURES`, điểm) và các chuỗi trạng thái; không có luật. |
| `ExamFile` | Nạp và kiểm tra file đề, bỏ đáp án khi gửi cho sinh viên, chấm điểm, ghi `results.csv`. |
| `StudentList` | Danh sách tài khoản sinh viên (CSV `studentId,name,password`): `parse` kiểm tra từng dòng và báo `Dòng n: lý do`, `find` so khớp mã SV không phân biệt hoa thường, `save` ghi `config/students.csv`. |
| `ExamBank` | Soạn đề: `build` dựng JSON (đánh số lại `id`), `save` ghi file tạm rồi kiểm tra bằng `ExamFile.load`, `parseCsv` nhập câu hỏi từ CSV. Giao diện ở `ExamBankDialog` và `QuestionDialog`. |
| `RulesFile` | Sửa các danh sách trong luật và ghi lại `rules.json` (giữ các khoá khác); `ExamServer.applyRules` ghi file rồi gọi `ExamRoom.updateRules`. |
| `ExamLog` | Giữ sự kiện và dòng log, tạo thư mục `ket-qua/<giờ>_<tên đề>/` và ghi ngay `events.csv`, `server.log`. |

## (b) Ba luồng chạy

**1. Kết nối → đăng nhập → PRECHECK → nhận đề → nộp bài**

- `ExamServer.acceptLoop` → `ClientHandler.run` → `ClientHandler.handleLine` → `ClientHandler.handleMessage`
- `LOGIN`: `handleMessage` → `ClientHandler.handleLogin` (đọc `studentId`, `password`) → `ExamRoom.login` (có danh sách thì `StudentList.find` + so mật khẩu, sai thì `ExamRoom.loginFailed` gửi `LOGIN_FAIL` chung một lý do và ghi sự kiện; rồi `ExamRoom.loginError` kiểm tra đăng nhập trùng) → `Protocol.send(LOGIN_OK)` kèm `name` → `ExamRoom.trySendExam` (chưa PRECHECK nên chưa gửi đề)
- `PRECHECK_RESULT`: `ClientHandler.handlePrecheckResult` → `ExamRoom.precheckResult` → `ExamRoom.trySendExam` (gửi `EXAM_START` nếu giám thị đã bấm Bắt đầu)
- Giám thị bấm Bắt đầu: `TeacherWindow` → `ExamRoom.startExam` → `ExamLog.attach` → `ExamRoom.trySendExam` cho từng máy đã PRECHECK đạt
- `FEATURES` (mỗi `featureIntervalSeconds` giây, server cấp trong `LOGIN_OK`; cũng là heartbeat): `ClientHandler.handleFeatures` → `ExamRoom.features`
- `APPS`: `ClientHandler.handleApps` (bỏ phần tử sai, tối đa 100 dòng) → `ExamRoom.apps` lưu vào `Session.apps` kèm giờ; giám thị đọc bằng `ExamRoom.appsOf` (tô loại theo luật hiện tại).
- `SUBMIT`: `ClientHandler.handleSubmit` → `ExamRoom.submit` → `ExamFile.grade` → `ExamRoom.writeResults` → `ExamFile.writeResults` → `ExamRoom.sendResult`

**1b. Giám thị đổi luật giữa giờ thi:** `RulesDialog.save` (hoặc menu chuột phải trong `DetailPanel`) → `ExamServer.applyRules` → `RulesFile.save` → `ExamRoom.updateRules` → `Protocol.send(RULES_UPDATE)` cho mọi `Session.connected()`; máy vào sau nhận luật mới trong `LOGIN_OK`.

**2. Máy rớt mạng rồi vào lại**

- Rớt hẳn: `ClientHandler.run` (khối `finally`) → `ExamRoom.disconnected` → `ExamRoom.markDisconnected` → `ExamLog.record(DISCONNECT)`
- Rớt "chết lặng" (không có tin nào): `ExamServer.checkIdle` (mỗi 10 giây) → `ExamRoom.dropIdle` → `Session.close` → `ExamRoom.markDisconnected`
- Vào lại: `ClientHandler.handleLogin` → `ExamRoom.login` (session cũ không còn `connected()` nên được nhận, gắn socket mới, ghi `RECONNECT`) → `ExamRoom.trySendExam` (gửi lại đề với thời gian còn lại) hoặc `ExamRoom.sendResult` nếu đã nộp

**3. Giám thị bấm Kết thúc, rồi bấm Kỳ thi mới**

- Kết thúc: `TeacherWindow` → `ExamRoom.endExam` → `ExamRoom.sendEnd` (máy chưa nộp) → `ExamLog.record(EXAM_END)` → `ExamRoom.writeResults`. Hết giờ thì `scheduler` gọi cùng hàm này.
- Kỳ thi mới: `TeacherWindow` → `ExamRoom.newSession` → `ExamRoom.sendEnd` → `ExamRoom.closeAll` → `sessions.clear()`, `epoch++` → `ExamLog.reset`. Đề đang nạp được giữ; phòng trống nên mã SV cũ đăng nhập lại là người mới (client phải tắt app mở lại); luồng cũ kết thúc muộn bị bỏ qua nhờ `ExamRoom.live`; điểm ML trả về muộn bị bỏ nhờ `epoch` trong `ExamRoom.applyScores`.

## (c) Thầy bảo sửa gì → mở đâu, đổi gì

| Thầy bảo | File → hàm | Đổi gì |
|---|---|---|
| Đổi timeout watchdog | `ExamServer` → trường `idleTimeoutMs` | Đổi `90_000`. Phải lớn hơn chu kỳ heartbeat `featureIntervalSeconds` (tối đa 60 giây) của client. Chu kỳ quét 10 giây nằm ở `ExamServer.start`. |
| Đổi cổng | `ExamServer.openTeacherWindow` | Đổi tham số `server.start(...)` (TCP). Máy sinh viên tự biết cổng TCP mới vì UDP trả cổng trong `EXAM_SERVER <cổng>`. Cổng UDP nằm ở `Protocol.UDP_PORT`, client phải khớp. |
| Thêm loại tin nhắn mới (vd `HELP`) | 1) `Protocol` thêm hằng. 2) `ClientHandler.handleMessage` thêm `case`. 3) `ClientHandler` thêm `handleHelp`. 4) `ExamRoom` thêm hàm `synchronized` (đầu hàm `if (!live(s)) return;`, rồi `log.accept`, `event`). 5) `Session` thêm trường nếu cần lưu. 6) Muốn hiện trên bảng giám thị: `ExamRoom.Row` + `rows()` và giao diện. 7) Client gửi/nhận tin đó. 8) Viết test. | Quên bước 2 thì server chỉ log "Bỏ qua type không biết". |
| Cho nộp lại bài | `ExamRoom.submit` | Bỏ `if (s.result == null)` để lần nào cũng `ExamFile.grade` lại và `writeResults`. Client đang tự đóng kết nối sau `RESULT`, phải sửa phía client nữa. |
| Khoá vào thi muộn sau N phút | `ExamRoom.loginError` | Thêm: `if (existing == null && started && System.currentTimeMillis() - startMillis > N * 60_000) return "Đã quá N phút";`. Máy đã có session vẫn vào lại được. |
| Giới hạn số máy | `ExamRoom.loginError` | Thêm: `if (existing == null && sessions.size() >= MAX) return "Phòng đã đủ máy";`. |
| Đổi cách chấm điểm | `ExamFile.grade` | Sửa dòng `score = Math.round(correct * 1000.0 / total) / 100.0` (thang 10, 2 số lẻ). Sửa luôn `GradingTest`. |
| Thêm cột vào `results.csv` | `Session` + `ExamRoom` + `ExamFile` | Thêm trường ở `Session`, gán ở `ExamRoom` (vd `submit`), thêm tên cột vào `ExamFile.RESULTS_HEADER` và giá trị vào `ExamFile.resultsRow` (cùng thứ tự). |
| Thêm loại sự kiện vào `events.csv` | `ExamRoom` (chỗ xảy ra sự kiện) | Gọi `event(s, "LOAI_MOI", "chi tiết")`; `ExamLog.record` tự ghi file. Muốn hiện trong nhật ký bên giám thị thì thêm tên vào `Roster.NOTABLE` và `Roster.eventText`. |
| Đổi cách kiểm tra mật khẩu (vd băm) | `StudentList` + `ExamRoom.login` | Hiện `ExamRoom.login` so `account.password().equals(password)`; muốn băm thì lưu băm trong `StudentList.Account` và so băm của mật khẩu client gửi tại đúng dòng đó. |
| Gửi thông báo tới mọi máy | `ExamRoom` (hàm mới `broadcast`) | `synchronized`, `Protocol.msg("NOTICE")` rồi vòng `for` qua `sessions.values()`, máy `connected()` thì `Protocol.send(s.out, m)`. Thêm nút ở `TeacherWindow`; client xử lý `NOTICE` như một loại tin mới. |
| Tự kết thúc khi mọi máy đã nộp | `ExamRoom.submit` | Cuối nhánh chấm bài, nếu không còn session nào `!s.submitted` thì `endExam("Mọi máy đã nộp")`. |
| Kick một máy | `ExamRoom` (hàm mới `kick`) | `synchronized`; lấy `Session s = sessions.get(id)`; `s.close(); markDisconnected(s, "giám thị đá");`. Client sẽ tự vào lại, muốn chặn thì thêm `Set<String> kicked` và kiểm tra trong `loginError`. |
| Ghi IP máy vào kết quả | `Session` + `ExamRoom.login` + `ExamFile` | `Session` thêm `String ip`; trong `login` gán `s.ip = socket.getInetAddress().getHostAddress()` (kiểm tra `socket != null`, test truyền `null`); thêm cột như dòng "Thêm cột". |
| Đổi tin báo sai mật khẩu | `ExamRoom.login` | Chuỗi `Sai mã sinh viên hoặc mật khẩu` (cố ý không nói rõ sai cái nào); test `loginWithWrongPasswordFails` kiểm tra đúng chuỗi này. |
| Đổi chu kỳ FEATURES và chấm ML | `config/rules.json` | Đổi `featureIntervalSeconds` (10 đến 60, sai thì 30). Server gửi trong `LOGIN_OK`, `ExamServer.start` chấm ML cùng chu kỳ. |

## (d) Năm câu thầy hay hỏi

**1. Vì sao mỗi client một thread?** `readLine()` chặn cho tới khi có dữ liệu, nên nếu một luồng phục vụ nhiều máy thì máy im lặng sẽ làm các máy khác phải chờ. Phòng thi chỉ vài chục máy nên vài chục luồng là rẻ, và code chỉ là một vòng `while` đọc dòng, dễ hiểu hơn NIO/Selector.

**2. Vì sao dùng `synchronized`?** Nhiều luồng cùng đụng vào `sessions` và các cờ `started`/`ended`: luồng của từng máy, luồng giao diện (`rows()`), luồng lập lịch (hết giờ, watchdog, ML). Mọi hàm đổi trạng thái của `ExamRoom` khoá cùng một monitor nên mỗi lúc chỉ một luồng sửa, không lệch (vd hai máy cùng đăng nhập một mã SV). Chỉ một khoá chung nên không lo deadlock; `ExamLog` có khoá riêng và luôn được gọi sau khoá của phòng.

**3. Vì sao gọi ML ngoài khối `synchronized`?** Gọi HTTP có thể mất vài giây hoặc treo; giữ khoá phòng lúc đó thì đăng nhập, nộp bài, bảng giám thị đều đứng. Nên `ExamServer.scoreRoomNow` lấy bản sao mẫu và `epoch` trong khoá, gọi ML ngoài khoá, rồi mới khoá lại để `applyScores`; nếu giữa chừng đã sang kỳ mới thì `epoch` khác và điểm bị bỏ.

**4. Watchdog hoạt động thế nào?** `ExamServer.start` lập lịch `checkIdle` mỗi 10 giây, gọi `ExamRoom.dropIdle(idleTimeoutMs)`. Máy đang ở trạng thái "Đang thi" mà `now - lastSeen` quá 90 giây thì bị `Session.close()` và đánh dấu "Mất kết nối". `lastSeen` được cập nhật khi nhận bất kỳ tin nào (kể cả `FEATURES` mỗi chu kỳ) và đúng lúc gửi `EXAM_START`, vì trước giờ thi client chưa gửi gì và không được tính là im lặng. Cần watchdog vì rút mạng đột ngột thì TCP không báo lỗi, `readLine` treo mãi.

**5. Vì sao TCP cho nghiệp vụ còn UDP chỉ để tìm server?** Đăng nhập, đề, nộp bài, kết quả không được mất hay đảo thứ tự, và cần biết khi nào mất kết nối, nên dùng TCP. Máy sinh viên chưa biết IP server nên broadcast `EXAM_DISCOVER` bằng UDP (TCP không broadcast được); mất gói thì hỏi lại cũng chẳng sao. Server trả `EXAM_SERVER <cổng TCP>` đúng người hỏi, rồi client mở TCP.
