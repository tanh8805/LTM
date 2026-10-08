# Owner: Nguoi4
"""
Chẩn đoán chi tiết (mô tả, không chọn tham số) từ dump của ExperimentRunner:
  - dòng thời gian quanh đợt bất thường đầu tiên: giá trị thật, dải q0.1-q0.9, điểm IF
  - nguồn báo nhầm của Chronos trong đoạn bình thường: metric nào vượt dải nhiều nhất
  - các chiều "hằng số" trong 30 mẫu học của từng trace (Isolation Forest không chia được theo chiều này)
Cách dùng: diagnose.py --dump scores.csv --traces cpu_02,network_02
"""
import argparse
import os

import numpy as np
import pandas as pd

import analyze

DIMS = analyze.METRICS + ["processCount", "focusLostCount"]


def timeline(df, trace, metrics, before=2, after=12):
    g = df[df["trace"] == trace].reset_index(drop=True)
    anomaly_index = g.index[g["label"] == 1]
    if len(anomaly_index) == 0:
        return
    start = anomaly_index[0]
    print("\n== %s: đợt đầu bắt đầu ở t=%d (%s) ==" % (trace, g.loc[start, "t"], g.loc[start, "scenario"]))
    for metric in metrics:
        print("-- %s: t | thật | q10 | q50 | q90 | ngoài dải? | IF điểm/threshold | nhãn" % metric)
        for i in range(max(0, start - before), min(len(g), start + after)):
            r = g.loc[i]
            q10, q50, q90 = r.get(metric + "_q10", np.nan), r.get(metric + "_q50", np.nan), r.get(metric + "_q90", np.nan)
            outside = "" if np.isnan(q10) else ("NGOÀI" if (r[metric] < q10 or r[metric] > q90) else "trong")
            print("   t=%2d %10.2f | %9.2f %9.2f %9.2f | %-5s | %.3f/%.3f | %d" % (
                r["t"], r[metric], q10, q50, q90, outside, r["ifScore"], r["ifThreshold"], r["label"]))


def false_alarm_sources(df):
    normal = df[(df["isEval"]) & (df["label"] == 0)]
    print("\n== Nguồn báo nhầm của Chronos trong đoạn label=0 (số mẫu ngoài dải q0.1-q0.9 theo metric) ==")
    for metric in analyze.METRICS:
        e = normal[normal[metric + "_q10"].notna()]
        outside = (e[metric] < e[metric + "_q10"]) | (e[metric] > e[metric + "_q90"])
        zero = (e[metric + "_q90"] - e[metric + "_q10"]) <= 1e-9
        print("  %-26s ngoài dải %5.1f%% (%d/%d) | dải rộng 0: %5.1f%% mẫu | ngoài dải khi dải rộng 0: %d" % (
            metric, 100 * outside.mean(), outside.sum(), len(e), 100 * zero.mean(), int((outside & zero).sum())))
    flagged = normal[normal["chronosFlag"] == 1]
    print("  mẫu label=0 bị Chronos (luật mặc định) báo: %d / %d, theo trace: %s" % (
        len(flagged), len(normal), flagged.groupby("trace").size().to_dict()))


def constant_dims(df):
    print("\n== Chiều không đổi trong 30 mẫu học (IF không chia được) ==")
    for trace, g in df.groupby("trace"):
        train = g[~g["isEval"]]
        const = [d for d in DIMS if train[d].nunique() <= 1]
        print("  %-14s hằng: %s" % (trace, const if const else "không"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dump", required=True)
    parser.add_argument("--traces", required=True)
    parser.add_argument("--timeline", default="")
    args = parser.parse_args()
    df = analyze.load_dump(args.dump)
    df = analyze.traces_in(df, args.traces.split(",")).reset_index(drop=True)
    constant_dims(df)
    false_alarm_sources(df)
    focus = {"cpu": ["cpuPercent"], "network": ["kbReceived", "distinctDestinationCount"], "memory": ["ramPercent"], "process": ["processCount"]}
    for trace in df["trace"].unique():
        key = trace.split("_")[0]
        if key in focus:
            timeline(df, trace, focus[key])


if __name__ == "__main__":
    main()
