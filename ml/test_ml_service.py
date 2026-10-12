# Test service ML: logic thuần score_samples qua HTTP (TestClient), không cần chạy uvicorn.
import random

from fastapi.testclient import TestClient

from ml_service import app

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
