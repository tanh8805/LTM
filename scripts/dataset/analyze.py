# Owner: Nguoi4
"""
Phân tích Isolation Forest và Chronos từ file dump của ExperimentRunner (--dump-scores).

  describe   : phân phối điểm NORMAL / BENIGN / ANOMALY, AUROC, độ phủ khoảng q0.1-q0.9, phản ứng của từng metric
  calibrate  : thử TỪNG thay đổi một (one-factor-at-a-time) trên tập VALIDATION, ghi MỌI thí nghiệm vào calibration_log.csv
  apply      : áp cấu hình đã chọn (cố định từ validation) lên tập TEST đúng một lần, cạnh cấu hình mặc định

Nguyên tắc: tham số chỉ được chọn bằng validation. Test chỉ dùng để báo cáo. Mọi thí nghiệm đều được ghi, kể cả kết quả xấu.
Mô phỏng lại luật IF/Chronos từ dữ liệu thô trong dump; ở cấu hình mặc định kết quả PHẢI trùng với ExperimentRunner (kiểm tra bằng
`check`), nên có thể tin các thí nghiệm mô phỏng lại.
"""

import argparse
import json
import os
import sys
from datetime import datetime, timezone

import numpy as np
import pandas as pd

METRICS = ["kbSent", "kbReceived", "connectionCount", "distinctDestinationCount", "cpuPercent", "ramPercent"]
DEFAULT_IF_MARGIN = 0.05

DEFAULT_CONFIG = {
    "if_margin": DEFAULT_IF_MARGIN, "if_consecutive": 3,
    "ch_consecutive": 3, "ch_widen": 1.0, "ch_floor_std_k": 0.0, "ch_min_metrics": 1,
}


# ----------------------------------------------------------------------------------------------------------------------
# Đọc dữ liệu
# ----------------------------------------------------------------------------------------------------------------------

def load_dump(path):
    df = pd.read_csv(path)
    df = df.sort_values(["trace", "t"]).reset_index(drop=True)
    df["isEval"] = df["ifTraining"] == 0
    return df


def traces_in(df, names):
    return df[df["trace"].isin(names)].copy()


def load_splits(repo):
    with open(os.path.join(repo, "data/traces/splits.json"), encoding="utf-8") as f:
        return json.load(f)


# ----------------------------------------------------------------------------------------------------------------------
# Mô phỏng lại luật phát hiện
# ----------------------------------------------------------------------------------------------------------------------

def consecutive_counts(flags):
    """Chuỗi bool -> số lần liên tiếp True tính đến mỗi vị trí."""
    counts = np.zeros(len(flags), dtype=int)
    run = 0
    for i, flag in enumerate(flags):
        run = run + 1 if flag else 0
        counts[i] = run
    return counts


def if_flags(df, margin, consecutive):
    """Isolation Forest: điểm > trainMax + margin, đủ `consecutive` lần liên tiếp. trainMax suy ra từ threshold mặc định (0.05)."""
    result = np.zeros(len(df), dtype=bool)
    for trace, group in df.groupby("trace", sort=False):
        evaluated = group[group["isEval"]]
        if evaluated.empty:
            continue
        train_max = evaluated["ifThreshold"].iloc[0] - DEFAULT_IF_MARGIN
        exceed = (group["ifScore"].values > train_max + margin) & group["isEval"].values
        flags = consecutive_counts(exceed) >= consecutive
        result[group.index.values] = flags
    return pd.Series(result, index=df.index)


def band(df, metric, widen, floor_std_k):
    """Khoảng chấp nhận [lo, hi] của một metric. widen nhân độ rộng nửa khoảng; floor_std_k đặt sàn bằng k x độ lệch chuẩn quá khứ (60 mẫu)."""
    q10, q50, q90 = df[metric + "_q10"], df[metric + "_q50"], df[metric + "_q90"]
    lo = q50 - widen * (q50 - q10)
    hi = q50 + widen * (q90 - q50)
    if floor_std_k > 0:
        # độ lệch chuẩn của chính trace đó trong 60 mẫu TRƯỚC mẫu hiện tại (không dùng tương lai)
        std = df.groupby("trace")[metric].transform(lambda s: s.shift(1).rolling(60, min_periods=12).std())
        half = floor_std_k * std.fillna(0)
        lo = np.minimum(lo, q50 - half)
        hi = np.maximum(hi, q50 + half)
    return lo, hi


