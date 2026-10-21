# Server 2: giao diện giám thị và dữ liệu

Cửa sổ giám thị (Swing + FlatLaf), các hộp thoại soạn đề/danh sách/luật, và mọi thứ đọc ghi file: đề thi, chấm điểm, `results.csv`, `students.csv`, `rules.json`. Trạng thái phòng thi nằm ở Server 1 (`ExamRoom`); phần này chỉ đọc qua `rows()`, `summary()`, `history()` và gọi lệnh `startExam`, `endExam`, `newSession`.

> Mỗi `File.hàm` trong tài liệu này đều có thật trong code. Các đoạn code chỉ để minh hoạ.

## 1. Bảng file

Trong `src/main/java/exam/server/` trừ khi ghi khác.

| File | Một câu |
|---|---|
| `TeacherWindow`, `SideBar` | Cửa sổ giám thị: thanh bên 5 mục (Giám sát, Sinh viên, Đề thi, Ứng dụng, Kết quả), thanh trên (tên đề, đồng hồ, `IP LAN`, nút Bắt đầu/Kết thúc/Kỳ thi mới), tab lọc, ô tìm kiếm, nhật ký; `Timer` 1 giây gọi `refresh`. |
| `RosterTable`, `Roster` | Bảng thí sinh (biểu tượng cảnh báo, nhãn trạng thái, thanh Bất thường); `Roster` là logic thuần: lọc theo tab, tìm không dấu, chữ hiển thị, tên Việt của sự kiện, sự kiện đáng chú ý. |
| `DetailPanel`, `HistoryDialog` | Khung chi tiết bên phải (số liệu, sự kiện đáng chú ý, app đang mở, chuột phải đổi luật) và hộp Lịch sử (giờ, loại, chi tiết; Vi phạm tô cam, Bất thường tô đỏ). |
| `Pill`, `TabButton`, `LocalAddresses` | Nhãn trạng thái bo tròn, nút tab, lấy IP LAN để hiển thị. |
| `RulesDialog`, `ListEditor`, `RulesFile` | Hộp thoại `Quản lý ứng dụng`; `ListEditor` là danh sách chuỗi có Thêm/Xoá; `RulesFile` sửa các danh sách trong luật và ghi lại `rules.json`, giữ các khoá khác. |
| `ExamFile` | Nạp và kiểm tra file đề, bỏ đáp án khi gửi cho sinh viên, chấm điểm, ghi `results.csv`. |
| `ExamBank`, `ExamBankDialog`, `QuestionDialog` | Soạn đề: `build` dựng JSON (đánh số lại `id`), `save` ghi file tạm rồi kiểm tra bằng `ExamFile.load`, `parseCsv` nhập câu hỏi từ CSV. |
| `StudentList` | Danh sách tài khoản (CSV `studentId,name,password`): `parse` báo `Dòng n: lý do`, `find` không phân biệt hoa thường, `save` ghi `config/students.csv`. |
| `common/Icons`, `Palette`, `Format`, `Html`, `Card`, `Look` | Biểu tượng tự vẽ, màu và khoảng cách, điểm kiểu Việt Nam (dấu phẩy), ngắt dòng HTML, thẻ trắng viền mảnh, cài FlatLaf (cũng dùng ở client). |

## 2. Luồng chạy

**1. Server có vi phạm → dòng được tô cam**

- Tin `VIOLATION` đến server (Server 1): `ExamRoom.violation` tăng `Session.violations`, đặt `lastViolation`.
- `TeacherWindow` có `Timer` 1 giây gọi `TeacherWindow.refresh` → `ExamRoom.rows()` → `Roster.filter` → `RosterTable.setRows`.
- `Roster.needsAttention(row)` đúng (có vi phạm hoặc bị ML gắn cờ) nên `RosterTable` tô nền cam nhạt, hiện biểu tượng cảnh báo, đếm vào tab "Cần chú ý"; cột "Vi phạm gần nhất" hiện `USB E:\ · 09:41`.
- Nhấp đúp dòng: `TeacherWindow.openHistory` → `ExamRoom.history` → `HistoryDialog`; chọn dòng thì `TeacherWindow.showDetail` → `DetailPanel.show` (sự kiện lọc bằng `Roster.notableEvents`, chữ bằng `Roster.eventText`).

**2. Nạp đề, nhập danh sách, bắt đầu thi**

- Chọn đề: `TeacherWindow.chooseExam` → `ExamRoom.loadExam` → `ExamFile.load` (kiểm tra từng câu, sai thì ném `IllegalArgumentException` có số câu) → `ExamFile.questionsForClient` (bỏ `answer`).
- Soạn đề: `ExamBankDialog` / `QuestionDialog` → `ExamBank.build` → `ExamBank.save` (ghi file tạm, `ExamFile.load` kiểm tra, đạt mới đổi tên).
- Nhập sinh viên: `TeacherWindow.importStudents` → `StudentList.parse` → `StudentList.save`.
- Nút Bắt đầu: `TeacherWindow.onAction` → `ExamRoom.startExam`.

**3. Nộp bài đến `results.csv`**

- `ExamRoom.submit` (Server 1) → `ExamFile.grade` (đếm câu đúng, thang 10, 2 số lẻ) → `ExamRoom.writeResults` → `ExamFile.writeResults` → `ExamFile.resultsRow` mỗi sinh viên một dòng (`ExamFile.csv` escape dấu phẩy, nháy kép), UTF-8 có BOM để Excel đọc tiếng Việt.

**4. Đổi luật giữa giờ**

