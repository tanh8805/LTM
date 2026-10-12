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


class Sample(BaseModel):
    studentId: str
    window: int
    values: list[float] = Field(min_length=6, max_length=6)


class ScoreRequest(BaseModel):
    samples: list[Sample]


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
        results.append({
            "studentId": student_id,
            "score": round(float(max(scores[i] for i in latest)), 3),
            "anomaly": bool(any(flagged[i] and scores[i] >= FLAG_MIN_SCORE for i in latest)),
        })
    return {"status": "ok", "results": results}


app = FastAPI()


@app.post("/score")
def score(req: ScoreRequest) -> dict:
    return score_samples(req.samples)