def exceedance(df, metric, widen=1.0, floor_std_k=0.0):
    """Mức vượt khỏi khoảng, chuẩn hóa theo độ rộng khoảng (0 nếu trong khoảng; NaN nếu chưa có dự báo)."""
    lo, hi = band(df, metric, widen, floor_std_k)
    x = df[metric]
    width = (hi - lo).clip(lower=1e-3 * (df[metric + "_q50"].abs() + 1.0))
    value = np.maximum(np.maximum(lo - x, x - hi), 0) / width
    return value.where(df[metric + "_q50"].notna())


def chronos_flags(df, consecutive, widen, floor_std_k, min_metrics, metrics=METRICS):
    """Luật Chronos mô phỏng lại: ít nhất `min_metrics` metric cùng nằm ngoài khoảng đủ `consecutive` lần liên tiếp."""
    votes = np.zeros(len(df), dtype=int)
    for metric in metrics:
        exceed = (exceedance(df, metric, widen, floor_std_k) > 0).fillna(False).values
        counts = np.zeros(len(df), dtype=int)
        for trace, group in df.groupby("trace", sort=False):
            counts[group.index.values] = consecutive_counts(exceed[group.index.values])
        votes += (counts >= consecutive).astype(int)
    return pd.Series(votes >= min_metrics, index=df.index)


def chronos_score(df, widen=1.0, floor_std_k=0.0, persist=3):
    """Điểm liên tục của Chronos: mức vượt nhỏ nhất trong `persist` mẫu gần nhất, lấy metric tệ nhất. persist=1: không cần liên tiếp."""
    scores = np.full(len(df), np.nan)
    per_metric = []
    for metric in METRICS:
        e = exceedance(df, metric, widen, floor_std_k)
        rolled = e.groupby(df["trace"]).transform(lambda s: s.rolling(persist, min_periods=persist).min())
        per_metric.append(rolled.values)
    stacked = np.vstack(per_metric)
    with np.errstate(all="ignore"):
        scores = np.nanmax(stacked, axis=0)
    return pd.Series(scores, index=df.index)


# ----------------------------------------------------------------------------------------------------------------------
# Chỉ số đánh giá (cùng định nghĩa với ExperimentEngine.evaluate)
# ----------------------------------------------------------------------------------------------------------------------

def evaluate(df, flags):
    d = df[df["isEval"]].copy()
    d["flag"] = flags[d.index].astype(bool)
    anomaly = d["label"] == 1
    tp = int((anomaly & d["flag"]).sum())
    fn = int((anomaly & ~d["flag"]).sum())
    fp = int((~anomaly & d["flag"]).sum())
    tn = int((~anomaly & ~d["flag"]).sum())
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    fpr = fp / (fp + tn) if fp + tn else 0.0

    episodes = 0
    for trace, group in d.groupby("trace", sort=False):
        previous = False
        for is_anomaly, flag in zip(group["label"].values == 1, group["flag"].values):
            false_alarm = (not is_anomaly) and flag
            if false_alarm and not previous:
                episodes += 1
            previous = false_alarm

    windows, detected, delay_total = 0, 0, 0
    per_scenario = {}
    for trace, group in d.groupby("trace", sort=False):
        labels = group["label"].values
        flag = group["flag"].values
        scenario = group["scenario"].values
        i = 0
        while i < len(labels):
            if labels[i] != 1:
                i += 1
                continue
            start = i
            while i < len(labels) and labels[i] == 1:
                i += 1
            windows += 1
            stats = per_scenario.setdefault(scenario[start], {"windows": 0, "detected": 0, "delay": 0})
            stats["windows"] += 1
            hits = np.where(flag[start:i])[0]
            if len(hits):
                detected += 1
                delay_total += int(hits[0])
                stats["detected"] += 1
                stats["delay"] += int(hits[0])
    normal = (d["category"] == "normal")
    benign = (d["category"] == "benign")
    return {
        "tp": tp, "fp": fp, "fn": fn, "tn": tn, "precision": precision, "recall": recall, "f1": f1, "fpr": fpr,
        "fp_episodes": episodes, "windows": windows, "windows_detected": detected,
        "avg_delay_samples": delay_total / detected if detected else float("nan"),
        "fp_normal": int((normal & d["flag"]).sum()), "normal_samples": int(normal.sum()),
        "fp_benign": int((benign & d["flag"]).sum()), "benign_samples": int(benign.sum()),
        "per_scenario": per_scenario,
    }


