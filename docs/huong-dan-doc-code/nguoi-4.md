# Người 4: ML và giả lập

Mảng của bạn: phát hiện máy "khác số đông" bằng Isolation Forest, nối Java với Python, và giả lập phòng thi để demo.

> Các đoạn code trong tài liệu này **chỉ để minh hoạ**, không áp dụng vào code thật.

## 1. Bạn phụ trách gì

| File | Làm gì |
|---|---|
| `ml/ml_service.py` | Service FastAPI: nhận các mẫu `FEATURES` của cả phòng, fit Isolation Forest, trả điểm bất thường 0–1 cho từng sinh viên. |
| `ml/simulate_clients.py` | Giả lập nhiều client TCP thật (kể cả một máy "gian lận") để demo mà không cần nhiều laptop. |
| `server/MlClient.java` | Phía Java gọi `POST /score` bằng `HttpClient`, timeout 5 giây. |
| `ml/test_ml_service.py` | 5 test của service. |

## 2. Thứ tự đọc

1. `ml_service.score_samples`: toàn bộ logic ML (hàm thuần, không cần HTTP).
2. `ml_service.score` và hai lớp `Sample`, `ScoreRequest`: endpoint `/score` và kiểm tra dữ liệu vào (`values` đúng 6 số, sai thì FastAPI trả 422).
3. `MlClient.score`: gửi JSON, mã khác 200 thì ném `IOException`.
4. `ExamServer.scoreRoomNow`: gom mẫu, gọi ML ngoài khối khoá, lỗi thì ghi log `ML lỗi` và giữ điểm cũ.
5. `ExamRoom.applyScores`: ghi `anomalyScore`, `anomaly`, `maxAnomaly` vào từng `Session`.
6. `simulate_clients.SimClient.handle` và `feature_loop`: client giả phản ứng với từng loại tin và gửi `FEATURES`.
7. `test_ml_service.py`: đọc để hiểu mỗi quy tắc được kiểm tra thế nào.

## 3. Một luồng chạy thật: từ `FEATURES` đến cột "Bất thường"

1. Mỗi 30 giây client gửi `FEATURES` (`window` và 6 số) từ `Monitor.sendFeatures`. Trên server, `ClientHandler.features` kiểm tra đúng 6 số rồi `ExamRoom.features` lưu mẫu vào `Session.samples`.
2. `ExamServer` có lịch 30 giây gọi `scoreIfRunning`. Bài đang diễn ra nên gọi `scoreRoomNow`: `room.samplesForMl()` gom mẫu của mọi sinh viên (thêm `studentId`).
3. `MlClient.score` gửi `POST http://localhost:8000/score` với `{"samples": [...]}` (không giữ khoá nào của phòng trong lúc chờ).
4. `ml_service.score` gọi `score_samples`: dưới 20 mẫu thì trả `not_enough_data`; ngược lại fit `IsolationForest`, tính điểm thô `-model.score_samples(x)`, chuẩn hoá min–max về 0–1, rồi với mỗi sinh viên lấy tối đa trong **3 cửa sổ gần nhất** và đặt `anomaly` theo quy tắc ở mục 4.
5. Trả `{"status": "ok", "results": [...]}`. `ExamRoom.applyScores` cập nhật `Session` (`status` khác `ok` thì bỏ qua).
6. `TeacherWindow.refresh` (mỗi giây) đọc `room.rows()` và hiện cột "Bất thường" (`%.2f`); dòng có `anomaly` bị tô đỏ nhạt.

## 4. Kiến thức cần giải thích được với thầy

- **Ý tưởng Isolation Forest:** dựng nhiều cây ngẫu nhiên, mỗi lần cắt chọn ngẫu nhiên một feature và một ngưỡng. Điểm "lạ" bị cô lập sau **ít lần cắt**, điểm giống số đông cần cắt nhiều lần. Độ dài đường đi trung bình càng ngắn thì càng bất thường.
- **Vì sao không cần dataset:** đây là học không giám sát; mỗi chu kỳ fit lại ngay trên dữ liệu của chính buổi thi và so các máy với nhau. Không có nhãn "gian lận" nào để học.
- **Chuẩn hoá min–max:** `score_samples` của sklearn càng âm càng bất thường; ta đổi dấu rồi đưa về 0–1 để dễ đọc: 1 là máy khác biệt nhất phòng, 0 là giống số đông nhất. Mọi máy giống hệt nhau thì mọi điểm bằng 0.
- **Vì sao thêm ngưỡng 0.7 cho `anomaly`:** feature là số đếm nhỏ, nhiều mẫu trùng nhau nên `predict() == -1` mặc định gắn cờ quá nhiều máy bình thường (25–88% trong thí nghiệm giả lập). Cờ chỉ bật khi `predict == -1` **và** điểm chuẩn hoá ≥ `FLAG_MIN_SCORE` = 0.7. Điểm tương đối theo phòng nên phòng không có ai lệch vẫn có máy ở dải cao nhất.
- **Chỉ xét 3 cửa sổ gần nhất:** hành vi cũ không nên kéo dài mãi; máy chỉ lệch ở đầu giờ rồi bình thường lại thì không còn bị cờ.
- **Vì sao tách ML thành service Python gọi qua HTTP:** scikit-learn có sẵn ở Python; Java không có Isolation Forest. Tách ra thì hai bên độc lập, và service tắt hay lỗi chỉ làm cột "Bất thường" đứng yên (`ML lỗi` trong log), server thi vẫn chạy.
- **Giao diện ghi "Bất thường", không ghi "Gian lận":** mô hình chỉ biết máy khác số đông; giám thị mới là người quyết định.

## 5. 3 câu thầy có thể hỏi và cách sửa

**a) "Thêm một feature thứ 7."** Phải sửa cả hai phía vì độ dài `values` được kiểm tra ở nhiều nơi.

```java
// Monitor.buildFeatures(): thêm phần tử thứ 7 vào mảng v (ví dụ giả định: pasteCount là bộ đếm mới)
int[] v = {newProcesses.size(), focusLostCount, secondsLost, newHosts.size(), violationsInWindow, answers, pasteCount};
// ClientHandler.features(): arr.size() == 6  ->  arr.size() == 7
```
```python
# ml_service.py
values: list[float] = Field(min_length=7, max_length=7)
# simulate_clients.py: thêm một số vào cuối normal_values() và cheater_values()
# test_ml_service.py và ExamServerTest/MlClientTest: cập nhật dữ liệu mẫu cho đủ 7 số
```

**b) "Đổi ngưỡng gắn cờ."** Sửa một hằng số trong `ml_service.py`; ngưỡng cao hơn thì ít máy bị gắn cờ hơn (nhưng dễ bỏ sót hơn).

```python
FLAG_MIN_SCORE = 0.8   # trước là 0.7
```

**c) "Đổi số cửa sổ gần nhất từ 3 thành 5."**

```python
RECENT = 5   # trước là 3
# test_uses_three_latest_windows cần cập nhật: cửa sổ lệch phải nằm ngoài 5 cửa sổ gần nhất
# (ví dụ phòng có 6 cửa sổ mỗi người và chỉ cửa sổ 1 lệch)
```
