# ML và thực nghiệm

Phát hiện máy "khác số đông" bằng Isolation Forest, nối Java với Python, giải thích lý do cho giám thị, và giả lập phòng thi để làm thực nghiệm/demo. ML **chỉ cảnh báo**, không bao giờ kết thúc bài thi của ai.

> Các đoạn code trong tài liệu này **chỉ để minh hoạ**, không áp dụng vào code thật.

## 1. Bảng file

| File | Một câu |
|---|---|
| `ml/ml_service.py` | Service FastAPI: nhận mẫu `FEATURES` của cả phòng, fit Isolation Forest, trả điểm 0 đến 1, `anomaly` và `reasons` (tối đa 3 feature lệch nhất) cho từng sinh viên. |
| `ml/test_ml_service.py` | Test của service (đủ mẫu, máy lệch, phòng giống hệt, 3 cửa sổ gần nhất, `reasons`). |
| `ml/simulate_clients.py` | Giả lập nhiều client TCP thật (kể cả một máy "gian lận") để demo mà không cần nhiều laptop; mặc định `--interval 20`. |
| `server/MlClient.java` | Phía Java gọi `POST /score` bằng `HttpClient`, timeout 5 giây. |
| `server/MlScores.java` | Gom mẫu gửi ML, ghi điểm trả về vào `Session`, log `Bất thường`/`Hết bất thường`/`ML: chưa đủ dữ liệu`, và ghép câu giải thích tiếng Việt (`describe`). |

`MlScores` và `MlClient` nằm trong `server/` nhưng thuộc vai này; chỗ gọi (`ExamServer.scoreRoomNow`, `ExamRoom.applyScores`) thuộc Server 1.

## 2. Luồng chạy

**Từ `FEATURES` đến cột "Bất thường" và câu giải thích**

1. Mỗi chu kỳ `featureIntervalSeconds` (20 giây trong `config/rules.json`) client gửi `FEATURES` (`window` và 6 số). `ClientHandler.handleFeatures` kiểm tra đúng 6 số rồi `ExamRoom.features` lưu vào `Session.samples`.
2. `ExamServer` có lịch cùng chu kỳ gọi `scoreIfRunning` → `scoreRoomNow`: `room.samplesForMl()` gom mẫu mọi sinh viên (thêm `studentId`).
3. `MlClient.score` gửi `POST http://localhost:8000/score` (không giữ khoá phòng trong lúc chờ).
4. `ml_service.score` → `score_samples`: dưới 20 mẫu **trong cả phòng** thì trả `not_enough_data`; ngược lại fit `IsolationForest`, điểm thô `-model.score_samples(x)`, chuẩn hoá min–max về 0–1, mỗi sinh viên lấy tối đa trong **3 cửa sổ gần nhất** (`RECENT`), `anomaly` theo quy tắc ở phần 4, và `find_reasons` chọn feature lệch nhất.
5. `ExamRoom.applyScores` → `MlScores.apply`: ghi `anomalyScore`, `anomaly`, `maxAnomaly`, `flaggedCount`, `reasons` vào `Session`; khi máy mới bị gắn cờ thì log và sự kiện `ANOMALY_ON` có chi tiết do `MlScores.describe` ghép: `Mức bất thường: Rất cao (1.00). Trong 1 phút gần nhất, máy này: rời khỏi bài thi 18 lần (các máy khác thường 1 lần) · ...`.
6. Giám thị thấy cột "Bất thường" và câu giải thích trong khung chi tiết/Lịch sử (Server 2 lo giao diện).

**Chạy thực nghiệm với máy giả:** `uvicorn ml_service:app` + server + `python simulate_clients.py --count 5 --cheater` (thêm `--interval 3` để thấy kết quả nhanh): `SimClient.handle` phản ứng từng loại tin, `feature_loop` gửi `FEATURES`.

## 3. Thầy bảo sửa gì → mở đâu, đổi gì