def auroc(positive_scores, negative_scores):
    """Xác suất điểm của mẫu bất thường cao hơn mẫu bình thường (Mann-Whitney U; NaN bỏ qua; hòa tính 0.5)."""
    p = np.asarray(positive_scores, dtype=float)
    n = np.asarray(negative_scores, dtype=float)
    p, n = p[~np.isnan(p)], n[~np.isnan(n)]
    if len(p) == 0 or len(n) == 0:
        return float("nan")
    combined = np.concatenate([p, n])
    ranks = pd.Series(combined).rank(method="average").values
    u = ranks[: len(p)].sum() - len(p) * (len(p) + 1) / 2
    return u / (len(p) * len(n))


# ----------------------------------------------------------------------------------------------------------------------
# describe
# ----------------------------------------------------------------------------------------------------------------------

def percentile_row(values):
    values = np.asarray(values, dtype=float)
    values = values[~np.isnan(values)]
    if len(values) == 0:
        return {"n": 0}
    return {"n": len(values), "mean": values.mean(), "p50": np.percentile(values, 50), "p90": np.percentile(values, 90),
            "p99": np.percentile(values, 99), "max": values.max()}


def describe(df, label):
    """Bảng mô tả (trả về dict các DataFrame) cho một tập trace."""
    ev = df[df["isEval"]].copy()
    ev["chronosExceed1"] = chronos_score(df, persist=1)[ev.index]
    ev["chronosExceed3"] = chronos_score(df, persist=3)[ev.index]
    ev["ifMargin"] = ev["ifScore"] - (ev["ifThreshold"] - DEFAULT_IF_MARGIN)  # điểm so với max lúc học (>margin là vượt)

    tables = {}
    rows = []
    groups = [("normal", ev[ev["category"] == "normal"]), ("benign", ev[ev["category"] == "benign"])]
    for scenario in sorted(ev[ev["label"] == 1]["scenario"].unique()):
        groups.append((scenario, ev[(ev["label"] == 1) & (ev["scenario"] == scenario)]))
    groups.append(("ALL_ANOMALY", ev[ev["label"] == 1]))
    for name, g in groups:
        for score in ["ifScore", "ifMargin", "chronosExceed1", "chronosExceed3"]:
            row = {"group": name, "score": score}
            row.update(percentile_row(g[score]))
            rows.append(row)
    tables["distribution"] = pd.DataFrame(rows)

    rows = []
    negatives = {"vs normal": ev[ev["category"] == "normal"], "vs normal+benign": ev[ev["label"] == 0]}
    for name, g in groups[2:]:
        for neg_name, neg in negatives.items():
            for score in ["ifScore", "chronosExceed1", "chronosExceed3"]:
                rows.append({"anomaly": name, "negatives": neg_name, "score": score, "auroc": auroc(g[score], neg[score])})
    tables["auroc"] = pd.DataFrame(rows)

    # Độ phủ thực tế của khoảng q0.1-q0.9 và độ rộng khoảng
    rows = []
    for metric in METRICS:
        e = ev[ev[metric + "_q10"].notna()]
        inside = (e[metric] >= e[metric + "_q10"]) & (e[metric] <= e[metric + "_q90"])
        width = e[metric + "_q90"] - e[metric + "_q10"]
        for name, mask in [("normal", e["category"] == "normal"), ("benign", e["category"] == "benign"), ("anomaly", e["label"] == 1)]:
            if mask.sum() == 0:
                continue
            rows.append({"metric": metric, "group": name, "n": int(mask.sum()),
                         "coverage_in_q10_q90": float(inside[mask].mean()),
                         "zero_width_fraction": float((width[mask] <= 1e-9).mean()),
                         "median_width": float(width[mask].median()),
                         "median_abs_error_q50": float((e[metric][mask] - e[metric + "_q50"][mask]).abs().median())})
    tables["chronos_coverage"] = pd.DataFrame(rows)

    # Phản ứng của từng metric với từng kịch bản (effect size chuẩn hóa theo độ lệch chuẩn của đoạn normal cùng trace)
    rows = []
    all_metrics = METRICS + ["processCount", "focusLostCount"]
    for scenario in sorted(ev[ev["label"] == 1]["scenario"].unique()):
        for metric in all_metrics:
            diffs = []
            for trace, g in ev.groupby("trace"):
                base = g[g["category"] == "normal"][metric]
                act = g[(g["label"] == 1) & (g["scenario"] == scenario)][metric]
                if len(base) < 5 or len(act) < 3:
                    continue
                scale = max(base.std(), 0.05 * abs(base.mean()), 1e-6)
                diffs.append((act.mean() - base.mean()) / scale)
            if diffs:
                rows.append({"scenario": scenario, "metric": metric, "effect_size_sd": float(np.mean(diffs)), "traces": len(diffs)})
    tables["feature_response"] = pd.DataFrame(rows)
    return tables


