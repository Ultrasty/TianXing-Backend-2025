#!/usr/bin/env python3
"""Fetch NOAA CPC official NAO and ENSO monthly index observations.

数据均为 NOAA CPC 官方观测序列：
- ENSO: ERSSTv6 逐月 Nino3.4 距平（ONI 的月输入，detrend.nino34.ascii.txt）
- NAO : CPC 官方逐月 NAO 指数（norm.nao.monthly.b5001.current.ascii）

语义：从目标年月起连续取文件里真实存在的观测值，最多 lead_months 个；
目标月无数据 / 中途断档 / 网络失败一律报错退出（exit 1），绝不编造、外推或兜底。
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from typing import Any, Dict, List, Tuple

ENSO_URL = "https://www.cpc.ncep.noaa.gov/data/indices/detrend.nino34.ascii.txt"
NAO_URL = "https://www.cpc.ncep.noaa.gov/products/precip/CWlink/pna/norm.nao.monthly.b5001.current.ascii"


class FetchError(Exception):
    pass


def http_get_text(url: str, retries: int = 1) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    last_err = None
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                return resp.read().decode("utf-8", errors="replace")
        except Exception as e:
            last_err = e
    raise FetchError("无法访问 NOAA 数据源 {}: {}".format(url, last_err))


def parse_index_file(text: str, value_col: int, min_cols: int, name: str) -> Dict[Tuple[int, int], float]:
    """解析 NOAA 指数文本文件，返回 {(year, month): value}。"""
    values: Dict[Tuple[int, int], float] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) < min_cols:
            continue
        try:
            y = int(parts[0])
            m = int(parts[1])
            v = float(parts[value_col])
        except ValueError:
            continue
        if 1 <= m <= 12:
            values[(y, m)] = v
    if not values:
        raise FetchError("NOAA {} 数据解析为空，数据源结构可能已变更".format(name))
    return values


def consecutive_observations(values: Dict[Tuple[int, int], float],
                             target_year: int, target_month: int,
                             max_months: int) -> List[float]:
    """从目标年月起连续取真实观测值（跨年进位），最多 max_months 个。"""
    if (target_year, target_month) not in values:
        latest = sorted(values)[-1]
        raise FetchError(
            "目标年月 {}-{:02d} 尚无 NOAA 观测数据（数据源最新仅到 {}-{:02d}）".format(
                target_year, target_month, latest[0], latest[1]
            )
        )
    series: List[float] = []
    y, m = target_year, target_month
    while len(series) < max_months:
        key = (y, m)
        if key not in values:
            break
        series.append(values[key])
        m += 1
        if m > 12:
            y += 1
            m = 1
    return series


def fetch_enso_series(target_year: int, target_month: int, max_months: int) -> Tuple[List[float], Dict[str, Any]]:
    text = http_get_text(ENSO_URL)
    values = parse_index_file(text, value_col=4, min_cols=5, name="ERSSTv6 Nino3.4")
    series = consecutive_observations(values, target_year, target_month, max_months)
    meta = {
        "source": "NOAA CPC ERSSTv6 Nino3.4 (monthly anomaly)",
        "source_type": "fetch",
    }
    return series, meta


def fetch_nao_series(target_year: int, target_month: int, max_months: int) -> Tuple[List[float], Dict[str, Any]]:
    text = http_get_text(NAO_URL)
    values = parse_index_file(text, value_col=2, min_cols=3, name="NAO")
    series = consecutive_observations(values, target_year, target_month, max_months)
    meta = {
        "source": "NOAA CPC NAO Monthly Index",
        "source_type": "fetch",
    }
    return series, meta


def apply_smoothing(data: List[float], smoothing: str) -> List[float]:
    if smoothing == "moving_avg" and len(data) >= 3:
        smoothed = []
        for i in range(len(data)):
            if i == 0:
                val = (data[0] * 2 + data[1]) / 3.0
            elif i == len(data) - 1:
                val = (data[-2] + data[-1] * 2) / 3.0
            else:
                val = (data[i - 1] + data[i] + data[i + 1]) / 3.0
            smoothed.append(round(val, 4))
        return smoothed
    return data


def main() -> None:
    parser = argparse.ArgumentParser(description="Fetch NOAA CPC NAO / ENSO monthly index observations")
    parser.add_argument("--dataset", required=True, choices=["NAO", "ENSO", "SIE"])
    parser.add_argument("--year", required=True)
    parser.add_argument("--month", required=True)
    parser.add_argument("--var_model", required=True)
    parser.add_argument("--source", default="default")
    parser.add_argument("--lead_months", type=int, default=12)
    parser.add_argument("--smoothing", default="raw")
    args = parser.parse_args()

    try:
        y = int(args.year)
        m = int(args.month)
        if not (1 <= m <= 12):
            raise ValueError("month 必须在 1-12")
    except ValueError as e:
        print("参数错误: {}".format(e), file=sys.stderr)
        sys.exit(2)

    lead_n = max(1, min(args.lead_months, 12))

    try:
        if args.dataset == "ENSO":
            raw_data, source_meta = fetch_enso_series(y, m, lead_n)
        else:
            raw_data, source_meta = fetch_nao_series(y, m, lead_n)
    except FetchError as e:
        print("NOAA 数据获取失败: {}".format(e), file=sys.stderr)
        sys.exit(1)

    final_data = apply_smoothing(raw_data, args.smoothing)

    payload = {
        "status": "ok",
        "metadata": {
            "dataset": args.dataset,
            "year": args.year,
            "month": args.month,
            "var_model": args.var_model,
            "source": source_meta["source"],
            "source_type": source_meta["source_type"],
            "lead_months": lead_n,
            "available_months": len(raw_data),
            "smoothing": args.smoothing,
        },
        "data": final_data,
    }
    print(json.dumps(payload, ensure_ascii=False))


if __name__ == "__main__":
    main()
