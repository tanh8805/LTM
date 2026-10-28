# Test service ML: logic thuần score_samples qua HTTP (TestClient), không cần chạy uvicorn.
import random

import pytest

from fastapi.testclient import TestClient

import simulate_clients as sim
from ml_service import FLAG_MIN_SCORE, app

client = TestClient(app)


def sample(student, window, values):
    return {"studentId": student, "window": window, "values": values}


def normal_values():
    # newProcesses, focusLostCount, focusLostSeconds, newRemoteHosts, violations: nhỏ trong khoảng 0-1; answers: 0-3
    return [random.random(), random.random(), random.random(), random.random(), 0.0, random.randint(0, 3)]


def room(deviant_windows=None):
    """6 sinh viên bình thường x 5 cửa sổ; deviant_windows là các cửa sổ lệch của sinh viên SV-X."""
    random.seed(0)
    samples = [sample(f"SV{i}", w, normal_values()) for i in range(1, 7) for w in range(1, 6)]
    for w in range(1, 6):
        lech = w in (deviant_windows if deviant_windows is not None else [])
        samples.append(sample("SV-X", w, [0, 6, 20, 0, 0, 8] if lech else normal_values()))
    return samples


def score(samples):
    resp = client.post("/score", json={"samples": samples})
    assert resp.status_code == 200
    return resp.json()


def by_student(body):
    return {r["studentId"]: r for r in body["results"]}


def test_not_enough_data():
    body = score([sample(f"SV{i}", 1, [0, 0, 0, 0, 0, 1]) for i in range(19)])
    assert body == {"status": "not_enough_data", "results": []}


def test_outlier_flagged():
    body = score(room(deviant_windows=[1, 2, 3, 4, 5]))
    assert body["status"] == "ok"
    results = by_student(body)
    assert results["SV-X"]["anomaly"] is True
    assert results["SV-X"]["score"] == max(r["score"] for r in results.values())

    # máy gian lận rời cửa sổ nhiều (feature 1 và 2) nên phải nằm trong lý do
    reasons = results["SV-X"]["reasons"]
    assert 1 <= len(reasons) <= 3
    assert {r["feature"] for r in reasons} & {1, 2}
    assert all(r["value"] > r["room"] for r in reasons)

    # SV-X có 5 cửa sổ lệch, chỉ 3 mẫu mới nhất được dùng nên total = value * 3
    for r in reasons:
        assert r["windows"] == 3
        assert r["total"] == pytest.approx(r["value"] * 3, abs=0.01)
        assert r["room_total"] == pytest.approx(r["room"] * 3, abs=0.01)
    by_feature = {r["feature"]: r for r in reasons}
    if 1 in by_feature:
        assert by_feature[1]["total"] == 18


def test_normal_student_has_no_strong_reasons():
    body = score(room(deviant_windows=[1, 2, 3, 4, 5]))
    assert by_student(body)["SV1"]["reasons"] == []


def test_identical_room_no_anomaly():
    body = score([sample(f"SV{i}", w, [0, 0, 1, 0, 0, 2]) for i in range(5) for w in range(1, 6)])
    assert body["status"] == "ok"
    assert all(r["score"] == 0 and r["anomaly"] is False for r in body["results"])


def test_uses_three_latest_windows():
    # cửa sổ 1 cực lệch nhưng cửa sổ 3-5 (3 mẫu mới nhất) bình thường như cả phòng
    body = score(room(deviant_windows=[1]))
    assert by_student(body)["SV-X"]["anomaly"] is False


def test_wrong_length_rejected():
    resp = client.post("/score", json={"samples": [sample("SV1", 1, [0, 0, 0, 0, 0])]})
    assert resp.status_code == 422


def sim_room(normal_students=30, windows=6, seed=1):
    """Phòng máy giả như simulate_clients.py (feature là số đếm nhỏ, nhiều mẫu trùng nhau)."""
    random.seed(seed)
    return [sample(f"SIM{i:02d}", w, sim.normal_values()) for i in range(1, normal_students + 1) for w in range(1, windows + 1)]


def test_normal_room_has_almost_no_anomaly():
    body = score(sim_room())
    flagged = [r["studentId"] for r in body["results"] if r["anomaly"]]
    assert len(flagged) <= 2, flagged


def test_cheater_in_normal_room_is_flagged():
    samples = sim_room()
    samples += [sample("SIM-X", w, sim.cheater_values()) for w in range(1, 7)]
    results = by_student(score(samples))
    assert results["SIM-X"]["anomaly"] is True
    assert results["SIM-X"]["reasons"]


def test_single_high_window_is_not_anomaly():
    samples = sim_room(normal_students=6)
    samples += [sample("SIM-X", w, sim.normal_values()) for w in range(1, 6)]
    samples.append(sample("SIM-X", 6, [0, 6, 20, 0, 0, 8]))   # chỉ chu kỳ cuối lệch
    result = by_student(score(samples))["SIM-X"]
    assert result["score"] >= FLAG_MIN_SCORE, "chu kỳ lệch phải thật sự cao thì test mới có nghĩa"
    assert result["anomaly"] is False


def test_anomaly_needs_at_least_one_reason():
    # mọi máy giống nhau thì không có lý do nào; anomaly luôn false dù điểm có thế nào
    body = score([sample(f"SV{i}", w, [0, 0, 1, 0, 0, 2]) for i in range(5) for w in range(1, 6)])
    assert all(r["reasons"] == [] and r["anomaly"] is False for r in body["results"])