# ----------------------------------------------------------------------------------------------------------------------
# calibrate / apply
# ----------------------------------------------------------------------------------------------------------------------

LOG_COLUMNS = ["timestamp", "experiment_id", "split", "stage", "detector", "changed", "if_margin", "if_consecutive", "ch_consecutive",
               "ch_widen", "ch_floor_std_k", "ch_min_metrics", "precision", "recall", "f1", "fpr", "fp", "fp_episodes", "tp", "fn", "tn",
               "windows_detected", "windows", "avg_delay_samples", "fp_normal", "normal_samples", "fp_benign", "benign_samples"]


def run_config(df, config, detector):
    flags_if = if_flags(df, config["if_margin"], config["if_consecutive"])
    flags_ch = chronos_flags(df, config["ch_consecutive"], config["ch_widen"], config["ch_floor_std_k"], config["ch_min_metrics"])
    if detector == "IF":
        flags = flags_if
    elif detector == "CHRONOS":
        flags = flags_ch
    elif detector == "BOTH_OR":
        flags = flags_if | flags_ch
    elif detector == "BOTH_AND":
        flags = flags_if & flags_ch
    else:
        flags = pd.Series(False, index=df.index)
    return evaluate(df, flags)


def log_row(log_path, experiment_id, split, stage, detector, changed, config, result):
    row = {"timestamp": datetime.now(timezone.utc).isoformat(), "experiment_id": experiment_id, "split": split, "stage": stage,
           "detector": detector, "changed": changed}
    row.update({k: config[k] for k in DEFAULT_CONFIG})
    row.update({k: result[k] for k in ["precision", "recall", "f1", "fpr", "fp", "fp_episodes", "tp", "fn", "tn", "windows_detected",
                                        "windows", "avg_delay_samples", "fp_normal", "normal_samples", "fp_benign", "benign_samples"]})
    new = not os.path.exists(log_path)
    pd.DataFrame([row], columns=LOG_COLUMNS).to_csv(log_path, mode="a", header=new, index=False)
    return row