- `RulesDialog.save` (hoặc chuột phải một app trong `DetailPanel` → `TeacherWindow.setAppAllowed`) → `ExamServer.applyRules` → `RulesFile.save` → `ExamRoom.updateRules` → `RULES_UPDATE` tới các máy đang thi (xem file `server-1.md`).

## 3. Thầy bảo sửa gì → mở đâu, đổi gì

| Thầy bảo | File → hàm | Đổi gì |
|---|---|---|
| Thêm / bỏ app khỏi danh sách cấm, cho phép, bỏ qua | Không cần sửa code | Bấm **Ứng dụng** trên cửa sổ giám thị (hoặc sửa tay `config/rules.json` rồi chạy lại server). Tên không phân biệt hoa thường, có hay không `.exe` đều được. |
| Thêm cột vào bảng giám thị (vd "Thời gian rời cửa sổ") | `Session`, `ExamRoom.features`/`Row`/`rows` (Server 1), `RosterTable` | Cộng dồn giá trị thứ 3 của `FEATURES` vào trường mới ở `Session`, thêm vào `Row`; ở `RosterTable` thêm tên vào `COLUMNS`, độ rộng vào `WIDTHS`, một nhánh vào `Model.getValueAt`; nếu cột nằm sau cột "Bất thường" thì nhớ chỉnh chỉ số trong các renderer. |
| Thêm tab lọc mới (vd "Chưa nộp") | `Roster.Tab`, `Roster.inTab` | Thêm giá trị enum có nhãn, thêm `case` trong `inTab`; `TeacherWindow` dựng nút tab từ enum nên tự có. |
| Đổi màu dòng hoặc nhãn trạng thái | `common/Palette`, `RosterTable` (renderer) | Đổi hằng màu trong `Palette`; nhãn trạng thái theo chuỗi `Session.ST_*` ở `RosterTable.StatusRenderer`. |
| Đổi cách chấm điểm | `ExamFile.grade` | Sửa dòng `score = Math.round(correct * 1000.0 / total) / 100.0` (thang 10, 2 số lẻ). Sửa luôn `GradingTest`. |
| Thêm cột vào `results.csv` | `ExamFile.RESULTS_HEADER`, `resultsRow` | Thêm tên cột và giá trị cùng thứ tự; dữ liệu lấy từ `Session` (nhờ Server 1 thêm trường). |
| Đổi định dạng file đề | `ExamFile.validate`, `validateQuestion`, `questionsForClient` | Thêm trường mới vào kiểm tra, và vào bản sao gửi sinh viên nếu client cần thấy. |
| Đổi cách kiểm tra mật khẩu (vd băm) | `StudentList.Account`, `StudentList.parse` | Lưu băm trong `Account`; chỗ so sánh nằm ở `ExamRoom.login` (Server 1), đổi cùng lúc. |
| Đổi tên hiển thị của loại sự kiện trong Lịch sử | `Roster.typeText` | Sửa chuỗi tiếng Việt; `events.csv` vẫn giữ mã tiếng Anh. |
| Hiện thêm sự kiện trong khung chi tiết | `Roster.NOTABLE`, `Roster.eventText` | Thêm mã sự kiện vào `NOTABLE` và một `case` trong `eventText`. |
| Đổi nhịp cập nhật bảng | `TeacherWindow` (hằng của `Timer`) | Đổi 1000 ms ở chỗ khởi tạo `Timer` gọi `refresh`. |
| Đổi thư mục lưu kết quả | `ExamRoom.setResultsRoot` / `resultsRoot` (Server 1), nút mở ở `TeacherWindow.openResults` | Mặc định `ket-qua/`; đổi giá trị mặc định trong `ExamRoom`. |

## 4. Câu hỏi thường gặp

**1. Vì sao bảng giám thị cập nhật bằng `Timer` 1 giây thay vì server đẩy sự kiện?** Đơn giản và an toàn luồng: `Timer` của Swing chạy trên luồng giao diện (EDT), gọi `ExamRoom.rows()` (hàm `synchronized` trả bản sao) rồi vẽ lại. Phòng vài chục máy thì 1 giây một lần là rất nhẹ.

**2. Vì sao Swing phải cập nhật trên EDT?** Các thành phần Swing không an toàn đa luồng. Nên mọi thay đổi giao diện đi qua `SwingUtilities.invokeLater` hoặc `Timer`; log từ luồng mạng cũng được đưa vào qua `invokeLater`.

**3. Vì sao tách `Roster` khỏi `RosterTable`?** `Roster` chỉ có logic thuần (lọc, tìm không dấu, chữ hiển thị) nên test được bằng JUnit mà không mở cửa sổ (`RosterTest`); `RosterTable` chỉ vẽ.

**4. Vì sao đáp án chỉ nằm ở server?** `ExamFile.questionsForClient` bỏ trường `answer` trước khi gửi; chấm điểm làm ở `ExamFile.grade` trên server nên client không có gì để gian lận về đáp án.

**5. Vì sao `results.csv` có BOM?** Excel không đoán được UTF-8; ký tự BOM đầu file (`\uFEFF`) giúp mở ra đúng tiếng Việt. `ExamFile.csv` bọc nháy kép khi giá trị có dấu phẩy, nháy kép hoặc xuống dòng.

**6. Sửa luật có cần build lại hay gửi lại jar không?** Không. Luật nằm ở `config/rules.json` phía server, gửi xuống client trong `LOGIN_OK`; đổi giữa giờ thì `RULES_UPDATE` tới các máy đang thi. `RulesFile.save` giữ nguyên các khoá khác của file.

**7. Vì sao ML chỉ hiển thị "Bất thường", không ghi "Gian lận"?** Mô hình chỉ biết máy khác số đông; giám thị mới là người quyết định. Vì vậy `DetailPanel.NOTE` nhắc điều này.
