<!-- Owner: Nguoi4 -->
# ml-service

Dịch vụ ML cho Server (Python 3.10+, FastAPI, Chronos-Bolt tiny trên CPU). **Hiện là stub**: `/score` trả dữ liệu giả cố định.

## Chạy

```bash
cd ml-service
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app
```

## API

| Method | Đường dẫn | Ý nghĩa |
|---|---|---|
| GET | `/health` | Kiểm tra dịch vụ sống: `{"status":"ok"}` |
| POST | `/score` | Chấm điểm một batch gồm mọi máy. Hiện trả dữ liệu giả cố định |

Ví dụ:

```bash
curl http://localhost:8000/health
curl -X POST http://localhost:8000/score -H "Content-Type: application/json" \
     -d '{"machines":[{"machineId":"SV001","series":{"kbSent":[1,2,3]}}]}'
```