# Mỗi yếu tố được thử MỘT mình quanh cấu hình mặc định.
FACTORS = [
    ("if_consecutive", [1, 2, 3, 4, 5]),
    ("if_margin", [0.0, 0.025, 0.05, 0.1, 0.15]),
    ("ch_consecutive", [1, 2, 3, 4, 5, 6]),
    ("ch_widen", [1.0, 1.5, 2.0, 3.0]),
    ("ch_floor_std_k", [0.0, 1.0, 2.0, 3.0]),
    ("ch_min_metrics", [1, 2, 3]),
]


def objective(result, max_fpr):
    """Chọn cấu hình: F1 cao nhất trong số cấu hình có FPR <= max_fpr (khai báo trước). Trả về None nếu vi phạm ràng buộc."""
    if result["fpr"] > max_fpr:
        return None
    return result["f1"]


def calibrate(df_val, log_path, max_fpr, out_json):
    results = []
    counter = 0

    def record(stage, detector, changed, config):
        nonlocal counter
        counter += 1
        result = run_config(df_val, config, detector)
        row = log_row(log_path, "V%03d" % counter, "validation", stage, detector, changed, config, result)
        results.append((row, result, config, detector))
        return result

    base = dict(DEFAULT_CONFIG)
    for detector in ["IF", "CHRONOS", "BOTH_OR", "BOTH_AND"]:
        record("baseline", detector, "default", base)

    best_single = {}
    for factor, values in FACTORS:
        detector = "IF" if factor.startswith("if_") else "CHRONOS"
        for value in values:
            if value == DEFAULT_CONFIG[factor]:
                continue
            config = dict(base)
            config[factor] = value
            result = record("one-factor", detector, "%s=%s" % (factor, value), config)
            score = objective(result, max_fpr)
            baseline_score = objective(run_config(df_val, base, detector), max_fpr)
            # Chỉ giữ thay đổi nếu thật sự tốt hơn mặc định theo mục tiêu đã khai báo trước
            if score is not None and (baseline_score is None or score > baseline_score + 1e-9):
                if factor not in best_single or score > best_single[factor][1]:
                    best_single[factor] = (value, score)

    # Giai đoạn 2: gộp các thay đổi đã tự chứng tỏ có ích khi đứng một mình (cho từng detector riêng)
    selected = {"IF": dict(base), "CHRONOS": dict(base)}
    for factor, (value, _) in best_single.items():
        selected["IF" if factor.startswith("if_") else "CHRONOS"][factor] = value
    combined = dict(base)
    combined.update({k: v for k, v in selected["IF"].items() if k.startswith("if_")})
    combined.update({k: v for k, v in selected["CHRONOS"].items() if k.startswith("ch_")})
    for detector in ["IF", "CHRONOS", "BOTH_OR", "BOTH_AND"]:
        record("combined-single-wins", detector, json.dumps({k: v for k, v in combined.items() if combined[k] != DEFAULT_CONFIG[k]}), combined)

    # Chọn tốt nhất theo mục tiêu trên TẤT CẢ thí nghiệm validation (kể cả mặc định)
    best = None
    for row, result, config, detector in results:
        score = objective(result, max_fpr)
        if score is None:
            continue
        if best is None or score > best[0] + 1e-12:
            best = (score, row["experiment_id"], detector, config, result)
    chosen = {"max_fpr_constraint": max_fpr, "objective": "max F1 subject to FPR <= max_fpr on validation"}
    if best is None:
        chosen["selected"] = None
        chosen["note"] = "Không cấu hình nào thỏa FPR <= %.3f trên validation" % max_fpr
    else:
        chosen["selected"] = {"experiment_id": best[1], "detector": best[2], "config": best[3], "validation_f1": best[0],
                              "validation_result": {k: v for k, v in best[4].items() if k != "per_scenario"}}
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(chosen, f, indent=2, ensure_ascii=False, default=float)
    return chosen


