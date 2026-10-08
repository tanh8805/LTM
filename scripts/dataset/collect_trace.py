# Owner: Nguoi4
"""
Thu MỘT trace: chạy Recorder (OSHI) và, đúng giờ theo kế hoạch, bật/tắt workload thật; ghi ground truth.

  python3 scripts/dataset/collect_trace.py --name cpu_01 --plan cpu [--out-dir data/traces]

Kết quả (trong --out-dir):
  <name>.csv          số liệu từ Recorder (không nhãn)
  <name>.meta.json    thông tin phiên ghi do Recorder ghi
  <name>.events.json  ground truth: các khoảng thời gian workload THẬT SỰ chạy (đo bằng đồng hồ, không phải kế hoạch)

Kế hoạch (PLANS) có đoạn bình thường trước, đoạn bất thường, đoạn nghỉ, đoạn bất thường thứ hai (cường độ cao hơn),
rồi đoạn bình thường sau. Đoạn đầu >= 5 phút để Isolation Forest học đúng mẫu bình thường (30 mẫu x 10 giây).
"""

import argparse
import json
import os
import subprocess
import sys
import time

import workloads

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CLASSPATH = os.pathsep.join([os.path.join(REPO, "source/tools/target/tools.jar"), os.path.join(REPO, "source/tools/target/lib/*")])

INTERVAL_SECONDS = 10

# Thời lượng (giây) mỗi đoạn.
PRE, ACTIVE, GAP, POST = 360, 90, 120, 120


def anomaly_plan(first, second, event_type, category="anomaly"):
    """Hai đợt cùng loại, đợt hai mạnh hơn: factory trả về workload mới cho mỗi đợt."""
    return {
        "total": PRE + ACTIVE + GAP + ACTIVE + POST,
        "events": [
            {"id": event_type.lower() + "-1", "type": event_type, "category": category, "offset": PRE, "duration": ACTIVE, "make": first},
            {"id": event_type.lower() + "-2", "type": event_type, "category": category, "offset": PRE + ACTIVE + GAP, "duration": ACTIVE, "make": second},
        ],
    }


PLANS = {
    # Chỉ ghi nền, không tạo gì. Dài hơn để đo báo nhầm.
    "normal": {"total": 720, "events": []},
    "cpu": anomaly_plan(lambda: workloads.CpuLoad(1), lambda: workloads.CpuLoad(4), "CPU_LOAD"),
    "network": anomaly_plan(lambda: workloads.NetworkLoad(1, 300), lambda: workloads.NetworkLoad(4, 1000), "NETWORK_LOAD"),
    "memory": anomaly_plan(lambda: workloads.MemoryLoad(1500), lambda: workloads.MemoryLoad(4000), "MEMORY_LOAD"),
    "process": anomaly_plan(lambda: workloads.ProcessChurn(40), lambda: workloads.ProcessChurn(200), "PROCESS_CHURN"),
    "mixed": anomaly_plan(
        lambda: workloads.Combined([workloads.CpuLoad(1), workloads.NetworkLoad(1, 300), workloads.ProcessChurn(30)]),
        lambda: workloads.Combined([workloads.CpuLoad(2), workloads.NetworkLoad(2, 1000), workloads.ProcessChurn(100),
                                    workloads.MemoryLoad(1500)]),
        "MIXED_LOAD"),
    # Hoạt động ứng dụng bình thường: KHÔNG phải bất thường (category benign).
    "application": anomaly_plan(lambda: workloads.ApplicationActivity(), lambda: workloads.ApplicationActivity(),
                                "APPLICATION_ACTIVITY", category="benign"),
    # Mất focus: lệnh gửi qua stdin của Recorder (xem FocusWorkload).
    "focus": None,  # dựng riêng ở build_focus_plan() vì cần stdin của Recorder
}


class FocusWorkload(workloads.Workload):
    """Gửi lệnh FOCUS_LOSS tới Recorder (--focus-probe) theo chu kỳ trong khoảng sự kiện."""

    def __init__(self, recorder, every_seconds):
        super().__init__()
        self.recorder = recorder
        self.every_seconds = every_seconds
        self.params = {"everySeconds": every_seconds, "mechanism": "Swing WindowFocusListener under Xvfb"}
        self._running = False
        self._thread = None
        self._wake = None
        self.sent = 0

    def start(self):
        import threading
        self._running = True
        self._wake = threading.Event()

        def loop():
            while self._running:
                try:
                    self.recorder.stdin.write(b"FOCUS_LOSS\n")
                    self.recorder.stdin.flush()
                    self.sent += 1
                except (BrokenPipeError, ValueError):
                    return
                self._wake.wait(self.every_seconds)

        self._thread = threading.Thread(target=loop, daemon=True)
        self._thread.start()

    def stop(self):
        self._running = False
        if self._wake is not None:
            self._wake.set()
        if self._thread is not None:
            self._thread.join(timeout=5)
        self.params["commandsSent"] = self.sent


