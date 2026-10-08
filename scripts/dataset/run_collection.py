# Owner: Nguoi4
"""
Thu toàn bộ dataset tuần tự (KHÔNG chạy song song: số liệu là của cả máy nên trace chạy cùng lúc sẽ làm bẩn nhau).

  python3 scripts/dataset/run_collection.py [--batch A|B|all] [--out-dir data/traces]

Có thể chạy lại: trace nào đã đủ file (csv + meta + events) thì bỏ qua. Trace lỗi được ghi vào log và đi tiếp.
Không chạy việc nặng khác (build, test, ml-service) trên cùng máy trong lúc đang thu, vì sẽ lẫn vào "bình thường".

Chia tập dữ liệu được CỐ ĐỊNH TRƯỚC khi thu (xem data/traces/splits.json): batch A = test, batch B = validation.
"""

import argparse
import os
import sys
import time
import traceback

import collect_trace

BATCH_A = [
    ("normal_01", "normal"), ("cpu_01", "cpu"), ("network_01", "network"), ("normal_02", "normal"),
    ("memory_01", "memory"), ("process_01", "process"), ("mixed_01", "mixed"), ("normal_03", "normal"),
    ("application_01", "application"), ("focus_01", "focus"),
]
BATCH_B = [
    ("normal_04", "normal"), ("cpu_02", "cpu"), ("network_02", "network"),
    ("memory_02", "memory"), ("process_02", "process"),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--batch", default="all", choices=["A", "B", "all"])
    parser.add_argument("--out-dir", default=os.path.join(collect_trace.REPO, "data/traces"))
    args = parser.parse_args()

    traces = []
    if args.batch in ("A", "all"):
        traces += BATCH_A
    if args.batch in ("B", "all"):
        traces += BATCH_B

    failed = []
    for name, plan in traces:
        if collect_trace.is_complete(name, args.out_dir):
            print("[run] %s đã có, bỏ qua" % name, flush=True)
            continue
        started = time.time()
        try:
            collect_trace.collect(name, plan, args.out_dir)
        except Exception:
            traceback.print_exc()
            failed.append(name)
        print("[run] %s mất %.1f phút" % (name, (time.time() - started) / 60), flush=True)

    print("[run] XONG. Lỗi: %s" % (failed if failed else "không"), flush=True)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
