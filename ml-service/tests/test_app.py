# Owner: Nguoi4
"""
Test ml-service. Chạy:  cd ml-service && pytest

Test dùng LTM_ML_BACKEND=naive hoặc forecaster giả để không cần tải model từ Internet.
Test với Chronos thật dùng model khởi tạo ngẫu nhiên (chỉ để kiểm tra đúng cách gọi API và shape, KHÔNG kiểm tra chất lượng dự báo).
"""

import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
os.environ["LTM_ML_BACKEND"] = "naive"  # đặt trước khi import app

from fastapi.testclient import TestClient  # noqa: E402

import app as app_module  # noqa: E402
from forecaster import ChronosForecaster, ForecastError, Forecaster, NaiveForecaster  # noqa: E402

client = TestClient(app_module.app)

METRICS = ["kbSent", "kbReceived", "connectionCount", "distinctDestinationCount", "cpuPercent", "ramPercent"]


def machine(machine_id, length=20, value=10.0, extra=None):
    series = {name: [value] * length for name in METRICS}
    series.update(extra or {})
    return {"machineId": machine_id, "series": series}


class RecordingForecaster(Forecaster):
    """Forecaster giả: ghi lại các batch được gọi, trả (v-1, v, v+1) theo giá trị cuối của chuỗi."""

    backend = "fake"
    model_name = "recording"

    def __init__(self):
        self.calls = []

    def is_ready(self):
        return True

    def status(self):
        return {"backend": "fake", "model": "recording", "loaded": True, "error": None}

    def predict(self, series_list):
        self.calls.append([list(s) for s in series_list])
        return [[s[-1] - 1, s[-1], s[-1] + 1] for s in series_list]


@pytest.fixture(autouse=True)
def restore_forecaster():
    original = app_module.app.state.forecaster
    yield
    app_module.app.state.forecaster = original


# ---------------------------------------------------------------- health, model


def test_health_returns_ok():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_model_status_reports_backend():
    body = client.get("/model").json()

    assert body["backend"] == "naive"
    assert body["loaded"] is True


# ---------------------------------------------------------------- /score: batch


def test_score_returns_forecast_for_every_machine_and_metric():
    body = client.post("/score", json={"machines": [machine("SV001"), machine("SV002")]}).json()

    assert [r["machineId"] for r in body["results"]] == ["SV001", "SV002"]
    for result in body["results"]:
        assert sorted(result["forecast"].keys()) == sorted(METRICS)
        for quantiles in result["forecast"].values():
            assert quantiles["q10"] <= quantiles["q50"] <= quantiles["q90"]


def test_score_puts_every_series_of_every_machine_in_one_forecast_call():
    fake = RecordingForecaster()
    app_module.app.state.forecaster = fake

    response = client.post("/score", json={"machines": [machine("SV001", 12, 1.0), machine("SV002", 12, 2.0),
                                                        machine("SV003", 12, 3.0)]})

    assert response.status_code == 200
    assert len(fake.calls) == 1, "phải chỉ có MỘT lần gọi model cho cả batch"
    assert len(fake.calls[0]) == 3 * 6, "3 máy x 6 metric trong cùng một batch"


def test_score_maps_predictions_back_to_the_right_machine_and_metric():
    fake = RecordingForecaster()
    app_module.app.state.forecaster = fake
    first = machine("SV001", 12, 1.0)
    first["series"]["kbSent"] = [7.0] * 12
    second = machine("SV002", 12, 5.0)

    body = client.post("/score", json={"machines": [first, second]}).json()

    assert body["backend"] == "fake"
    assert body["results"][0]["forecast"]["kbSent"] == {"q10": 6.0, "q50": 7.0, "q90": 8.0}
    assert body["results"][0]["forecast"]["cpuPercent"]["q50"] == 1.0
    assert body["results"][1]["forecast"]["kbSent"]["q50"] == 5.0


def test_score_ignores_metrics_chronos_does_not_use():
    fake = RecordingForecaster()
    app_module.app.state.forecaster = fake
    extra = {"processCount": [100.0] * 12, "focusLostCount": [0.0] * 12}

    body = client.post("/score", json={"machines": [machine("SV001", 12, extra=extra)]}).json()

    assert "processCount" not in body["results"][0]["forecast"]
    assert "focusLostCount" not in body["results"][0]["forecast"]
    assert len(fake.calls[0]) == 6


def test_score_skips_series_that_are_too_short():
    body = client.post("/score", json={"machines": [machine("SV001", 2)]}).json()

    assert body["results"][0]["forecast"] == {}


def test_null_inside_a_series_is_rejected_with_422():
    dirty = machine("SV001", 5, 3.0)
    dirty["series"]["cpuPercent"] = [1.0, None, 3.0, 4.0, 5.0]

    assert client.post("/score", json={"machines": [dirty]}).status_code == 422