def start_recorder(name, out_dir, total_seconds, use_focus_probe, display):
    csv = os.path.join(out_dir, name + ".csv")
    command = ["java", "-Xmx256m", "-Dstdout.encoding=UTF-8", "-cp", CLASSPATH, "exam.tools.Recorder",
               "--out", csv, "--session", name, "--machine", "LOCAL", "--seconds", str(total_seconds),
               "--interval", str(INTERVAL_SECONDS), "--scenario", name.rsplit("_", 1)[0]]
    env = dict(os.environ)
    if use_focus_probe:
        command.append("--focus-probe")
        env["DISPLAY"] = display
    recorder = subprocess.Popen(command, cwd=REPO, env=env, stdin=subprocess.PIPE,
                                stdout=open(os.path.join(out_dir, name + ".recorder.log"), "wb"), stderr=subprocess.STDOUT)
    return recorder


def collect(name, plan_name, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    use_focus = plan_name == "focus"
    plan = PLANS[plan_name] if not use_focus else {
        "total": PRE + ACTIVE + GAP + ACTIVE + POST,
        "events": [
            {"id": "focus_loss-1", "type": "FOCUS_LOSS", "category": "anomaly", "offset": PRE, "duration": ACTIVE, "every": 8},
            {"id": "focus_loss-2", "type": "FOCUS_LOSS", "category": "anomaly", "offset": PRE + ACTIVE + GAP, "duration": ACTIVE, "every": 4},
        ],
    }

    quiet_wait = workloads.wait_until_quiet()
    print("[collect] %s: hệ thống yên sau %ds, bắt đầu (plan=%s, %ds)" % (name, quiet_wait, plan_name, plan["total"]), flush=True)

    xvfb = None
    display = ":87"
    if use_focus:
        xvfb = subprocess.Popen(["Xvfb", display, "-screen", "0", "800x600x24"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(2)

    recorder = start_recorder(name, out_dir, plan["total"], use_focus, display)
    recorded_events = []
    active = None
    try:
        # Chờ Recorder sẵn sàng (JVM khởi động) rồi lấy mốc thời gian 0.
        log_path = os.path.join(out_dir, name + ".recorder.log")
        deadline = time.time() + 60
        while time.time() < deadline:
            with open(log_path, "rb") as log:
                if b"Phi" in log.read():
                    break
            time.sleep(0.2)
        else:
            raise RuntimeError("Recorder không khởi động được, xem " + log_path)
        t0 = time.time()

        for event in plan["events"]:
            sleep_until(t0 + event["offset"])
            if use_focus:
                workload = FocusWorkload(recorder, event["every"])
            else:
                workload = event["make"]()
            start_ms = int(time.time() * 1000)
            workload.start()
            active = workload
            sleep_until(t0 + event["offset"] + event["duration"])
            workload.stop()
            end_ms = int(time.time() * 1000)
            active = None
            recorded_events.append({
                "id": event["id"], "type": event["type"], "category": event["category"],
                "startTime": start_ms, "endTime": end_ms,
                "params": workload.params,
                "description": "%s; planned offset %ds, duration %ds" % (event["type"], event["offset"], event["duration"]),
            })
            print("[collect] %s: %s xong (%.1fs)" % (name, event["id"], (end_ms - start_ms) / 1000.0), flush=True)

        sleep_until(t0 + plan["total"])
        recorder.wait(timeout=60)
    finally:
        if active is not None:
            active.stop()
        if recorder.poll() is None:
            recorder.kill()
        if xvfb is not None:
            xvfb.kill()

    if recorder.returncode != 0:
        raise RuntimeError("Recorder thoát với mã %s, xem %s.recorder.log" % (recorder.returncode, name))

    events_doc = {"sessionId": name, "plan": plan_name, "events": recorded_events}
    with open(os.path.join(out_dir, name + ".events.json"), "w", encoding="utf-8") as out:
        json.dump(events_doc, out, indent=2, ensure_ascii=False)
    os.remove(os.path.join(out_dir, name + ".recorder.log"))
    print("[collect] %s: hoàn tất" % name, flush=True)


def sleep_until(moment):
    remaining = moment - time.time()
    if remaining > 0:
        time.sleep(remaining)


def is_complete(name, out_dir):
    """Trace đã thu đủ: có csv, meta và events."""
    return all(os.path.exists(os.path.join(out_dir, name + suffix)) for suffix in (".csv", ".meta.json", ".events.json"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", required=True)
    parser.add_argument("--plan", required=True, choices=sorted(PLANS))
    parser.add_argument("--out-dir", default=os.path.join(REPO, "data/traces"))
    args = parser.parse_args()
    collect(args.name, args.plan, args.out_dir)


if __name__ == "__main__":
    main()