| Thầy bảo | File → hàm | Đổi gì |
|---|---|---|
| Đổi ngưỡng gắn cờ | `ml_service.FLAG_MIN_SCORE` | `0.7` thành `0.8`: ít máy bị gắn cờ hơn nhưng dễ bỏ sót hơn. |
| Đổi số cửa sổ gần nhất | `ml_service.RECENT` | `3` thành `5`; sửa `test_uses_three_latest_windows` (cửa sổ lệch phải nằm ngoài 5 cửa sổ gần nhất). |
| Đổi số mẫu tối thiểu | `ml_service.MIN_SAMPLES` và `MlScores.ML_MIN_SAMPLES` | Phải khớp nhau (log "chưa đủ dữ liệu (n/20 mẫu)" lấy số này). |
| Thêm feature thứ 7 | `Monitor.buildFeatures` (Client), `ClientHandler.isSixNumbers` (Server 1), `ml_service.Sample.values`, `simulate_clients.py`, `MlScores.REASON_FORMATS` | Đổi độ dài 6 thành 7 ở mọi nơi, thêm câu mô tả feature mới trong `REASON_FORMATS`, cập nhật dữ liệu mẫu trong test. |
| Đổi số lý do hoặc ngưỡng độ lệch | `ml_service.MAX_REASONS`, `MIN_DEVIATION` | `3` và `1` (độ lệch chuẩn hoá `(value - room) / (std + 1e-6)`). |
| Đổi cách viết câu giải thích | `MlScores.REASON_FORMATS`, `describe`, `level` | Sửa mẫu câu từng feature, ngưỡng `Rất cao ≥ 0.9`, `Cao ≥ 0.7`; sửa `MlScoresTest`. |
| Đổi chu kỳ chấm | `config/rules.json` → `featureIntervalSeconds` | 10 đến 60; ML chấm cùng chu kỳ. Chuỗi "Trong ... gần nhất" tự tính theo chu kỳ. |
| Thay thuật toán (vd LOF, z-score) | `ml_service.score_samples` | Thay đoạn tạo `raw`; giữ nguyên dạng trả về `{studentId, score, anomaly, reasons}` thì Java không phải đổi. |
| Đổi địa chỉ service ML | `ExamServer.main` (`new MlClient("http://localhost:8000")`) | Đổi URL; `MlClientTest` dùng server giả. |
| Cho ML tự đình chỉ | Không làm | Quy tắc của nhóm: ML chỉ cảnh báo; chỉ vi phạm luật rõ ràng mới bị đình chỉ. |

## 4. Câu hỏi thường gặp

**1. Ý tưởng Isolation Forest?** Dựng nhiều cây ngẫu nhiên, mỗi lần cắt chọn ngẫu nhiên một feature và một ngưỡng. Điểm "lạ" bị cô lập sau **ít lần cắt**, điểm giống số đông cần cắt nhiều lần. Đường đi trung bình càng ngắn thì càng bất thường.

**2. Vì sao không cần dataset?** Học không giám sát: mỗi chu kỳ fit lại ngay trên dữ liệu của chính buổi thi và so các máy với nhau. Không có nhãn "gian lận" nào để học.

**3. Chuẩn hoá min–max để làm gì?** `score_samples` của sklearn càng âm càng bất thường; ta đổi dấu rồi đưa về 0 đến 1: 1 là máy khác biệt nhất phòng, 0 là giống số đông nhất. Mọi máy giống hệt nhau thì mọi điểm bằng 0.

**4. Vì sao `anomaly` có thêm ngưỡng 0.7?** Feature là số đếm nhỏ, nhiều mẫu trùng nhau nên `predict() == -1` mặc định gắn cờ quá nhiều máy bình thường (25 đến 88% trong thí nghiệm giả lập). Cờ chỉ bật khi `predict == -1` **và** điểm ≥ `FLAG_MIN_SCORE`; đo lại thì còn khoảng 0 đến 1% máy bình thường bị cờ, vẫn bắt 97 đến 100% máy lệch.

**5. Vì sao máy thật chưa có điểm lúc đầu?** Ngưỡng 20 mẫu tính trên cả phòng và mỗi máy gửi 1 mẫu mỗi chu kỳ, nên phòng ít máy phải đợi lâu. Đây không phải lỗi; log `ML: chưa đủ dữ liệu (n/20 mẫu)` cho biết đang ở đâu. Phòng đông hoặc chạy thêm `simulate_clients.py` thì đủ sớm hơn.

**6. Vì sao chỉ xét 3 cửa sổ gần nhất?** Hành vi cũ không nên kéo dài mãi; máy chỉ lệch ở đầu giờ rồi bình thường lại thì không còn bị cờ.

**7. Vì sao tách ML thành service Python gọi qua HTTP?** scikit-learn có sẵn ở Python, Java không có Isolation Forest. Tách ra thì hai bên độc lập, service tắt hay lỗi chỉ làm cột "Bất thường" đứng yên (`ML lỗi` trong log), server thi vẫn chạy. Lời gọi HTTP nằm ngoài khối `synchronized` để ML chậm không treo phòng.

**8. Vì sao giao diện ghi "Bất thường", không ghi "Gian lận", và vì sao ML không đình chỉ?** Mô hình chỉ biết máy khác số đông (có thể do học khác cách), không phải bằng chứng. Vi phạm luật (mở app cấm, vào tên miền cấm, cắm USB) là bằng chứng rõ ràng nên mới được tự động đình chỉ; ML chỉ giúp giám thị biết nên nhìn máy nào, và `reasons` cho biết máy đó lạ ở đâu.
