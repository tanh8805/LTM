# Owner: Nguoi4
"""
Workload có kiểm soát để tạo "kịch bản bất thường hệ thống" khi thu dữ liệu.

QUAN TRỌNG: đây là kịch bản hệ thống (CPU / network / memory / process...), KHÔNG phải gian lận.
Mỗi workload là tiến trình HỆ ĐIỀU HÀNH thật; số liệu do Recorder (OSHI) đo, không được giả lập.

Mỗi lớp có start() và stop(); stop() luôn giết sạch tiến trình con (kể cả khi lỗi) để không làm bẩn đoạn sau.
"""

import os
import shutil
import signal
import subprocess
import sys
import time

# Địa chỉ tải về: các host mà môi trường cho phép truy cập trực tiếp, khác IP nhau (để tăng distinctDestinationCount).
# Chỉ tải xuống và giới hạn tốc độ; không upload gì lên bên thứ ba.
DOWNLOAD_URLS = [
    "https://files.pythonhosted.org/packages/4a/9e/4e7a07fd0776dc2210cdacf2010be8665194d094defc10c419d7dea794cc/"
    "numpy-2.5.3-cp315-cp315t-manylinux_2_27_x86_64.manylinux_2_28_x86_64.whl",
    "https://registry.npmjs.org/typescript/-/typescript-5.4.5.tgz",
    "https://proxy.golang.org/golang.org/x/net/@v/v0.24.0.zip",
    "https://pypi.org/simple/pandas/",
]


def _spawn(args, **kwargs):
    """Chạy tiến trình trong nhóm tiến trình riêng để giết cả nhóm được."""
    return subprocess.Popen(args, start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, **kwargs)


