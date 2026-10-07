# Owner: Nguoi4
"""
Dự báo chuỗi thời gian cho ml-service.

Có hai backend, chọn bằng biến môi trường LTM_ML_BACKEND:
  chronos (mặc định) : Chronos-Bolt tiny trên CPU (amazon/chronos-bolt-tiny). ĐÂY là backend dùng thật.
  naive              : dự báo đơn giản bằng trung vị + MAD. CHỈ để dev/test khi không tải được model
                       (ví dụ máy không có Internet). Kết quả không phải Chronos.

Nếu Chronos không tải được (không có Internet, thiếu thư viện...), ml-service VẪN CHẠY: /health vẫn trả ok,
còn /score trả HTTP 503 để Server Java bỏ qua ML (fallback). Server không bị ảnh hưởng.
"""

import math
import os
import threading
from typing import List, Optional, Sequence

# Các phân vị Server cần: q0.1, q0.5, q0.9
QUANTILE_LEVELS = [0.1, 0.5, 0.9]
# Chronos-Bolt cắt chuỗi thành các "patch" 16 điểm. Gom các chuỗi có cùng số patch vào một lần gọi model.
PATCH_SIZE = 16
# Cần tối thiểu bấy nhiêu điểm mới dự báo được.
MIN_POINTS = 3


class ForecastError(Exception):
    """Dự báo thất bại (model chưa sẵn sàng hoặc lỗi khi chạy)."""


def clean_series(values: Sequence[float]) -> List[float]:
    """Bỏ giá trị NaN/vô cực (số liệu hỏng) khỏi chuỗi."""
    return [float(v) for v in values if v is not None and math.isfinite(v)]


class Forecaster:
    """Giao diện chung: predict(series_list) -> với mỗi chuỗi trả (q10, q50, q90) cho bước kế tiếp."""

    backend = "none"
    model_name = ""

    def is_ready(self) -> bool:
        raise NotImplementedError

    def status(self) -> dict:
        raise NotImplementedError

    def predict(self, series_list: List[List[float]]) -> List[Optional[List[float]]]:
        raise NotImplementedError


class NaiveForecaster(Forecaster):
    """Backend dev/test: trung vị của chuỗi, khoảng q10-q90 = trung vị ± 1.2816 * (1.4826 * MAD)."""

    backend = "naive"
    model_name = "median-mad (dev/test only)"

    def is_ready(self) -> bool:
        return True

    def status(self) -> dict:
        return {"backend": self.backend, "model": self.model_name, "loaded": True, "error": None}

    def predict(self, series_list):
        results = []
        for series in series_list:
            if len(series) < MIN_POINTS:
                results.append(None)
                continue
            ordered = sorted(series)
            median = _median(ordered)
            mad = _median(sorted(abs(v - median) for v in series))
            spread = 1.2816 * 1.4826 * mad
            results.append([median - spread, median, median + spread])
        return results


def _median(ordered: List[float]) -> float:
    middle = len(ordered) // 2
    if len(ordered) % 2 == 1:
        return ordered[middle]
    return (ordered[middle - 1] + ordered[middle]) / 2.0


class ChronosForecaster(Forecaster):
    """Chronos-Bolt: nạp model một lần ở thread nền, dự báo cả batch bằng pipeline.predict_quantiles."""

    backend = "chronos"

    def __init__(self, model_name: str = "amazon/chronos-bolt-tiny", pipeline=None):
        self.model_name = model_name
        self._pipeline = pipeline          # có thể truyền sẵn pipeline (dùng trong test)
        self._error: Optional[str] = None if pipeline is not None else "đang nạp model..."
        self._lock = threading.Lock()      # pipeline không đảm bảo thread-safe: mỗi lúc một lần dự báo

    def load_in_background(self) -> None:
        """Nạp model ở thread khác để ml-service khởi động ngay (tải model có thể mất nhiều giây)."""
        threading.Thread(target=self._load, name="chronos-loader", daemon=True).start()

    def _load(self) -> None:
        try:
            import torch
            from chronos import BaseChronosPipeline

            torch.set_num_threads(max(1, min(4, os.cpu_count() or 1)))
            pipeline = BaseChronosPipeline.from_pretrained(
                self.model_name, device_map="cpu", torch_dtype=torch.float32)
            with self._lock:
                self._pipeline = pipeline
                self._error = None
            print("[ml-service] Đã nạp model", self.model_name, flush=True)
        except Exception as error:  # noqa: BLE001 - mọi lỗi nạp model đều phải được ghi lại, không làm sập service
            with self._lock:
                self._error = f"{type(error).__name__}: {error}"
            print("[ml-service] KHÔNG nạp được model", self.model_name, "->", self._error, flush=True)

    def is_ready(self) -> bool:
        with self._lock:
            return self._pipeline is not None

    def status(self) -> dict:
        with self._lock:
            return {"backend": self.backend, "model": self.model_name,
                    "loaded": self._pipeline is not None, "error": self._error}

    def predict(self, series_list):
        with self._lock:
            pipeline = self._pipeline
            if pipeline is None:
                raise ForecastError(self._error or "model chưa sẵn sàng")

        import torch

        results: List[Optional[List[float]]] = [None] * len(series_list)

        # Gom các chuỗi có cùng số patch vào một lần gọi (tránh lỗi mặt nạ attention khi một batch có patch toàn NaN).
        groups = {}
        for index, series in enumerate(series_list):
            if len(series) >= MIN_POINTS:
                patches = math.ceil(len(series) / PATCH_SIZE)
                groups.setdefault(patches, []).append(index)

        with self._lock, torch.inference_mode():
            for indexes in groups.values():
                batch = [torch.tensor(series_list[i], dtype=torch.float32) for i in indexes]
                try:
                    quantiles, _ = pipeline.predict_quantiles(
                        batch, prediction_length=1, quantile_levels=QUANTILE_LEVELS)
                except Exception as error:  # noqa: BLE001
                    raise ForecastError(f"{type(error).__name__}: {error}") from error
                # quantiles có shape (số chuỗi, prediction_length=1, 3 phân vị)
                for position, index in enumerate(indexes):
                    q10, q50, q90 = (float(x) for x in quantiles[position, 0, :])
                    # Chronos có thể trả phân vị không đơn điệu một chút: sắp lại để q10 <= q50 <= q90.
                    results[index] = sorted([q10, q50, q90])
        return results


def build_forecaster() -> Forecaster:
    """Chọn backend theo LTM_ML_BACKEND và model theo LTM_ML_MODEL (tên trên HuggingFace hoặc đường dẫn thư mục local)."""
    backend = os.environ.get("LTM_ML_BACKEND", "chronos").strip().lower()
    if backend == "naive":
        return NaiveForecaster()

    model_name = os.environ.get("LTM_ML_MODEL", "amazon/chronos-bolt-tiny").strip()
    forecaster = ChronosForecaster(model_name)
    forecaster.load_in_background()
    return forecaster
