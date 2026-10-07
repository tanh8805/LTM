# CONTRIBUTING – Quy tắc làm việc nhóm

Nhóm 4 người làm song song trên cùng một repo. Mục tiêu của các quy tắc dưới đây: **ít xung đột, dễ đọc, dễ giải thích khi vấn đáp.**

## 1. Ai sở hữu phần nào

| Người | Vai trò | Sở hữu |
|---|---|---|
| **Nguoi1** | Network | `exam.common.protocol`, `exam.common.model`, `exam.server.api`, `exam.server.net`, `exam.server.session`, `exam.server.rate`, `ServerMain`, `exam.client.api`, `exam.client.net`, `StudentMain`, `TeacherMain`, `config/server.properties`, `docs/`, file build (`pom.xml`) |
| **Nguoi2** | Nghiệp vụ thi | `exam.server.exam`, `exam.server.db`, `schema.sql` + dữ liệu mẫu (`source/server/src/main/resources/`), `exam.client.student`, `exam.client.teacher` (trừ `teacher.monitor`) |
| **Nguoi3** | Giám sát | `exam.server.monitor`, `exam.client.monitor`, `exam.client.teacher.monitor` |
| **Nguoi4** | ML / Thực nghiệm | `exam.common.ml`, `exam.server.ml`, `exam.tools`, `ml-service/`, `config/ml.properties`, `data/traces/`, `statics/`, file `exam.client.api.IsolationForestScorer` |

Mọi thư mục sở hữu khớp với `.github/CODEOWNERS`. Mỗi file Java có dòng đầu `// Owner: NguoiX`.
Chi tiết công việc từng người, tuần và tiêu chí xong: **`WORK_SPLIT.md`**.

## 2. Quy tắc vàng

1. **Không sửa file của người khác.** Cần họ sửa gì thì nhắn họ (hoặc mở issue/PR comment). Ngoại lệ duy nhất: sửa lỗi build khiến mọi người không chạy được — vẫn phải báo ngay cho chủ file.
2. **Muốn đổi `common/` (protocol, model) → nhắn Nguoi1.** Hai phía Server và Client cùng phụ thuộc vào nó, đổi tùy tiện sẽ làm gãy người khác.
3. **Muốn đổi bất kỳ `*.api` (interface) → nhắn Nguoi1.** Interface là hợp đồng giữa các thành viên.
4. **Muốn thêm thư viện hoặc framework mới → hỏi cả nhóm trước.** Danh sách đã thống nhất nằm trong `docs/SPEC.md` mục 3. Không tự thêm Spring, Lombok, Netty, Redis, ... Việc này không phải do môn học cấm, mà là quyết định thiết kế của nhóm để làm nổi bật TCP socket và concurrency.
5. **Không đổi tên package/file người khác** và không xóa file nếu chưa hỏi.

## 3. Quy trình Git

- Mỗi task **một branch**, đặt tên `feat/<ten>`, ví dụ `feat/reconnect`, `feat/question-bank-dao`, `feat/oshi-metrics`.
- **Không push thẳng vào `main`.** Mở Pull Request; cần **ít nhất 1 người duyệt** (CODEOWNERS sẽ tự yêu cầu chủ của thư mục bạn sửa).
- **Mỗi sáng chạy `git pull --rebase`** (trên `main` và trên branch đang làm) để luôn cập nhật.
- PR nhỏ, **merge trong 1–2 ngày**. Branch sống lâu sẽ xung đột rất khó sửa.
- Commit message ngắn gọn, nói rõ việc đã làm (ví dụ: `Add RECONNECT handling in ClientHandler`).
- Không commit: `data/exam.db`, thư mục `target/`, file IDE, file `.log` (đã có trong `.gitignore`).

## 4. Build, chạy, test

Cần: Java 21, Maven 3.9+, Python 3.10+ (cho ml-service). **Chạy mọi lệnh Server/Client từ thư mục gốc repo** (để `config/` và `data/` đúng).

```bash
# Build toàn bộ + chạy test (bắt buộc pass trước khi mở PR)
cd source
mvn -q package

# Chỉ chạy test
mvn -q test

# Chỉ một module (ví dụ common)
mvn -q -pl common test
```