def test_clean_series_drops_nan_and_infinite_values():
    from forecaster import clean_series

    assert clean_series([1.0, float("nan"), 2.0, float("inf"), float("-inf"), 3.0]) == [1.0, 2.0, 3.0]


def test_score_with_no_machines_returns_empty_results():
    body = client.post("/score", json={"machines": []}).json()

    assert body["results"] == []


# ---------------------------------------------------------------- /score: lỗi


def test_invalid_body_is_422():
    assert client.post("/score", json={"bad": 1}).status_code == 422
    assert client.post("/score", json={"machines": [{"machineId": "SV001"}]}).status_code == 422
    assert client.post("/score", content="not json", headers={"Content-Type": "application/json"}).status_code == 422


def test_score_returns_503_when_model_is_not_loaded_but_health_stays_ok():
    broken = ChronosForecaster("model-khong-ton-tai")
    broken._error = "OSError: không tải được model (giả lập)"
    app_module.app.state.forecaster = broken

    response = client.post("/score", json={"machines": [machine("SV001")]})

    assert response.status_code == 503
    assert response.json()["error"] == "model_unavailable"
    assert client.get("/health").json() == {"status": "ok"}
    status = client.get("/model").json()
    assert status["loaded"] is False
    assert "giả lập" in status["error"]


def test_score_returns_503_when_forecast_itself_fails():
    class Exploding(RecordingForecaster):
        def predict(self, series_list):
            raise ForecastError("boom")

    app_module.app.state.forecaster = Exploding()

    response = client.post("/score", json={"machines": [machine("SV001")]})

    assert response.status_code == 503
    assert response.json()["error"] == "forecast_failed"


def test_chronos_load_failure_does_not_crash_the_service():
    forecaster = ChronosForecaster("thu-muc-model-khong-ton-tai-12345")
    forecaster._load()  # chạy đồng bộ: không có Internet/không có thư mục -> phải ghi lỗi chứ không ném exception

    assert forecaster.is_ready() is False
    assert forecaster.status()["error"] is not None


# ---------------------------------------------------------------- naive backend


def test_naive_forecast_is_median_with_mad_band():
    forecaster = NaiveForecaster()

    result = forecaster.predict([[10.0, 10.0, 10.0, 10.0, 10.0], [1.0, 2.0, 3.0, 4.0, 100.0]])

    assert result[0] == [10.0, 10.0, 10.0]
    assert result[1][1] == 3.0
    assert result[1][0] < 3.0 < result[1][2]


# ---------------------------------------------------------------- Chronos thật (model ngẫu nhiên)


def build_random_chronos_pipeline():
    torch = pytest.importorskip("torch")
    pytest.importorskip("chronos")
    from chronos import ChronosBoltPipeline
    from chronos.chronos_bolt import ChronosBoltModelForForecasting
    from transformers import T5Config

    config = T5Config(d_model=32, d_kv=8, d_ff=64, num_heads=2, num_layers=1, dense_act_fn="relu",
                      feed_forward_proj="relu", is_encoder_decoder=False, vocab_size=2,
                      decoder_start_token_id=0, pad_token_id=0, eos_token_id=1)
    config.chronos_config = dict(context_length=2048, input_patch_size=16, input_patch_stride=16,
                                 prediction_length=64, quantiles=[0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9],
                                 use_reg_token=True)
    torch.manual_seed(0)
    return ChronosBoltPipeline(model=ChronosBoltModelForForecasting(config))


def test_real_chronos_pipeline_handles_a_batch_with_different_lengths():
    forecaster = ChronosForecaster(pipeline=build_random_chronos_pipeline())

    # Độ dài khác nhau (60, 20, 10, 33, 32) - đúng tình huống đầu ca thi khi các máy có số điểm khác nhau
    series_list = [[float(i) for i in range(n)] for n in (60, 20, 10, 33, 32)]
    result = forecaster.predict(series_list)

    assert len(result) == 5
    for quantiles in result:
        assert quantiles is not None and len(quantiles) == 3
        assert quantiles[0] <= quantiles[1] <= quantiles[2]


def test_real_chronos_pipeline_through_the_http_endpoint():
    app_module.app.state.forecaster = ChronosForecaster(pipeline=build_random_chronos_pipeline())

    response = client.post("/score", json={"machines": [machine("SV001", 60, 5.0), machine("SV002", 20, 8.0)]})

    assert response.status_code == 200
    body = response.json()
    assert body["backend"] == "chronos"
    assert sorted(body["results"][0]["forecast"].keys()) == sorted(METRICS)
    assert sorted(body["results"][1]["forecast"].keys()) == sorted(METRICS)


def test_real_chronos_short_series_are_skipped_not_fatal():
    forecaster = ChronosForecaster(pipeline=build_random_chronos_pipeline())

    result = forecaster.predict([[1.0, 2.0], [1.0, 2.0, 3.0, 4.0]])

    assert result[0] is None
    assert result[1] is not None
