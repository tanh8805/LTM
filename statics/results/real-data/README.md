# Thực nghiệm trên dữ liệu đo thật (OSHI)

Dữ liệu: `data/traces/` (15 trace, 1146 mẫu, mẫu 10 giây, máy: container cloud 4 CPU/16 GB). Mỗi trace có `<tên>.csv` (số liệu, không nhãn),
`<tên>.meta.json` và `<tên>.events.json` (ground truth: khoảng thời gian workload thật sự chạy). Chia tập cố định trước khi thu: `data/traces/splits.json`
(validation = batch B, test = batch A). Các kịch bản CPU/network/memory/process/mixed là **system anomaly** có kiểm soát, KHÔNG phải gian lận;
`application_01` là hoạt động bình thường (benign).

Thu thập: `scripts/dataset/run_collection.py`. Phân tích: `scripts/dataset/analyze.py`, `diagnose.py`. Chronos: Chronos-Bolt tiny thật (CPU).

## Nhật ký thí nghiệm (không bỏ thí nghiệm nào)
- `experiment_log.csv`: các lần chạy ExperimentRunner (Java): E00 mặc định validation; E01/E02 window 30/20; E03/E04 số mẫu học 20/45 (E04 không hợp lệ: đợt anomaly đầu rơi vào giai đoạn học); E10 test mặc định; E11 focus_01.
- `val/calibration_log.csv`: V001–V031, đổi MỘT yếu tố mỗi lần trên validation. `val/chosen_config.json`: cấu hình chọn theo tiêu chí khai báo trước (F1 cao nhất với FPR <= 2% trên validation) = IF với margin 0.0.
- `test/calibration_log.csv`: đánh giá test đúng một lần, cấu hình mặc định và cấu hình đã chọn.
- `*_scores.csv`: điểm IF và dự báo q0.1/q0.5/q0.9 của Chronos từng mẫu. `describe_*.csv`: phân phối điểm, AUROC, độ phủ dải Chronos, phản ứng của metric.

## Kết quả test (9 trace, không gồm focus_01; 414 mẫu đánh giá: 90 anomaly, 18 benign, 306 normal)

| cấu hình | mode | precision | recall | F1 | FPR | đợt báo nhầm | đoạn bắt được |
|---|---|---|---|---|---|---|---|
| mặc định | IF | 1.000 | 0.089 | 0.163 | 0.000 | 0 | 2/10 |
| mặc định | CHRONOS | 0.372 | 0.178 | 0.241 | 0.083 | 16 | 7/10 |
| mặc định | BOTH_OR | 0.449 | 0.244 | 0.317 | 0.083 | 16 | 7/10 |
| mặc định | BOTH_AND | 1.000 | 0.022 | 0.043 | 0.000 | 0 | 2/10 |
| IF margin 0.0 | IF | 0.935 | 0.322 | 0.479 | 0.006 | 2 | 5/10 |
| IF margin 0.0 | BOTH_OR | 0.586 | 0.456 | 0.513 | 0.090 | 18 | 9/10 |
| IF margin 0.0 | BOTH_AND | 1.000 | 0.044 | 0.085 | 0.000 | 0 | 3/10 |

## Kết luận và giới hạn
- **Chưa đủ bằng chứng để chọn `ml.mode`** (mặc định vẫn `NONE`): chỉ 10 đoạn test, F1 của IF margin 0.0 là 0.175 trên validation nhưng 0.479 trên test, một máy/một môi trường, không có gian lận thật.
- Chronos: dự báo một bước tự thích nghi với dịch chuyển kéo dài nên chỉ lệch ở 1-2 mẫu đầu, luật "3 lần liên tiếp" gần như không đạt; dải q0.1-q0.9 chỉ phủ 80% nên báo nhầm cấu trúc (khoảng 18 đợt/giờ/sinh viên); không nhìn processCount và focus theo thiết kế.
- Isolation Forest: không ngoại suy được, điểm không vượt hẳn điểm mẫu cực đoan lúc học nên ngưỡng max + 0.05 làm nó gần như mù; chiều hằng trong 30 mẫu học không chia được.
- Thao tác git của tác giả lúc 02:29Z lọt vào đoạn học của `normal_01` (mẫu 12-14); xem `git_ops_during_collection_utc.txt`.
- Chi tiết đầy đủ: báo cáo trong cuộc trò chuyện kèm các file CSV ở đây.