Sau `mvn package`, mỗi module có `target/<module>.jar` và `target/lib/` (thư viện phụ thuộc). Chạy (từ thư mục gốc repo):

```bash
# Server
java -cp "source/server/target/server.jar:source/server/target/lib/*" exam.server.ServerMain

# Teacher (mặc định: localhost 5000 gv01 teacher123)
java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.TeacherMain

# Student (mặc định: localhost 5000 SV001 123456 CA001)
java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.StudentMain
# Tham số: [host] [port] [maSV] [matKhau] [maCaThi], ví dụ chạy sinh viên thứ hai:
java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.StudentMain localhost 5000 SV002 123456 CA001

# ML service (Python)
cd ml-service
python -m venv .venv && source .venv/bin/activate    # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app
```

Ghi chú:

- Trên **Windows** đổi dấu `:` trong classpath thành `;` (ví dụ `"source/server/target/server.jar;source/server/target/lib/*"`).
- Nếu log tiếng Việt bị lỗi font, thêm `-Dstdout.encoding=UTF-8` ngay sau `java`.
- Muốn nạp lại dữ liệu mẫu: dừng Server, xóa `data/exam.db`, chạy lại Server.
- Tài khoản mẫu: xem `docs/SPEC.md` mục 14.
- Kiểm tra nhanh Server còn sống: chạy Teacher/Student và xem log `[Login] OK` và `[Heartbeat] ...` ở cửa sổ Server.

## 5. Quy tắc viết code (để vấn đáp giải thích được)

Ưu tiên: **dễ đọc > đơn giản > dễ debug > dễ bảo trì > tối ưu.** Có hai cách, chọn cách dài hơn một chút nhưng dễ đọc.

- Code cho **sinh viên năm 3 đọc, debug và giải thích được**. Không viết cho "trông chuyên nghiệp".
- **Không over-engineering:** không tạo Factory/Manager/Helper/Generic... chỉ để "kiến trúc đẹp". Một class một trách nhiệm rõ ràng; một method làm một việc chính.
- Dùng `for` thay cho chuỗi Stream phức tạp nếu `for` dễ đọc hơn. Tránh lambda phức tạp, generic lồng nhau, reflection, Optional lồng nhau. Code phải dễ đặt breakpoint.
- **Tên rõ nghĩa:** `studentId`, `heartbeatSequence`, `handleLogin()`, `sendLoginOk()`. Không `x`, `tmp`, `data2`, `doStuff()`.
- **Comment** giải thích kiến trúc, protocol, concurrency, lý do thiết kế — không comment điều hiển nhiên.
- **TODO ghi rõ người và việc:** `// TODO(Nguoi2): Mô tả chính xác việc cần làm.` Không viết `// TODO` trơ trọi.
- **Dòng đầu mỗi file Java:** `// Owner: NguoiX`.
- **Không nuốt exception.** Không `catch (Exception e) {}` rỗng. Chưa xử lý nghiệp vụ thì tối thiểu:
  ```java
  catch (Exception e) {
      // TODO(NguoiX): Add proper error handling.
      e.printStackTrace();
  }
  ```
- **SQL rõ ràng:** dùng text block + `PreparedStatement`, không query builder.
  ```java
  String sql = """
      SELECT id, username, full_name
      FROM students
      WHERE username = ? AND password = ?
      """;
  ```
  Thứ tự trong DAO: mở connection → prepare → set tham số → execute → đọc kết quả → đóng (try-with-resources).
- **Giao diện Swing đơn giản:** chỉ cần dùng được, không cần đẹp.
- **Cập nhật tài liệu cùng PR:** đổi message → `docs/PROTOCOL.md`; đổi luật/ngưỡng → `docs/SPEC.md`.

## 6. Checklist trước khi mở PR

- [ ] `cd source && mvn -q package` thành công (có test).
- [ ] Chỉ sửa file thuộc phần mình sở hữu (hoặc đã được chủ file đồng ý).
- [ ] Mọi `TODO` có dạng `TODO(NguoiX): ...`; file mới có `// Owner: NguoiX`.
- [ ] Không có `catch` rỗng; không commit `target/`, `data/exam.db`.
- [ ] Đã chạy thử Server + client và chức năng của mình hoạt động.
- [ ] Đã cập nhật tài liệu nếu đổi protocol/luật.
