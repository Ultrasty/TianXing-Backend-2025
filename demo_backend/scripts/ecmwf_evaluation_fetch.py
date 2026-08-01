#!/usr/bin/env python3
"""Fetch one ECMWF Open Data field and reduce it to a bounded JSON array.

This helper deliberately does not claim to calculate a scientific verification
score. It provides a deterministic bridge from a real GRIB2 field to the
TianXing evaluation import contract. Domain-specific verification pipelines can
replace this helper while preserving the Java API contract.
"""

from __future__ import annotations

import argparse
import json
import os
import tempfile
from typing import Any, Dict, List, Tuple

import numpy as np
from eccodes import codes_get, codes_get_array, codes_grib_new_from_file, codes_release
from ecmwf.opendata import Client


def safe_get(gid: Any, key: str, default: Any = None) -> Any:
    try:
        return codes_get(gid, key)
    except Exception:
        return default


def reduce_values(values: np.ndarray, ni: Any, nj: Any, reducer: str, max_points: int) -> List[float]:
    flat = np.asarray(values, dtype=float).reshape(-1)
    finite = flat[np.isfinite(flat)]
    if finite.size == 0:
        raise RuntimeError("GRIB2 field contains no finite numeric values")

    if reducer == "MEAN":
        return [float(np.mean(finite))]

    if reducer == "ROW_MEAN":
        if isinstance(ni, (int, float)) and isinstance(nj, (int, float)):
            ni_i, nj_i = int(ni), int(nj)
            if ni_i > 0 and nj_i > 0 and ni_i * nj_i == flat.size:
                grid = flat.reshape((nj_i, ni_i))
                with np.errstate(invalid="ignore"):
                    row_means = np.nanmean(np.where(np.isfinite(grid), grid, np.nan), axis=1)
                output = row_means[np.isfinite(row_means)]
                if output.size:
                    return [float(value) for value in output]
        return [float(np.mean(finite))]

    count = min(max_points, int(finite.size))
    if count == 1:
        return [float(finite[0])]
    indexes = np.linspace(0, finite.size - 1, num=count, dtype=int)
    return [float(value) for value in finite[indexes]]


def decode_grib(path: str, reducer: str, max_points: int) -> Tuple[List[float], List[Dict[str, Any]], int]:
    outputs: List[List[float]] = []
    field_metadata: List[Dict[str, Any]] = []
    total_points = 0

    with open(path, "rb") as handle:
        while True:
            gid = codes_grib_new_from_file(handle)
            if gid is None:
                break
            try:
                values = np.asarray(codes_get_array(gid, "values"), dtype=float)
                ni, nj = safe_get(gid, "Ni"), safe_get(gid, "Nj")
                total_points += int(values.size)
                outputs.append(reduce_values(values, ni, nj, reducer, max_points))
                field_metadata.append(
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

    if not outputs:
        raise RuntimeError("download succeeded but GRIB2 contained no decodable fields")

    data = outputs[0] if len(outputs) == 1 else [value for output in outputs for value in output]
    return data, field_metadata, total_points


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
    parser.add_argument("--reducer", default="MEAN", choices=["MEAN", "ROW_MEAN", "SAMPLE"])
    parser.add_argument("--max-points", type=int, default=200)
    return parser.parse_args()


def retrieve_with_fallback(args: argparse.Namespace, request: Dict[str, Any], target: str) -> Tuple[Any, str, List[str]]:
    providers = [args.source]
    if args.source == "ecmwf":
        providers.extend(["aws", "google", "azure"])
    failures: List[str] = []
    for provider in providers:
        try:
            with open(target, "wb"):
                pass
            client = Client(source=provider, model=args.model, resol="0p25", infer_stream_keyword=True)
            result = client.retrieve(request=request, target=target)
            return result, provider, failures
        except Exception as error:
            failures.append(f"{provider}: {error.__class__.__name__}: {str(error)[:240]}")
    raise RuntimeError("all ECMWF Open Data providers failed: " + " | ".join(failures))


def main() -> None:
    args = parse_args()
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

    with tempfile.NamedTemporaryFile(suffix=".grib2", delete=False) as temporary:
        grib_path = temporary.name

    try:
        result, provider_used, fallback_failures = retrieve_with_fallback(args, request, grib_path)
        data, fields, total_points = decode_grib(grib_path, args.reducer, args.max_points)
        result_datetime = getattr(result, "datetime", None)
        payload = {
            "data": data,
            "metadata": {
                "source": provider_used,
                "requestedSource": args.source,
                "fallbackFailures": fallback_failures,
                "model": args.model,
                "request": request,
                "forecastDatetime": result_datetime.isoformat() if result_datetime is not None else None,
                "reducer": args.reducer,
                "totalGridPoints": total_points,
                "outputPoints": len(data),
                "fields": fields,
                "notice": "Reduced ECMWF model field; not a domain verification score",
            },
        }
        with open(args.output, "w", encoding="utf-8") as output:
            json.dump(payload, output, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
    finally:
        try:
            os.remove(grib_path)
        except OSError:
            pass


if __name__ == "__main__":
    main()
