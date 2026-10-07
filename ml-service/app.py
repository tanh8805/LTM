# Owner: Nguoi4
"""
ml-service: dịch vụ chấm điểm bất thường cho Server (Chronos-Bolt tiny, chạy CPU).

Chạy:
    cd ml-service
    uvicorn app:app            # mặc định cổng 8000

Server (Java) gọi POST /score MỘT lần mỗi chu kỳ với dữ liệu của MỌI máy (batch), timeout 3 giây.
Với mỗi máy và mỗi metric, ml-service trả dự báo q0.1 / q0.5 / q0.9 cho bước kế tiếp; Server so giá trị thật
với khoảng đó (xem docs/SPEC.md mục 9).

Nếu model không tải được, /health vẫn trả {"status":"ok"} còn /score trả HTTP 503: Server tự bỏ qua ML.
"""

import math
from typing import Dict, List, Optional

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from forecaster import Forecaster, ForecastError, MIN_POINTS, build_forecaster, clean_series

# 6 metric Chronos chấm. Không gồm số tiến trình và số lần mất focus.
METRIC_NAMES = [
    "kbSent",
    "kbReceived",
    "connectionCount",
    "distinctDestinationCount",
    "cpuPercent",
    "ramPercent",
]

MAX_MACHINES = 1000
MAX_POINTS_PER_SERIES = 2048

app = FastAPI(title="LTM ML service")
# Tạo forecaster khi nạp module (nạp model ở thread nền nên không chặn việc khởi động).
app.state.forecaster = build_forecaster()


class MachineSeries(BaseModel):
    machineId: str = Field(min_length=1, max_length=64)
    # tên metric -> các giá trị gần nhất (tối đa 2048), cũ nhất ở đầu
    series: Dict[str, List[float]]


class ScoreRequest(BaseModel):
    predictionLength: int = 1
    machines: List[MachineSeries] = Field(max_length=MAX_MACHINES)


class Quantiles(BaseModel):
    q10: float
    q50: float
    q90: float


class MachineForecast(BaseModel):
    machineId: str
    # tên metric -> dự báo cho bước kế tiếp. Metric thiếu dữ liệu (quá ngắn) không có trong forecast.
    forecast: Dict[str, Quantiles]


class ScoreResponse(BaseModel):
    backend: str
    model: str
    results: List[MachineForecast]


def get_forecaster() -> Forecaster:
    return app.state.forecaster


@app.get("/health")
def health():
    """Service còn sống (không nói gì về việc model đã nạp xong chưa: xem /model)."""
    return {"status": "ok"}


@app.get("/model")
def model_status():
    """Trạng thái model: đã nạp chưa, nếu lỗi thì lỗi gì."""
    return get_forecaster().status()


@app.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest):
    forecaster = get_forecaster()
    if not forecaster.is_ready():
        status = forecaster.status()
        return JSONResponse(status_code=503,
                            content={"error": "model_unavailable", "detail": status.get("error")})

    # Gom MỌI chuỗi của MỌI máy vào một danh sách: model chạy theo batch, không gọi từng metric.
    slots = []        # (chỉ số máy, tên metric) tương ứng với từng chuỗi
    series_list = []
    for machine_index, machine in enumerate(request.machines):
        for metric in METRIC_NAMES:
            values = machine.series.get(metric)
            if values is None or len(values) > MAX_POINTS_PER_SERIES:
                continue
            cleaned = clean_series(values)
            if len(cleaned) >= MIN_POINTS:
                slots.append((machine_index, metric))
                series_list.append(cleaned)

    try:
        predictions = forecaster.predict(series_list) if series_list else []
    except ForecastError as error:
        return JSONResponse(status_code=503, content={"error": "forecast_failed", "detail": str(error)})

    forecasts: List[Dict[str, Quantiles]] = [dict() for _ in request.machines]
    for (machine_index, metric), prediction in zip(slots, predictions):
        if prediction is not None and all(math.isfinite(x) for x in prediction):
            forecasts[machine_index][metric] = Quantiles(q10=prediction[0], q50=prediction[1], q90=prediction[2])

    results = [MachineForecast(machineId=machine.machineId, forecast=forecasts[i])
               for i, machine in enumerate(request.machines)]
    return ScoreResponse(backend=forecaster.backend, model=forecaster.model_name, results=results)
