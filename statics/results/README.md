# Kết quả thực nghiệm

Tất cả số liệu ở đây sinh từ **trace mô phỏng** `data/traces/sim.csv` (do `exam.tools.Simulator` tạo, seed cố định,
10 máy x 120 mẫu, 32 dòng gian lận). Chưa phải số liệu đo trong phòng thi thật.

| File | Nội dung | Độ tin cậy |
|---|---|---|
| `experiment-if.csv` | `NONE` và `IF` (Isolation Forest tự cài, đúng luật 30 mẫu học, threshold = max + 0.05, 3 lần liên tiếp) | Thật |
| `experiment-all-dev-naive-backend.csv` | Thêm `CHRONOS`, `BOTH_OR`, `BOTH_AND` | **Chỉ để kiểm thử đường ống.** Cột Chronos lấy từ backend `naive` (trung vị + MAD) của ml-service, KHÔNG phải Chronos-Bolt thật |

## Vì sao không có số liệu Chronos thật

Sandbox chạy phiên phát triển chặn `huggingface.co` (HTTP 403) nên không tải được trọng số `amazon/chronos-bolt-tiny`.
Muốn có số liệu thật: cho phép `huggingface.co` (hoặc đặt `LTM_ML_MODEL` trỏ tới thư mục model đã tải sẵn),
chạy ml-service **không** đặt `LTM_ML_BACKEND=naive`, rồi chạy lại:

```text
java -cp "source/tools/target/tools.jar:source/tools/target/lib/*" exam.tools.ExperimentRunner \
     --trace data/traces/sim.csv --ml-url http://localhost:8000 --out statics/results/experiment.csv
```

## Đọc kết quả Isolation Forest

```text
mode   precision recall   F1    FPR   windows delay(mau)
IF         1.000  0.031  0.061  0.000   1/3     7.0
```

- Precision 1.0 và FPR 0: IF gần như không báo oan.
- Recall chỉ 0.03: với luật của đề (học 30 mẫu, ngưỡng = điểm cao nhất lúc học + 0.05, phải vượt 3 lần liên tiếp),
  điểm Isolation Forest bão hòa quanh 0.63 dù giá trị lệch rất xa, trong khi ngưỡng khoảng 0.61-0.63, nên đa số đợt gian lận không vượt ngưỡng.
- Đây là hành vi của thuật toán + luật ngưỡng, không phải lỗi cài đặt: thử với đợt tăng đột biến gấp ~900 lần, IF vẫn chỉ phát hiện được khoảng 24-62% số lần chạy (các test của `IsolationForest` trong `common` vẫn xác nhận điểm của điểm lạ cao hơn điểm bình thường).
- Hệ quả cho việc chọn `ml.mode`: IF một mình bỏ sót nhiều; ghép `BOTH_OR` có thể bù recall nhưng phải kiểm lại bằng Chronos thật.
  Vì vậy `ml.mode` mặc định vẫn là `NONE` cho tới khi Nguoi4 có số liệu Chronos thật (`TODO(Nguoi4)` trong `config/ml.properties`).
