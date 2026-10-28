# Service ML: Isolation Forest tìm máy có hành vi khác số đông, fit lại trên chính dữ liệu của buổi thi.
import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel, Field
from sklearn.ensemble import IsolationForest

MIN_SAMPLES = 20  # ít hơn thì chưa đủ cơ sở để so với số đông
RECENT = 3        # chỉ xét 3 cửa sổ gần nhất của mỗi sinh viên
# predict() của Isolation Forest (contamination mặc định) gắn -1 cho đa số mẫu bình thường khi feature là số đếm
# nhỏ nên chỉ đánh dấu anomaly khi điểm chuẩn hoá cũng thuộc dải cao nhất phòng. Đo trên dữ liệu giả lập:
# không có điều kiện này 25-88% sinh viên bình thường bị đánh dấu, có thì ~0-1%, vẫn bắt được 97-100% máy lệch.
FLAG_MIN_SCORE = 0.7
MIN_HIGH_WINDOWS = 2  # máy chỉ bị anomaly khi có ít nhất 2 trong 3 chu kỳ gần nhất đều "cao"
MAX_REASONS = 3   # mỗi máy chỉ nêu tối đa 3 feature lệch nhất
MIN_DEVIATION = 1  # feature phải lệch hơn 1 độ lệch chuẩn của phòng mới được nêu


class Sample(BaseModel):
    studentId: str
    window: int
    values: list[float] = Field(min_length=6, max_length=6)


class ScoreRequest(BaseModel):
    samples: list[Sample]


def find_reasons(x: np.ndarray, latest: list[int]) -> list[dict]:
    """Các feature mà máy này lệch nhiều nhất so với phòng, lệch càng nhiều càng đứng trước."""
    recent = x[latest]
    value = recent.mean(axis=0)         # trung bình feature trong các mẫu gần nhất của máy
    room = np.median(x, axis=0)         # trung vị feature trên toàn bộ mẫu của phòng
    deviation = (value - room) / (x.std(axis=0) + 1e-6)

    reasons = []
    for feature in np.argsort(-deviation)[:MAX_REASONS]:
        if deviation[feature] <= MIN_DEVIATION:
            break
        reasons.append({
            "feature": int(feature),
            "value": round(float(value[feature]), 3),
            "room": round(float(room[feature]), 3),
            "total": round(float(recent[:, feature].sum()), 3),             # tổng trong các mẫu gần nhất
            "room_total": round(float(room[feature]) * len(latest), 3),     # phòng thường có bấy nhiêu trong cùng số mẫu
            "windows": len(latest),
        })
    return reasons


def score_samples(samples: list[Sample]) -> dict:
    """Hàm thuần (không cần HTTP). Điểm càng gần 1 càng khác số đông, không có nghĩa là gian lận."""
    if len(samples) < MIN_SAMPLES:
        return {"status": "not_enough_data", "results": []}

    x = np.array([s.values for s in samples])
    model = IsolationForest(n_estimators=100, random_state=42).fit(x)
    raw = -model.score_samples(x)  # score_samples âm và càng thấp càng bất thường, nên đổi dấu
    low, high = raw.min(), raw.max()
    if high - low < 1e-9:  # mọi máy hành xử giống hệt nhau thì không có gì để so
        scores = np.zeros(len(samples))
        flagged = np.zeros(len(samples), dtype=bool)
    else:
        scores = (raw - low) / (high - low)
        flagged = model.predict(x) == -1

    by_student: dict[str, list[int]] = {}
    for i, s in enumerate(samples):
        by_student.setdefault(s.studentId, []).append(i)

    results = []
    for student_id, indexes in by_student.items():
        latest = sorted(indexes, key=lambda i: samples[i].window)[-RECENT:]
        # một chu kỳ "cao" khi bị gắn cờ và điểm đủ lớn; một chu kỳ lẻ là nhiễu nên không đủ để kết luận
        high_windows = sum(1 for i in latest if flagged[i] and scores[i] >= FLAG_MIN_SCORE)
        reasons = find_reasons(x, latest)
        results.append({
            "studentId": student_id,
            "score": round(float(max(scores[i] for i in latest)), 3),
            "anomaly": high_windows >= MIN_HIGH_WINDOWS and len(reasons) > 0,   # không có lý do thì không gắn cờ
            "reasons": reasons,
        })
    return {"status": "ok", "results": results}


app = FastAPI()


@app.post("/score")
def score(req: ScoreRequest) -> dict:
    return score_samples(req.samples)
