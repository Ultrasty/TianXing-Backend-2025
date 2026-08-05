#!/usr/bin/env python3
"""Compute traceable SIC/SIE verification metrics from official NSIDC products.

SIC uses NSIDC G10005 (MASAM2 V2) and reprojects the observation field to the
existing Ice-BCNet/MITgcm grid before computing metrics. SIE uses the monthly
Northern Hemisphere extent series from NSIDC G02135 V4.

The program deliberately emits only evaluation metrics and provenance. ECMWF
Open Data is handled by the separate raw-field preview pipeline.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import csv
import datetime as dt
import hashlib
import io
import json
import math
import os
from pathlib import Path
import sys
from typing import Any, Iterable
from urllib.parse import urlparse

import h5py
import numpy as np
from pyproj import CRS, Transformer
import requests


MASAM2_DATASET_ID = "G10005"
MASAM2_VERSION = "2"
MASAM2_DOI = "10.7265/bqd9-vm28"
MASAM2_URL = (
    "https://noaadata.apps.nsidc.org/NOAA/G10005_V2/Data/"
    "{year}/masam2_minconc40_{year}{month:02d}_v2.nc"
)
SEA_ICE_INDEX_DATASET_ID = "G02135"
SEA_ICE_INDEX_VERSION = "4"
SEA_ICE_INDEX_DOI = "10.7265/N5K072F8"
SEA_ICE_INDEX_URL = (
    "https://noaadata.apps.nsidc.org/NOAA/G02135/north/monthly/data/"
    "N_{month:02d}_extent_v4.0.csv"
)
ICE_THRESHOLD = 0.15
EARTH_RADIUS_METRES = 6_371_008.8


class EvaluationError(RuntimeError):
    pass


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_cache_name(url: str) -> str:
    name = Path(urlparse(url).path).name
    if not name or name in {".", ".."} or any(char in name for char in "/\\"):
        raise EvaluationError("NSIDC URL does not contain a safe file name")
    return name


def request_session() -> requests.Session:
    session = requests.Session()
    session.headers.update({"User-Agent": "TianXing-NSIDC-Evaluation/1.0"})
    return session


def remote_size(session: requests.Session, url: str) -> int:
    response = session.head(url, allow_redirects=True, timeout=(30, 60))
    response.raise_for_status()
    value = response.headers.get("Content-Length")
    if not value or not value.isdigit() or int(value) <= 0:
        raise EvaluationError(f"NSIDC response has no valid Content-Length: {url}")
    return int(value)


def download_small(session: requests.Session, url: str, target: Path, expected_size: int) -> None:
    response = session.get(url, timeout=(30, 180))
    response.raise_for_status()
    if len(response.content) != expected_size:
        raise EvaluationError(f"Incomplete NSIDC download: {url}")
    temporary = target.with_suffix(target.suffix + ".download")
    temporary.write_bytes(response.content)
    os.replace(temporary, target)


def download_parallel_ranges(
    session: requests.Session, url: str, target: Path, expected_size: int, workers: int
) -> None:
    workers = max(1, min(workers, 24, expected_size))
    chunk_size = math.ceil(expected_size / workers)

    def fetch(index: int) -> Path:
        start = index * chunk_size
        end = min(expected_size - 1, (index + 1) * chunk_size - 1)
        expected = end - start + 1
        part = target.with_suffix(target.suffix + f".part-{index:02d}")
        if part.exists() and part.stat().st_size == expected:
            return part
        response = session.get(
            url,
            headers={"Range": f"bytes={start}-{end}"},
            timeout=(30, 360),
        )
        response.raise_for_status()
        if response.status_code != 206 or len(response.content) != expected:
            raise EvaluationError(f"NSIDC server did not return requested byte range {index}")
        temporary_part = part.with_suffix(part.suffix + ".download")
        temporary_part.write_bytes(response.content)
        os.replace(temporary_part, part)
        return part

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        parts = list(executor.map(fetch, range(workers)))

    temporary = target.with_suffix(target.suffix + ".download")
    with temporary.open("wb") as output:
        for part in parts:
            with part.open("rb") as source:
                for block in iter(lambda: source.read(1024 * 1024), b""):
                    output.write(block)
    if temporary.stat().st_size != expected_size:
        raise EvaluationError(f"Assembled NSIDC file has the wrong size: {url}")
    os.replace(temporary, target)
    for part in parts:
        part.unlink(missing_ok=True)


def download_cached(url: str, cache_dir: Path, workers: int = 12) -> tuple[Path, str]:
    cache_dir.mkdir(parents=True, exist_ok=True)
    target = (cache_dir / safe_cache_name(url)).resolve()
    if cache_dir.resolve() not in target.parents:
        raise EvaluationError("Resolved cache target escaped the configured cache directory")
    session = request_session()
    expected_size = remote_size(session, url)
    if not target.exists() or target.stat().st_size != expected_size:
        if expected_size < 2 * 1024 * 1024:
            download_small(session, url, target, expected_size)
        else:
            download_parallel_ranges(session, url, target, expected_size, workers)
    return target, sha256_file(target)


def as_float_array(value: Any, name: str, ndim: int | None = None) -> np.ndarray:
    try:
        array = np.asarray(value, dtype=np.float64)
    except (TypeError, ValueError) as exception:
        raise EvaluationError(f"{name} is not a numeric array") from exception
    if ndim is not None and array.ndim != ndim:
        raise EvaluationError(f"{name} must have {ndim} dimensions")
    return array


def normalize_sic(array: np.ndarray) -> np.ndarray:
    finite = array[np.isfinite(array)]
    if finite.size == 0:
        raise EvaluationError("SIC prediction contains no finite value")
    result = array.copy()
    if float(np.nanmax(np.abs(finite))) > 1.5:
        result /= 100.0
    return np.clip(result, 0.0, 1.0)


def spherical_cell_areas(latitude: np.ndarray, longitude: np.ndarray) -> np.ndarray:
    """Approximate curvilinear cell area from centre-coordinate derivatives."""
    lat = np.deg2rad(latitude)
    lon = np.deg2rad(longitude)
    xyz = np.stack(
        (np.cos(lat) * np.cos(lon), np.cos(lat) * np.sin(lon), np.sin(lat)), axis=-1
    )
    derivative_row = np.gradient(xyz, axis=0)
    derivative_col = np.gradient(xyz, axis=1)
    cross = np.cross(derivative_row, derivative_col)
    area = (EARTH_RADIUS_METRES**2) * np.linalg.norm(cross, axis=-1)
    area[~np.isfinite(area) | (area <= 0)] = np.nan
    return area


def decode_attr(value: Any) -> str:
    if isinstance(value, bytes):
        return value.decode("utf-8")
    if isinstance(value, np.bytes_):
        return bytes(value).decode("utf-8")
    return str(value)


def bilinear_regrid_masam2(
    netcdf_path: Path, valid_date: dt.date, target_lat: np.ndarray, target_lon: np.ndarray
) -> np.ndarray:
    with h5py.File(netcdf_path, "r") as dataset:
        times = np.asarray(dataset["time"][:], dtype=np.int64)
        epoch = dt.date(1970, 1, 1)
        wanted = (valid_date - epoch).days
        matching = np.flatnonzero(times == wanted)
        if matching.size != 1:
            raise EvaluationError(f"MASAM2 does not contain observation date {valid_date.isoformat()}")

        x = np.asarray(dataset["x"][:], dtype=np.float64)
        y = np.asarray(dataset["y"][:], dtype=np.float64)
        raw = np.asarray(dataset["sea_ice_concentration"][int(matching[0])], dtype=np.float64)
        proj4 = (
            decode_attr(dataset["crs"].attrs["proj4text"])
            .replace("+x_0=0+y_0=0", "+x_0=0 +y_0=0")
            .replace("+no_defs=True", "+no_defs")
        )

    transformer = Transformer.from_crs(CRS.from_epsg(4326), CRS.from_proj4(proj4), always_xy=True)
    target_x, target_y = transformer.transform(target_lon, target_lat)
    dx = x[1] - x[0]
    dy = y[1] - y[0]
    fractional_x = (target_x - x[0]) / dx
    fractional_y = (target_y - y[0]) / dy
    x0 = np.floor(fractional_x).astype(np.int64)
    y0 = np.floor(fractional_y).astype(np.int64)
    inside = (
        np.isfinite(fractional_x)
        & np.isfinite(fractional_y)
        & (x0 >= 0)
        & (x0 + 1 < x.size)
        & (y0 >= 0)
        & (y0 + 1 < y.size)
    )

    result = np.full(target_lat.shape, np.nan, dtype=np.float64)
    flat_inside = np.flatnonzero(inside.ravel())
    if flat_inside.size == 0:
        return result
    flat_x0 = x0.ravel()[flat_inside]
    flat_y0 = y0.ravel()[flat_inside]
    wx = fractional_x.ravel()[flat_inside] - flat_x0
    wy = fractional_y.ravel()[flat_inside] - flat_y0
    v00 = raw[flat_y0, flat_x0]
    v10 = raw[flat_y0, flat_x0 + 1]
    v01 = raw[flat_y0 + 1, flat_x0]
    v11 = raw[flat_y0 + 1, flat_x0 + 1]
    valid = (v00 <= 100) & (v10 <= 100) & (v01 <= 100) & (v11 <= 100)
    values = (
        v00 * (1 - wx) * (1 - wy)
        + v10 * wx * (1 - wy)
        + v01 * (1 - wx) * wy
        + v11 * wx * wy
    ) / 100.0
    target_flat = result.ravel()
    target_flat[flat_inside[valid]] = values[valid]
    return result


def weighted_sic_metrics(
    prediction: np.ndarray, observation: np.ndarray, area: np.ndarray
) -> dict[str, float | int]:
    valid = np.isfinite(prediction) & np.isfinite(observation) & np.isfinite(area) & (area > 0)
    if not np.any(valid):
        raise EvaluationError("Prediction and NSIDC observation have no matched valid ocean cells")
    weights = area[valid]
    predicted = prediction[valid]
    observed = observation[valid]
    rmse = math.sqrt(float(np.average((predicted - observed) ** 2, weights=weights)))
    predicted_ice = predicted >= ICE_THRESHOLD
    observed_ice = observed >= ICE_THRESHOLD
    true_positive = float(weights[predicted_ice & observed_ice].sum())
    false_negative = float(weights[~predicted_ice & observed_ice].sum())
    true_negative = float(weights[~predicted_ice & ~observed_ice].sum())
    false_positive = float(weights[predicted_ice & ~observed_ice].sum())
    positive_total = true_positive + false_negative
    negative_total = true_negative + false_positive
    if positive_total <= 0 or negative_total <= 0:
        raise EvaluationError("BACC requires both ice and open-water observation cells")
    sensitivity = true_positive / positive_total
    specificity = true_negative / negative_total
    balanced_accuracy = (sensitivity + specificity) / 2.0
    return {
        "rmsePercent": round(rmse * 100.0, 6),
        "baccPercent": round(balanced_accuracy * 100.0, 6),
        "validCellCount": int(valid.sum()),
        "validAreaKm2": round(float(weights.sum()) / 1_000_000.0, 3),
        "sensitivityPercent": round(sensitivity * 100.0, 6),
        "specificityPercent": round(specificity * 100.0, 6),
        "iieeKm2": round((false_positive + false_negative) / 1_000_000.0, 3),
    }


def evaluate_sic(payload: dict[str, Any], cache_dir: Path, workers: int) -> dict[str, Any]:
    try:
        start_date = dt.date(int(payload["year"]), int(payload["month"]), int(payload["day"]))
    except (KeyError, TypeError, ValueError) as exception:
        raise EvaluationError("SIC request contains an invalid start date") from exception
    offset = int(payload.get("leadStartOffsetDays", 0))
    if offset not in (0, 1):
        raise EvaluationError("leadStartOffsetDays must be 0 or 1")
    prediction = normalize_sic(as_float_array(payload.get("prediction"), "prediction", ndim=3))
    latitude = as_float_array(payload.get("latitude"), "latitude", ndim=2)
    longitude = as_float_array(payload.get("longitude"), "longitude", ndim=2)
    if latitude.shape != longitude.shape or prediction.shape[1:] != latitude.shape:
        raise EvaluationError("SIC prediction and model latitude/longitude shapes do not match")
    if prediction.shape[0] != 7:
        raise EvaluationError("SIC_Ice-BCNet prediction must contain exactly 7 lead days")
    cell_area = spherical_cell_areas(latitude, longitude)
    urls: dict[str, str] = {}
    hashes: dict[str, str] = {}
    downloaded: dict[tuple[int, int], Path] = {}
    diagnostics: list[dict[str, Any]] = []
    rmse_values: list[float] = []
    bacc_values: list[float] = []
    valid_dates: list[str] = []
    for lead in range(7):
        valid_date = start_date + dt.timedelta(days=offset + lead)
        key = (valid_date.year, valid_date.month)
        if key not in downloaded:
            url = MASAM2_URL.format(year=valid_date.year, month=valid_date.month)
            path, digest = download_cached(url, cache_dir, workers)
            downloaded[key] = path
            urls[f"{valid_date.year}-{valid_date.month:02d}"] = url
            hashes[f"{valid_date.year}-{valid_date.month:02d}"] = digest
        observation = bilinear_regrid_masam2(downloaded[key], valid_date, latitude, longitude)
        metrics = weighted_sic_metrics(prediction[lead], observation, cell_area)
        metrics["leadDay"] = lead + 1
        metrics["validDate"] = valid_date.isoformat()
        diagnostics.append(metrics)
        rmse_values.append(float(metrics["rmsePercent"]))
        bacc_values.append(float(metrics["baccPercent"]))
        valid_dates.append(valid_date.isoformat())

    year = str(start_date.year)
    return {
        "source": "NSIDC",
        "dataKind": "EVALUATION_METRIC",
        "category": "SIC",
        "predictionModel": "SIC_Ice-BCNet",
        "observation": {
            "datasetId": MASAM2_DATASET_ID,
            "name": "MASAM2 Daily 4 km Arctic Sea Ice Concentration",
            "version": MASAM2_VERSION,
            "doi": MASAM2_DOI,
            "accessedAt": utc_now(),
            "urls": urls,
            "sha256": hashes,
        },
        "matching": {
            "predictionGrid": list(latitude.shape),
            "observationGrid": [2550, 2100],
            "regridding": "bilinear in MASAM2 polar stereographic projection; invalid/land neighbours masked",
            "leadStartOffsetDays": offset,
            "validDates": valid_dates,
        },
        "metricDefinitions": {
            "RMSE": "area-weighted grid-cell RMSE, percentage points",
            "BACC": "area-weighted balanced accuracy at SIC >= 15%, percent",
        },
        "diagnostics": diagnostics,
        "records": [
            {
                "year": year,
                "month": str(start_date.month),
                "day": str(start_date.day),
                "varModel": f"{year}_RMSE",
                "data": rmse_values,
                "source": "NSIDC",
            },
            {
                "year": year,
                "month": str(start_date.month),
                "day": str(start_date.day),
                "varModel": f"{year}_BACC",
                "data": bacc_values,
                "source": "NSIDC",
            },
        ],
    }


def parse_extent_csv(content: str, observations: dict[tuple[int, int], float]) -> None:
    for row in csv.DictReader(io.StringIO(content)):
        try:
            year = int(row["year"].strip())
            month = int(row[" mo"].strip())
            extent = float(row[" extent"].strip())
        except (KeyError, TypeError, ValueError):
            continue
        if 0.0 < extent < 30.0:
            observations[(year, month)] = extent


def pearson(predicted: np.ndarray, observed: np.ndarray) -> float:
    if predicted.size < 2 or np.std(predicted) == 0 or np.std(observed) == 0:
        raise EvaluationError("SIE correlation requires at least two non-constant matched samples")
    return float(np.corrcoef(predicted, observed)[0, 1])


def compute_sie_lead_metrics(
    prediction_rows: Iterable[dict[str, Any]], observations: dict[tuple[int, int], float]
) -> tuple[dict[str, list[float]], list[dict[str, Any]]]:
    samples: list[list[tuple[float, float]]] = [[] for _ in range(12)]
    for row in prediction_rows:
        try:
            initial = dt.date(int(row["year"]), int(row["month"]), 1)
        except (KeyError, TypeError, ValueError) as exception:
            raise EvaluationError("SIE prediction row has invalid year/month") from exception
        values = as_float_array(row.get("data"), "prediction_IceTFT data", ndim=1)
        if values.size != 12:
            raise EvaluationError("Each prediction_IceTFT row must contain 12 monthly values")
        for lead, predicted in enumerate(values):
            month_index = initial.year * 12 + initial.month - 1 + lead
            valid_year, zero_based_month = divmod(month_index, 12)
            valid_month = zero_based_month + 1
            observed = observations.get((valid_year, valid_month))
            if observed is not None and np.isfinite(predicted):
                samples[lead].append((float(predicted), observed))

    metrics = {name: [] for name in ("RMSD", "BAIS", "VAR", "CORRELATION", "OBS_STD", "PRE_STD")}
    diagnostics: list[dict[str, Any]] = []
    for lead, pairs in enumerate(samples):
        if len(pairs) < 2:
            raise EvaluationError(f"SIE lead {lead + 1} has fewer than two matched prediction/NSIDC samples")
        array = np.asarray(pairs, dtype=np.float64)
        predicted = array[:, 0]
        observed = array[:, 1]
        error = predicted - observed
        mean_bias = float(np.mean(error))
        rmsd = math.sqrt(float(np.mean(error**2)))
        bias_squared = mean_bias**2
        error_variance = float(np.mean((error - mean_bias) ** 2))
        correlation = pearson(predicted, observed)
        obs_std = float(np.std(observed))
        pred_std = float(np.std(predicted))
        values = (rmsd, bias_squared, error_variance, correlation, obs_std, pred_std)
        for name, value in zip(metrics, values):
            metrics[name].append(round(value, 6))
        diagnostics.append(
            {
                "leadMonth": lead + 1,
                "sampleCount": len(pairs),
                "meanBiasMillionKm2": round(mean_bias, 6),
                "rmsdMillionKm2": round(rmsd, 6),
            }
        )
    return metrics, diagnostics


def evaluate_sie(payload: dict[str, Any], cache_dir: Path, workers: int) -> dict[str, Any]:
    try:
        cohort_year = int(payload["year"])
    except (KeyError, TypeError, ValueError) as exception:
        raise EvaluationError("SIE request contains an invalid cohort year") from exception
    prediction_rows = payload.get("predictions")
    if not isinstance(prediction_rows, list) or not prediction_rows:
        raise EvaluationError("No prediction_IceTFT rows were provided")
    observations: dict[tuple[int, int], float] = {}
    urls: dict[str, str] = {}
    hashes: dict[str, str] = {}
    for month in range(1, 13):
        url = SEA_ICE_INDEX_URL.format(month=month)
        path, digest = download_cached(url, cache_dir, min(workers, 4))
        parse_extent_csv(path.read_text(encoding="utf-8-sig"), observations)
        urls[f"month-{month:02d}"] = url
        hashes[f"month-{month:02d}"] = digest
    metrics, diagnostics = compute_sie_lead_metrics(prediction_rows, observations)
    records = [
        {
            "year": str(cohort_year),
            "month": "1",
            "varModel": name,
            "data": values,
            "source": "NSIDC",
        }
        for name, values in metrics.items()
    ]
    return {
        "source": "NSIDC",
        "dataKind": "EVALUATION_METRIC",
        "category": "SIE",
        "predictionModel": "prediction_IceTFT",
        "observation": {
            "datasetId": SEA_ICE_INDEX_DATASET_ID,
            "name": "Sea Ice Index Monthly Northern Hemisphere Extent",
            "version": SEA_ICE_INDEX_VERSION,
            "doi": SEA_ICE_INDEX_DOI,
            "units": "million km2",
            "accessedAt": utc_now(),
            "urls": urls,
            "sha256": hashes,
        },
        "matching": {
            "cohortYear": cohort_year,
            "initializationMonths": sorted(int(row["month"]) for row in prediction_rows),
            "leadMonths": list(range(1, 13)),
            "validMonthRule": "forecast array index 0 is initialization month",
        },
        "metricDefinitions": {
            "RMSD": "root mean squared difference by lead month, million km2",
            "BAIS": "squared mean bias by lead month, (million km2)^2",
            "VAR": "population variance of forecast error by lead month, (million km2)^2",
            "CORRELATION": "Pearson correlation by lead month",
            "OBS_STD": "population standard deviation of NSIDC observations, million km2",
            "PRE_STD": "population standard deviation of IceTFT predictions, million km2",
        },
        "diagnostics": diagnostics,
        "records": records,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="JSON input produced by the backend")
    parser.add_argument("--output", required=True, help="JSON result consumed by the backend")
    parser.add_argument("--cache-dir", required=True, help="Directory for verified NSIDC source files")
    parser.add_argument("--download-workers", type=int, default=12)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.download_workers < 1 or args.download_workers > 24:
        raise EvaluationError("download-workers must be between 1 and 24")
    with Path(args.input).open("r", encoding="utf-8") as stream:
        payload = json.load(stream)
    mode = str(payload.get("mode", "")).upper()
    cache_dir = Path(args.cache_dir)
    if mode == "SIC":
        result = evaluate_sic(payload, cache_dir, args.download_workers)
    elif mode == "SIE":
        result = evaluate_sie(payload, cache_dir, args.download_workers)
    else:
        raise EvaluationError("mode must be SIC or SIE")
    with Path(args.output).open("w", encoding="utf-8") as stream:
        json.dump(result, stream, ensure_ascii=False, allow_nan=False, separators=(",", ":"))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (EvaluationError, requests.RequestException, OSError, ValueError) as error:
        print(f"NSIDC evaluation failed: {error}", file=sys.stderr)
        raise SystemExit(2)
