<!-- Owner: Nguoi4 -->
# ml-service

Dịch vụ ML cho Server: dự báo chuỗi thời gian bằng **Chronos-Bolt tiny** (`amazon/chronos-bolt-tiny`, CPU) qua FastAPI.
Server (Java) gọi `POST /score` **một lần mỗi chu kỳ** với 60 điểm gần nhất của **mọi máy** (batch), timeout 3 giây.

## Cài đặt và chạy

Cần Python 3.10+.

```bash
cd ml-service
python -m venv .venv
source .venv/bin/activate                  # Windows: .venv\Scripts\activate
pip install -r requirements.txt --extra-index-url https://download.pytorch.org/whl/cpu
uvicorn app:app                            # cổng 8000
```

Lần chạy đầu cần Internet để tải model từ HuggingFace (khoảng vài chục MB). Để dùng offline:
tải sẵn thư mục model về máy rồi `LTM_ML_MODEL=/duong/dan/chronos-bolt-tiny uvicorn app:app`.

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `LTM_ML_BACKEND` | `chronos` | `chronos` (thật) hoặc `naive` (trung vị + MAD, **chỉ để dev/test** khi không tải được model) |
| `LTM_ML_MODEL` | `amazon/chronos-bolt-tiny` | Tên model trên HuggingFace hoặc đường dẫn thư mục local |

## API

| Method | Đường dẫn | Ý nghĩa |
|---|---|---|
| GET | `/health` | Service còn sống: `{"status":"ok"}` (kể cả khi model chưa nạp được) |
| GET | `/model` | Trạng thái model: `backend`, `model`, `loaded`, `error` |
| POST | `/score` | Chấm điểm một batch gồm mọi máy |

`POST /score`:

```json
{"predictionLength": 1,
 "machines": [{"machineId": "SV001",
               "series": {"kbSent": [1.0, 2.0, 3.0], "kbReceived": [...], "connectionCount": [...],
                          "distinctDestinationCount": [...], "cpuPercent": [...], "ramPercent": [...]}}]}
```

Trả về (cho mỗi máy, mỗi metric): phân vị dự báo của bước kế tiếp. Server so giá trị thật với khoảng `q10 - q90`.

```json
{"backend": "chronos", "model": "amazon/chronos-bolt-tiny",
 "results": [{"machineId": "SV001", "forecast": {"kbSent": {"q10": 0.5, "q50": 1.0, "q90": 2.0}, "...": {}}}]}
```

- Chỉ 6 metric được chấm: `kbSent`, `kbReceived`, `connectionCount`, `distinctDestinationCount`, `cpuPercent`, `ramPercent`
  (không gồm số tiến trình và số lần mất focus). Metric khác trong request bị bỏ qua.
- Chuỗi ngắn hơn 3 điểm không dự báo được: metric đó vắng mặt trong `forecast`.
- **Model không nạp được** (không có Internet, thiếu file...): service vẫn chạy; `/score` trả **HTTP 503**
  (`{"error":"model_unavailable"}`) và Server Java tự bỏ qua ML (fallback), hệ thống thi vẫn bình thường.
- Mọi chuỗi của mọi máy được gom vào **cùng một lần gọi model** (nhóm theo số patch 16 điểm để tương thích
  `chronos-forecasting` 2.x với `transformers` 5.x).

## Test

```bash
pip install -r requirements.txt -r requirements-dev.txt --extra-index-url https://download.pytorch.org/whl/cpu
cd ml-service && pytest
```

Test không cần Internet: dùng backend `naive`, forecaster giả, và pipeline Chronos với **trọng số khởi tạo ngẫu nhiên**
(chỉ kiểm tra cách gọi API, batch và shape, không kiểm tra chất lượng dự báo).
