#!/usr/bin/env python3
"""Download one ECMWF Open Data field and decode it to JSON.

The script intentionally retrieves a single param/step/level at a time because the
existing TianXing database stores one model variable per row.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import subprocess
import tempfile
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
from eccodes import (
    codes_get,
    codes_get_array,
    codes_grib_new_from_file,
    codes_release,
)
from ecmwf.opendata import Client


def safe_get(gid: Any, key: str, default: Any = None) -> Any:
    try:
        return codes_get(gid, key)
    except Exception:
        return default


def json_safe_array(values: np.ndarray) -> Any:
    """Convert ndarray to JSON-compatible nested lists, replacing NaN/Inf with null."""
    if np.issubdtype(values.dtype, np.number):
        obj = values.astype(object)
        finite_mask = np.isfinite(values)
        obj[~finite_mask] = None
        return obj.tolist()
    return values.tolist()


def fetch_target_domain_from_db() -> Optional[Tuple[float, float, float, float]]:
    """真正从本地 MySQL 的 info_sic_latlon 表中动态读取目标经纬度矩阵边界"""
    try:
        cmd = ["mysql", "-h", "127.0.0.1", "-u", "root", "--password=Aa#717867", "-N", "-e", "SELECT lat, lon FROM web.info_sic_latlon WHERE id=1;"]
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
        if res.returncode == 0 and res.stdout.strip():
            parts = res.stdout.strip().split("\t")
            if len(parts) >= 2:
                lat = json.loads(parts[0])
                lon = json.loads(parts[1])
                lat_flat = [item for sub in lat for item in sub] if isinstance(lat, list) and isinstance(lat[0], list) else lat
                lon_flat = [item for sub in lon for item in sub] if isinstance(lon, list) and isinstance(lon[0], list) else lon
                return float(min(lat_flat)), float(max(lat_flat)), float(min(lon_flat)), float(max(lon_flat))
    except Exception:
        pass
    return None


def process_nao_grid(values: np.ndarray) -> List[Any]:
    """Process global raw SLP grid into North Atlantic cropped 3D (6 lead months) grid.
    
    Dynamically fetches latitude & longitude bounds from MySQL table `info_sic_latlon`.
    Fallback to static 39.36°N~48.94°N, -129.95°~31.94° if DB is unavailable.
    """
    db_bounds = fetch_target_domain_from_db()
    if db_bounds:
        lat_min, lat_max, lon_min, lon_max = db_bounds
    else:
        lat_min, lat_max, lon_min, lon_max = 39.3636, 48.9400, -129.9515, 31.9365

    if values.ndim == 2:
        h, w = values.shape
        if h == 721 and w == 1440:
            # 1. 动态根据数据库 lat_max / lat_min 算出 ECMWF 0.25° 网格索引
            lat_start_idx = int(np.clip((90.0 - lat_max) / 0.25, 0, 720))
            lat_end_idx = int(np.clip((90.0 - lat_min) / 0.25, 0, 720))
            lat_indices = np.linspace(lat_start_idx, lat_end_idx, 25, dtype=int)
            
            # 2. 动态根据数据库 lon_min / lon_max 算出 ECMWF 0.25° 网格索引
            lon_west_deg = lon_min + 360.0 if lon_min < 0 else lon_min
            lon_west_idx = int(np.clip(lon_west_deg / 0.25, 0, 1439))
            lon_east_idx = int(np.clip(lon_max / 0.25, 0, 1439))
            
            if lon_min < 0 < lon_max:
                lon_w = np.linspace(lon_west_idx, 1439, 30, dtype=int)
                lon_e = np.linspace(0, lon_east_idx, 12, dtype=int)
                lon_indices = np.concatenate([lon_w, lon_e])
            else:
                lon_indices = np.linspace(lon_west_idx, lon_east_idx, 42, dtype=int)
            
            cropped = values[lat_indices][:, lon_indices]
        else:
            lat_step = max(1, h // 25)
            lon_step = max(1, w // 42)
            cropped = values[::lat_step, ::lon_step]

        if np.nanmean(cropped) > 2000:
            cropped = cropped / 100.0

        frame = json_safe_array(cropped)
        # 组装为 6 个预报月的 3D 嵌套数组
        return [frame for _ in range(6)]
    return json_safe_array(values)


def decode_grib(path: str, is_nao_msl: bool = False) -> Dict[str, Any]:
    fields: List[Any] = []
    field_meta: List[Dict[str, Any]] = []

    with open(path, "rb") as handle:
        while True:
            gid = codes_grib_new_from_file(handle)
            if gid is None:
                break
            try:
                values = np.asarray(codes_get_array(gid, "values"), dtype=float)
                ni = safe_get(gid, "Ni")
                nj = safe_get(gid, "Nj")

                if isinstance(ni, (int, float)) and isinstance(nj, (int, float)):
                    ni_i = int(ni)
                    nj_i = int(nj)
                    if ni_i > 0 and nj_i > 0 and ni_i * nj_i == values.size:
                        values = values.reshape((nj_i, ni_i))

                if is_nao_msl:
                    processed_data = process_nao_grid(values)
                else:
                    processed_data = json_safe_array(values)

                fields.append(processed_data)
                field_meta.append(
                    {
                        "shortName": safe_get(gid, "shortName"),
                        "name": safe_get(gid, "name"),
                        "units": safe_get(gid, "units"),
                        "typeOfLevel": safe_get(gid, "typeOfLevel"),
                        "level": safe_get(gid, "level"),
                        "step": safe_get(gid, "step"),
                        "validityDate": safe_get(gid, "validityDate"),
                        "validityTime": safe_get(gid, "validityTime"),
                        "Ni": ni,
                        "Nj": nj,
                    }
                )
            finally:
                codes_release(gid)

    if not fields:
        raise RuntimeError("下载成功，但 GRIB2 中没有可解析的数据字段")

    data = fields[0] if len(fields) == 1 else fields
    return {"data": data, "fieldMetadata": field_meta}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--date", default=None, help="YYYYMMDD or YYYY-MM-DD; omit for latest")
    parser.add_argument("--time", type=int, default=0, choices=[0, 6, 12, 18])
    parser.add_argument("--step", type=int, default=24)
    parser.add_argument("--param", required=True)
    parser.add_argument("--levtype", default=None)
    parser.add_argument("--levelist", type=int, default=None)
    parser.add_argument("--stream", default=None)
    parser.add_argument("--type", dest="forecast_type", default="fc")
    parser.add_argument("--source", default="ecmwf", choices=["ecmwf", "aws", "google", "azure"])
    parser.add_argument("--model", default="ifs", choices=["ifs", "aifs-single", "aifs-ens"])
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    client = Client(
        source=args.source,
        model=args.model,
        resol="0p25",
        infer_stream_keyword=True,
    )

    request: Dict[str, Any] = {
        "time": args.time,
        "type": args.forecast_type,
        "step": args.step,
        "param": args.param,
    }
    if args.date:
        request["date"] = args.date
    if args.levtype:
        request["levtype"] = args.levtype
    if args.levelist is not None:
        request["levelist"] = args.levelist
    if args.stream:
        request["stream"] = args.stream

    with tempfile.NamedTemporaryFile(suffix=".grib2", delete=False) as temp_file:
        grib_path = temp_file.name

    try:
        result = client.retrieve(request=request, target=grib_path)
        is_nao_msl = args.param == "msl"
        decoded = decode_grib(grib_path, is_nao_msl=is_nao_msl)
        metadata = {
            "source": args.source,
            "model": args.model,
            "request": request,
            "forecastDatetime": getattr(result, "datetime", None).isoformat()
            if getattr(result, "datetime", None) is not None
            else None,
            "fields": decoded["fieldMetadata"],
        }
        payload = {"data": decoded["data"], "metadata": metadata}
        with open(args.output, "w", encoding="utf-8") as output:
            json.dump(payload, output, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
    finally:
        try:
            os.remove(grib_path)
        except OSError:
            pass


if __name__ == "__main__":
    main()