def apply_to_test(df_test, chosen, log_path):
    """Áp cấu hình mặc định và cấu hình đã chọn lên test, đúng một lần."""
    out = []
    for detector in ["NONE", "IF", "CHRONOS", "BOTH_OR", "BOTH_AND"]:
        result = run_config(df_test, DEFAULT_CONFIG, detector)
        log_row(log_path, "T-default-" + detector, "test", "final", detector, "default", DEFAULT_CONFIG, result)
        out.append(("default", detector, DEFAULT_CONFIG, result))
    selected = chosen.get("selected")
    if selected:
        config = selected["config"]
        detector = selected["detector"]
        result = run_config(df_test, config, detector)
        log_row(log_path, "T-selected-" + detector, "test", "final", detector, "selected from validation " + selected["experiment_id"], config, result)
        out.append(("selected", detector, config, result))
    return out


# ----------------------------------------------------------------------------------------------------------------------
# check: mô phỏng lại phải trùng ExperimentRunner
# ----------------------------------------------------------------------------------------------------------------------

def check_against_java(df, java_results_csv):
    java = pd.read_csv(java_results_csv).set_index("mode")
    problems = []
    for detector in ["IF", "CHRONOS", "BOTH_OR", "BOTH_AND"]:
        if detector not in java.index:
            continue
        mine = run_config(df, DEFAULT_CONFIG, detector)
        for key_py, key_java in [("tp", "tp"), ("fp", "fp"), ("fn", "fn"), ("tn", "tn"), ("windows_detected", "windowsDetected")]:
            if int(mine[key_py]) != int(java.loc[detector, key_java]):
                problems.append("%s %s: python=%s java=%s" % (detector, key_py, mine[key_py], java.loc[detector, key_java]))
    return problems


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ["describe", "calibrate", "apply", "check"]:
        p = sub.add_parser(name)
        p.add_argument("--dump", required=True)
        p.add_argument("--out-dir", required=True)
        p.add_argument("--repo", default=os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
        p.add_argument("--traces", help="danh sách trace phân tách bằng dấu phẩy (mặc định: theo split)")
        p.add_argument("--split", choices=["validation", "test"])
        p.add_argument("--max-fpr", type=float, default=0.02)
        p.add_argument("--chosen", help="file JSON do calibrate tạo ra (cho apply)")
        p.add_argument("--java-results", help="file kết quả ExperimentRunner (cho check)")
        p.add_argument("--log", default=None)
    args = parser.parse_args()
    os.makedirs(args.out_dir, exist_ok=True)

    df = load_dump(args.dump)
    splits = load_splits(args.repo)
    if args.traces:
        names = args.traces.split(",")
    elif args.split:
        names = [t for t in splits[args.split] if t not in splits.get("report_separately", {})]
    else:
        names = list(df["trace"].unique())
    df = traces_in(df, names).reset_index(drop=True)
    print("[analyze] %s: %d trace, %d mẫu" % (args.command, df["trace"].nunique(), len(df)))
    log_path = args.log or os.path.join(args.out_dir, "calibration_log.csv")

    if args.command == "describe":
        tables = describe(df, args.split or "custom")
        for name, table in tables.items():
            path = os.path.join(args.out_dir, "describe_%s.csv" % name)
            table.to_csv(path, index=False, float_format="%.4f")
            print("\n== %s ==" % name)
            print(table.to_string(index=False, float_format=lambda v: "%.3f" % v))
    elif args.command == "calibrate":
        chosen = calibrate(df, log_path, args.max_fpr, os.path.join(args.out_dir, "chosen_config.json"))
        print(json.dumps(chosen, indent=2, default=float))
    elif args.command == "apply":
        with open(args.chosen, encoding="utf-8") as f:
            chosen = json.load(f)
        for kind, detector, config, result in apply_to_test(df, chosen, log_path):
            print("%-9s %-9s P=%.3f R=%.3f F1=%.3f FPR=%.3f FPepisodes=%d windows=%d/%d delay=%.1f" % (
                kind, detector, result["precision"], result["recall"], result["f1"], result["fpr"], result["fp_episodes"],
                result["windows_detected"], result["windows"], result["avg_delay_samples"]))
    elif args.command == "check":
        problems = check_against_java(df, args.java_results)
        print("TRÙNG KHỚP với ExperimentRunner" if not problems else "\n".join(problems))
        sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
