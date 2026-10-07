# Owner: Nguoi4
"""
ml-service: dịch vụ chấm điểm bất thường cho Server (Chronos-Bolt tiny, chạy CPU).

HIỆN LÀ STUB: /score trả dữ liệu GIẢ CỐ ĐỊNH, chưa chạy Chronos thật.

Chạy:
    cd ml-service
    uvicorn app:app            # mặc định cổng 8000

Server (Java) gọi POST /score MỘT lần mỗi 10 giây với dữ liệu của MỌI máy (batch), timeout 3 giây.
"""

from typing import Dict, List

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="LTM ML service (stub)")

# 6 metric Chronos chấm điểm. Không gồm số tiến trình và số lần mất focus.
METRIC_NAMES = [
    "kbSent",
    "kbReceived",
    "connectionCount",
    "distinctDestinationCount",
    "cpuPercent",
    "ramPercent",
]


class MachineSeries(BaseModel):
    machineId: str
    # tên metric -> tối đa 60 giá trị gần nhất, cũ nhất ở đầu
    series: Dict[str, List[float]]


class ScoreRequest(BaseModel):
    machines: List[MachineSeries]


class Quantiles(BaseModel):
    q10: float
    q50: float
    q90: float


class MachineForecast(BaseModel):
    machineId: str
    # tên metric -> dự báo cho bước kế tiếp. Server so giá trị thật với khoảng q10 - q90.
    forecast: Dict[str, Quantiles]


class ScoreResponse(BaseModel):
    stub: bool
    results: List[MachineForecast]


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest):
    # TODO(Nguoi4): Nạp amazon/chronos-bolt-tiny (chronos-forecasting) một lần khi khởi động,
    #  gom series của mọi máy thành một batch, dự báo bước kế tiếp và trả q10, q50, q90 thật.
    fake_quantiles = Quantiles(q10=0.0, q50=5.0, q90=10.0)

    results = []
    for machine in request.machines:
        forecast = {name: fake_quantiles for name in METRIC_NAMES}
        results.append(MachineForecast(machineId=machine.machineId, forecast=forecast))

    return ScoreResponse(stub=True, results=results)
