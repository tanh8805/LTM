# CONTRIBUTING – Quy tắc làm việc nhóm

Nhóm 4 người làm song song trên cùng một repo. Mục tiêu của các quy tắc dưới đây: **ít xung đột, dễ đọc, dễ giải thích khi vấn đáp.**

## 1. Ai sở hữu phần nào

| Người | Vai trò | Sở hữu |
|---|---|---|
| **Nguoi1** | Network | `exam.common.protocol`, `exam.common.model`, `exam.server` (`ServerMain`, `ServerApp`, `ServerConfig`), `exam.server.api`, `exam.server.net`, `exam.server.session`, `exam.server.rate`, `exam.client.api`, `exam.client.net`, `exam.client` (`StudentClient`, `TeacherClient`, `StudentMain`, `TeacherMain`, `ClientConfig`), `source/e2e/`, `config/server.properties`, `config/client.properties`, `docs/`, file build (`pom.xml`) |
| **Nguoi2** | Nghiệp vụ thi | `exam.server.exam`, `exam.server.db`, `schema.sql` + dữ liệu mẫu (`source/server/src/main/resources/`), `exam.client.student`, `exam.client.teacher` (trừ `teacher.monitor`) |
| **Nguoi3** | Giám sát | `exam.server.monitor`, `exam.client.monitor`, `exam.client.teacher.monitor` |
| **Nguoi4** | ML / Thực nghiệm | `exam.common.ml`, `exam.server.ml`, `exam.client.ml`, `exam.tools`, `ml-service/`, `config/ml.properties`, `data/traces/`, `statics/` |

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
# Build toàn bộ + chạy test (bắt buộc pass trước khi mở PR). Mất khoảng 1,5 phút vì có test mạng và end-to-end.
cd source
mvn -q package

# Chỉ chạy test
mvn -q test

# Chỉ một module (ví dụ common); module khác cần thêm -am để build common trước
mvn -q -pl common test
mvn -q -pl client -am test
```

Sau `mvn package`, mỗi module có `target/<module>.jar` và `target/lib/` (thư viện phụ thuộc). Chạy (từ thư mục gốc repo):

```bash
# Server (đọc config/server.properties và config/ml.properties)
java -cp "source/server/target/server.jar:source/server/target/lib/*" exam.server.ServerMain

# Teacher (mặc định: host/port từ config/client.properties, gv01 teacher123)
java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.TeacherMain

# Student (mặc định: SV001 123456 CA001)
java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.StudentMain
# Tham số: [host] [port] [maSV] [matKhau] [maCaThi] [auto], ví dụ chạy sinh viên thứ hai:
java -cp "source/client/target/client.jar:source/client/target/lib/*" exam.client.StudentMain localhost 5000 SV002 123456 CA001

# ML service (Python)
cd ml-service
python -m venv .venv && source .venv/bin/activate    # Windows: .venv\Scripts\activate
pip install -r requirements.txt --extra-index-url https://download.pytorch.org/whl/cpu
uvicorn app:app                                      # cổng 8000
pip install -r requirements-dev.txt && python -m pytest   # test ml-service
```

Quy trình chạy thử bằng tay: bật Server → bật Teacher, đăng nhập `gv01` → tab **Câu hỏi** (đã có 30 câu mẫu) → tab **Đề thi** tạo đề → tab **Ca thi** tạo ca với mã `CA001` và danh sách thí sinh `SV001`, `SV002` → bật Student đăng nhập `SV001` / `CA001` → Teacher bấm **Bắt đầu ca** → sinh viên làm bài, giáo viên xem tab **Giám sát**.

Chạy kịch bản end-to-end (20 bước) bằng **tiến trình thật** (ml-service + Server thật, OSHI thật):

```bash
cd source && mvn -q package -DskipTests && cd ..
java -cp "source/e2e/target/e2e.jar:source/e2e/target/lib/*" exam.e2e.EndToEndRunner \
     --python ml-service/.venv/bin/python --backend naive --work-dir /tmp/ltm-e2e
# --backend naive: ml-service dùng bộ dự báo thử nghiệm (không cần tải model). --backend chronos: dùng Chronos-Bolt thật
# (cần tải được model từ huggingface.co; nếu model chưa nạp, ml-service trả 503 và Server bỏ qua ML, kịch bản vẫn phải qua).
```

Thực nghiệm ML (Nguoi4):

```bash
java -cp "source/tools/target/tools.jar:source/tools/target/lib/*" exam.tools.Simulator --out data/traces/sim.csv
java -cp "source/tools/target/tools.jar:source/tools/target/lib/*" exam.tools.Recorder --out data/traces/me.csv --seconds 120
java -cp "source/tools/target/tools.jar:source/tools/target/lib/*" exam.tools.ExperimentRunner \
     --trace data/traces/sim.csv --ml-url http://localhost:8000 --out statics/results/experiment.csv
# Thu baseline thật từ phòng thi (giáo viên bật BASELINE rồi xuất số liệu từ Server):
java -cp "source/tools/target/tools.jar:source/tools/target/lib/*" exam.tools.Recorder \
     --from-server localhost 5000 gv01 teacher123 --out data/traces/baseline.csv
```

Ghi chú:

- Trên **Windows** đổi dấu `:` trong classpath thành `;` (ví dụ `"source/server/target/server.jar;source/server/target/lib/*"`).
- Nếu log tiếng Việt bị lỗi font, thêm `-Dstdout.encoding=UTF-8` ngay sau `java`.
- Muốn nạp lại dữ liệu mẫu (hoặc sau khi schema đổi): dừng Server, xóa `data/exam.db`, chạy lại Server.
- Tài khoản mẫu: giáo viên `gv01` / `teacher123`; sinh viên `SV001`…`SV010` / `123456`. Chi tiết: `docs/SPEC.md` mục 14.
- `ml.mode` mặc định `NONE`; muốn thử ML sửa `config/ml.properties`. Muốn Chronos thật phải cho phép truy cập `huggingface.co` (hoặc đặt `LTM_ML_MODEL`).
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
