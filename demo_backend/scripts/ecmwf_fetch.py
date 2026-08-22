#!/usr/bin/env python3
"""Download one ECMWF Open Data field and decode it to JSON.

The script intentionally retrieves a single param/step/level at a time because the
existing TianXing database stores one model variable per row.
"""

from __future__ import annotations

import argparse
import json
import os
import tempfile
from typing import Any, Dict, List, Optional

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


def decode_grib(path: str) -> Dict[str, Any]:
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

                fields.append(json_safe_array(values))
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

    # 请求单字段时通常只有一条 GRIB message；若有多条则保留第一维 message 维度。
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
        decoded = decode_grib(grib_path)
        raw_data = decoded["data"]
        total_grid_points = int(np.asarray(raw_data).size) if raw_data is not None else 0
        metadata = {
            "source": args.source,
            "model": args.model,
            "request": request,
            "totalGridPoints": total_grid_points,
            "forecastDatetime": getattr(result, "datetime", None).isoformat()
            if getattr(result, "datetime", None) is not None
            else None,
            "fields": decoded["fieldMetadata"],
        }
        payload = {"data": raw_data, "metadata": metadata}

        with open(args.output, "w", encoding="utf-8") as output:
            json.dump(payload, output, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
    finally:
        try:
            os.remove(grib_path)
        except OSError:
            pass


if __name__ == "__main__":
    main()