def _kill_group(process):
    if process.poll() is not None:
        return
    try:
        os.killpg(os.getpgid(process.pid), signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait()


class Workload:
    """Lớp cha: giữ danh sách tiến trình đã tạo để stop() dọn sạch."""

    def __init__(self):
        self.processes = []
        self.params = {}

    def start(self):
        raise NotImplementedError

    def stop(self):
        for process in self.processes:
            _kill_group(process)
        self.processes = []


class CpuLoad(Workload):
    """`cores` tiến trình vòng lặp bận, mỗi tiến trình chiếm trọn một nhân CPU."""

    def __init__(self, cores):
        super().__init__()
        self.cores = cores
        self.params = {"cores": cores}

    def start(self):
        for _ in range(self.cores):
            self.processes.append(_spawn([sys.executable, "-c", "while True: pass"]))


class MemoryLoad(Workload):
    """Một tiến trình cấp phát `megabytes` MB và chạm vào từng trang để RAM thật sự được dùng."""

    def __init__(self, megabytes):
        super().__init__()
        self.megabytes = megabytes
        self.params = {"megabytes": megabytes}

    def start(self):
        code = (
            "import time\n"
            "chunk = b'\\x01' * (1024 * 1024)\n"
            "blocks = []\n"
            "for _ in range(%d):\n"
            "    blocks.append(bytearray(chunk))\n"
            "time.sleep(3600)\n" % self.megabytes
        )
        self.processes.append(_spawn([sys.executable, "-c", code]))


class NetworkLoad(Workload):
    """
    `streams` luồng tải xuống song song, mỗi luồng giới hạn `rate_kb` KB/giây, tới các host khác nhau.
    Mỗi luồng là một vòng lặp curl; tải lại ngay khi xong.
    """

    def __init__(self, streams, rate_kb):
        super().__init__()
        self.streams = streams
        self.rate_kb = rate_kb
        self.params = {"streams": streams, "rateKbPerStream": rate_kb}

    def start(self):
        if shutil.which("curl") is None:
            raise RuntimeError("Không có curl để tạo tải network")
        urls = []
        for url in DOWNLOAD_URLS:
            check = subprocess.run(["curl", "-s", "-o", "/dev/null", "-m", "10", "-I", "-w", "%{http_code}", url],
                                   capture_output=True, text=True)
            if check.stdout.strip() in ("200", "301", "302"):
                urls.append(url)
        if not urls:
            raise RuntimeError("Không URL tải nào truy cập được; không thể tạo tải network")
        self.params["urls"] = urls
        for i in range(self.streams):
            url = urls[i % len(urls)]
            script = "while true; do curl -s -o /dev/null --limit-rate %dk -m 60 '%s'; sleep 1; done" % (self.rate_kb, url)
            self.processes.append(_spawn(["sh", "-c", script]))


class ProcessChurn(Workload):
    """`count` tiến trình `sleep` sống suốt đoạn, thêm tiến trình ngắn hạn sinh ra/mất đi liên tục (mở/đóng process)."""

    def __init__(self, count):
        super().__init__()
        self.count = count
        self.params = {"sleepingProcesses": count, "churnPerSecond": 2}

    def start(self):
        for _ in range(self.count):
            self.processes.append(_spawn(["sleep", "3600"]))
        churn = "while true; do sleep 1 & sleep 1 & sleep 1; done"
        self.processes.append(_spawn(["sh", "-c", churn]))


class ApplicationActivity(Workload):
    """
    Hoạt động ứng dụng thông thường (KHÔNG phải bất thường): biên dịch Java, nén file, tìm file, chạy Python ngắn.
    Có các khoảng nghỉ như người dùng thật.
    """

    def __init__(self):
        super().__init__()
        self.params = {"tasks": ["javac", "tar+gzip", "find", "python"]}

    def start(self):
        work_dir = "/tmp/ltm_app_activity"
        os.makedirs(work_dir, exist_ok=True)
        with open(os.path.join(work_dir, "Hello.java"), "w") as source:
            source.write("public class Hello { public static void main(String[] a) { System.out.println(\"hi\"); } }\n")
        script = (
            "cd %s; while true; do "
            "javac Hello.java >/dev/null 2>&1; java -cp . Hello >/dev/null 2>&1; sleep 4; "
            "tar -cf - /usr/lib/python3* 2>/dev/null | gzip -1 > /dev/null; sleep 3; "
            "find /usr -type f 2>/dev/null | wc -l >/dev/null; sleep 3; "
            "python3 -c 'import json,sys; print(sum(i*i for i in range(2000000)))' >/dev/null; sleep 5; "
            "done" % work_dir
        )
        self.processes.append(_spawn(["sh", "-c", script]))

    def stop(self):
        super().stop()
        shutil.rmtree("/tmp/ltm_app_activity", ignore_errors=True)


class Combined(Workload):
    """Nhiều workload chạy cùng lúc (kịch bản MIXED)."""

    def __init__(self, parts):
        super().__init__()
        self.parts = parts
        self.params = {"parts": [{type(p).__name__: p.params} for p in parts]}

    def start(self):
        started = []
        try:
            for part in self.parts:
                part.start()
                started.append(part)
        except Exception:
            for part in started:
                part.stop()
            raise

    def stop(self):
        for part in self.parts:
            part.stop()


def _cpu_busy_percent(seconds=3):
    """% CPU bận trong `seconds` giây, đọc từ /proc/stat (không dùng load average vì nó giảm rất chậm)."""
    def read():
        with open("/proc/stat") as stat:
            fields = [int(x) for x in stat.readline().split()[1:]]
        idle = fields[3] + fields[4]
        return sum(fields), idle

    total1, idle1 = read()
    time.sleep(seconds)
    total2, idle2 = read()
    delta = max(1, total2 - total1)
    return 100.0 * (delta - (idle2 - idle1)) / delta


def wait_until_quiet(max_wait_seconds=240, required_quiet_checks=4):
    """Đợi hệ thống yên sau workload: CPU bận < 3% liên tiếp `required_quiet_checks` lần. Trả về số giây đã chờ."""
    start = time.time()
    quiet = 0
    while time.time() - start < max_wait_seconds and quiet < required_quiet_checks:
        quiet = quiet + 1 if _cpu_busy_percent() < 3.0 else 0
        if quiet < required_quiet_checks:
            time.sleep(2)
    return round(time.time() - start)
