# Kết quả thực nghiệm

Tất cả số liệu ở đây sinh từ **trace mô phỏng** `data/traces/sim.csv` (do `exam.tools.Simulator` tạo, seed cố định,
10 máy x 120 mẫu, 32 dòng gian lận). Chưa phải số liệu đo trong phòng thi thật.

| File | Nội dung | Độ tin cậy |
|---|---|---|
| `experiment-if.csv` | `NONE` và `IF` (Isolation Forest tự cài, đúng luật 30 mẫu học, threshold = max + 0.05, 3 lần liên tiếp) | Thật |
| `experiment-all-real-chronos.csv` | `NONE`, `IF`, `CHRONOS`, `BOTH_OR`, `BOTH_AND` với **Chronos-Bolt tiny thật** (trọng số `amazon/chronos-bolt-tiny`, CPU) | Thật, nhưng trên trace **mô phỏng** |
| `experiment-all-dev-naive-backend.csv` | Như trên nhưng cột Chronos lấy từ backend `naive` (trung vị + MAD) | **Chỉ để kiểm thử đường ống**, KHÔNG phải Chronos-Bolt |

## Kết quả với Chronos-Bolt thật (trace mô phỏng)

```text
mode      precision  recall      F1     FPR   windows delay(mau) time(ms)
NONE          0.000   0.000   0.000   0.000    0/3             -        0
IF            1.000   0.031   0.061   0.000    1/3           7.0      101
CHRONOS       0.024   0.031   0.027   0.047    1/3           1.0     7000
BOTH_OR       0.047   0.063   0.053   0.047    1/3           1.0     7101
BOTH_AND      0.000   0.000   0.000   0.000    0/3             -     7101
```

- Chronos chạy đúng (dự báo hợp lý: với chuỗi trung bình 50 thì q50 ≈ 49.4), nhưng trên trace này **phát hiện kém**: recall 0.03 và FPR 4.7%, tức báo oan nhiều hơn bắt đúng.
- Lý do dễ thấy: trace mô phỏng là nhiễu ngẫu nhiên độc lập, còn luật yêu cầu giá trị thật ngoài q0.1–q0.9 **3 lần liên tiếp**; khoảng q0.1–q0.9 của Chronos hẹp nên nhiễu bình thường cũng hay vượt, còn đợt gian lận ngắn thì khó đủ 3 lần liên tiếp.
- Không mode nào cho recall tốt. `IF` có F1 cao nhất và FPR = 0 nên an toàn nhất khi báo giáo viên; `BOTH_OR` không cải thiện đáng kể.
- **Chưa kết luận được** cho phòng thi thật vì chưa có trace thật. Cần chạy `Recorder` trên máy thật + một vài hành vi gian lận thật rồi chạy lại.

## Cách chạy lại

```text
LTM_ML_MODEL=/duong/dan/chronos-bolt-tiny uvicorn app:app --port 8000   # trong ml-service/
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
  Vì vậy `ml.mode` mặc định vẫn là `NONE` cho tới khi nhóm có trace thật và chọn mode (`TODO(Nguoi4)` trong `config/ml.properties`).
