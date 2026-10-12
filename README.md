# LTM - Hệ thống thi trắc nghiệm có giám sát

Ứng dụng thi trắc nghiệm trong mạng LAN: giáo viên chọn đề và bắt đầu thi, sinh viên làm bài trên client Windows.
Trong giờ thi, client giám sát bằng luật (app ngoài whitelist, kết nối tới địa chỉ bị cấm, cắm USB, rời cửa sổ thi) và gửi dữ liệu về server.
Server gửi dữ liệu cả phòng sang service Python dùng Isolation Forest để tìm máy có hành vi khác biệt so với số đông.

## Phân công

| Người | File | Phụ trách |
|---|---|---|
| Cả nhóm | `exam/common/Protocol.java` | Giao thức chung |
| Người 1 | `StudentApp.java`, `ServerConnection.java`, `ServerFinder.java` | Giao diện và kết nối phía sinh viên |
| Người 2 | `Monitor.java` | Giám sát ở client |
| Người 3 | `ExamServer.java`, `ClientHandler.java`, `TeacherWindow.java` | Server và giao diện giám thị |
| Người 4 | `MlClient.java`, `ml/ml_service.py`, `ml/simulate_clients.py` | Service ML và giả lập client |

## Chạy (từ thư mục gốc repo)

```bash
mvn -q package                                              # build target/exam.jar
cd ml && uvicorn ml_service:app --host 127.0.0.1 --port 8000   # service ML
java -cp target/exam.jar exam.server.ExamServer             # server
java -cp target/exam.jar exam.client.StudentApp             # client
```

Phần còn lại của tài liệu được hoàn thiện ở Task 11. Đặc tả đầy đủ: [docs/AGENT_BRIEF.md](docs/AGENT_BRIEF.md).
