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
import sys
import tempfile
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
from eccodes import (
    codes_get,
    codes_get_array,
    codes_grib_new_from_file,
    codes_release,
)
from ecmwf.opendata import Client

try:
    import cdsapi as _cdsapi
except ImportError:
    _cdsapi = None

# ---------------------------------------------------------------------------
# Monkey-patch: ecmwf-opendata 库 BUG 修复
#
# 库内部 user_to_url() 将 stream="mmsa" 映射为 "mmsf" 用于 URL 构造，
# 但 PATTERNS 字典只有 {"mmsa": MONTHLY_PATTERN}，缺少 "mmsf" 映射，
# 导致 fallback 到 HOURLY_PATTERN（需要 {step} 占位符），引发 KeyError: 'step'。
# 这里在模块导入后立刻补上缺失的映射。
# ---------------------------------------------------------------------------
import ecmwf.opendata.client as _ecmwf_client_mod

if "mmsf" not in _ecmwf_client_mod.PATTERNS:
    _ecmwf_client_mod.PATTERNS["mmsf"] = _ecmwf_client_mod.MONTHLY_PATTERN


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




def process_single_nao_frame(values: np.ndarray) -> List[Any]:
    """Crop a single 2D global SLP grid to North Atlantic 13×27 and convert Pa→hPa."""
    if values.ndim == 2:
        h, w = values.shape
        # 硬编码北大西洋 NAO 区域，因为之前的 info_sic_latlon 是海冰专属的 384x420 区域
        lat_min, lat_max, lon_min, lon_max = 20.0, 80.0, -80.0, 40.0

        if h == 721 and w == 1440:
            lat_start_idx = int(np.clip((90.0 - lat_max) / 0.25, 0, 720))
            lat_end_idx = int(np.clip((90.0 - lat_min) / 0.25, 0, 720))
            lat_indices = np.linspace(lat_start_idx, lat_end_idx, 13, dtype=int)
            
            lon_west_deg = lon_min + 360.0 if lon_min < 0 else lon_min
            lon_west_idx = int(np.clip(lon_west_deg / 0.25, 0, 1439))
            lon_east_idx = int(np.clip(lon_max / 0.25, 0, 1439))
            
            if lon_min < 0 < lon_max:
                lon_w = np.linspace(lon_west_idx, 1439, 18, dtype=int)
                lon_e = np.linspace(0, lon_east_idx, 9, dtype=int)
                lon_indices = np.concatenate([lon_w, lon_e])
            else:
                lon_indices = np.linspace(lon_west_idx, lon_east_idx, 27, dtype=int)
            
            cropped = values[lat_indices][:, lon_indices]
        elif h == 180 and w == 360:
            # Copernicus CDS 1° grid (lat: 89.5 to -89.5, lon: 0.5 to 359.5)
            lat_start_idx = int(np.clip((89.5 - lat_max) / 1.0, 0, 179))
            lat_end_idx = int(np.clip((89.5 - lat_min) / 1.0, 0, 179))
            lat_indices = np.linspace(lat_start_idx, lat_end_idx, 13, dtype=int)
            
            lon_west_deg = lon_min + 360.0 if lon_min < 0 else lon_min
            lon_west_idx = int(np.clip((lon_west_deg - 0.5) / 1.0, 0, 359))
            lon_east_idx = int(np.clip((lon_max - 0.5) / 1.0, 0, 359))
            
            if lon_min < 0 < lon_max:
                lon_w = np.linspace(lon_west_idx, 359, 18, dtype=int)
                lon_e = np.linspace(0, lon_east_idx, 9, dtype=int)
                lon_indices = np.concatenate([lon_w, lon_e])
            else:
                lon_indices = np.linspace(lon_west_idx, lon_east_idx, 27, dtype=int)
            
            cropped = values[lat_indices][:, lon_indices]
        else:
            lat_step = max(1, h // 13)
            lon_step = max(1, w // 27)
            cropped = values[::lat_step, ::lon_step]

        # Pa → hPa 转换（ECMWF 海平面气压以 Pa 为单位，约 101325 Pa = 1013.25 hPa）
        if np.nanmean(cropped) > 2000:
            cropped = cropped / 100.0

        return json_safe_array(cropped)
    return json_safe_array(values)


def decode_grib_single(path: str) -> Dict[str, Any]:
    """Decode a single GRIB file into raw 2D grid + metadata (no NAO post-processing)."""
    fields: List[np.ndarray] = []
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

                fields.append(values)
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

    return {"fields": fields, "fieldMetadata": field_meta}


def decode_grib(path: str, is_nao_msl: bool = False) -> Dict[str, Any]:
    """Decode GRIB and optionally post-process for NAO (legacy single-file path)."""
    raw = decode_grib_single(path)
    processed: List[Any] = []
    for values in raw["fields"]:
        if is_nao_msl:
            # 单文件路径：对每个 field 做 NAO 裁切，复制成 6 帧（兜底逻辑）
            frame = process_single_nao_frame(values)
            processed.append(frame)
        else:
            processed.append(json_safe_array(values))

    data = processed[0] if len(processed) == 1 else processed
    return {"data": data, "fieldMetadata": raw["fieldMetadata"]}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Download & Decode ECMWF Open Data")
    parser.add_argument("--date", default=None)
    parser.add_argument("--time", type=int, default=0)
    parser.add_argument("--step", type=int, default=24)
    parser.add_argument("--param", default="2t")
    parser.add_argument("--levtype", default=None)
    parser.add_argument("--levelist", type=int, default=None)
    parser.add_argument("--stream", default=None)
    parser.add_argument("--type", dest="forecast_type", default="fc")
    parser.add_argument("--source", default="ecmwf", choices=["ecmwf", "aws", "google", "azure", "cds"])
    parser.add_argument("--model", default="ifs", choices=["ifs", "aifs-single", "aifs-ens"])
    parser.add_argument("--dataset", default=None)
    parser.add_argument("--var_model", default=None)
    parser.add_argument("--fcmonth", default="1/2/3/4/5/6",
                        help="Forecast lead months, slash-separated (e.g. 1/2/3/4/5/6)")
    parser.add_argument("--output", required=True)
    return parser.parse_args()


def _build_client(src: str, model: str) -> Client:
    """Construct an ecmwf.opendata.Client for the given source."""
    return Client(
        source=src,
        model=model,
        resol="0p25",
    )


def _compute_date(args: argparse.Namespace, monthly: bool = False) -> str:
    """Return an explicit 8-digit date string, avoiding the library's self.latest() call.

    For monthly seasonal forecasts (stream=mmsa), ECMWF only produces data on the
    1st of each month. If no date is given, we default to the 1st of the current month.
    For regular forecasts, we default to yesterday.
    """
    if args.date:
        return args.date
    if monthly:
        # 季节预报月度平均仅在每月 1 号起报
        first_of_month = datetime.now().replace(day=1)
        return first_of_month.strftime("%Y%m%d")
    yesterday = datetime.now() - timedelta(days=1)
    return yesterday.strftime("%Y%m%d")


def _do_retrieve(client: Client, request_params: Dict[str, Any]) -> str:
    """Call client.retrieve() into a temp file and return the path."""
    tmp_grib = tempfile.NamedTemporaryFile(delete=False, suffix=".grib2")
    tmp_grib.close()
    client.retrieve(request_params, tmp_grib.name)
    return tmp_grib.name


def download_nao_monthly(args: argparse.Namespace) -> str:
    """Download seasonal forecast monthly-mean MSL from Copernicus CDS.

    Uses the 'seasonal-monthly-single-levels' dataset via cdsapi.
    Returns path to a single GRIB file containing one field per leadtime month.
    """
    if _cdsapi is None:
        raise RuntimeError(
            "cdsapi 未安装。请执行 pip install cdsapi 并配置 ~/.cdsapirc"
        )

    # 解析 fcmonth 列表 (如 "1/2/3/4/5/6" → ["1", "2", ..., "6"])
    leadtime_months = [m.strip() for m in args.fcmonth.split("/")]

    # 确定起报年月：优先使用用户显式传入的 date (YYYYMMDD)，否则取当月 1 号
    if args.date:
        base_date = datetime.strptime(args.date, "%Y%m%d")
        allow_fallback = False  # 用户指定了精确日期，不进行回退
    else:
        base_date = datetime.now().replace(day=1)
        allow_fallback = True   # 自动计算的日期，如果未发布则允许回退到上个月

    # CDS 变量名映射
    CDS_VAR_MAP = {
        "msl": "mean_sea_level_pressure",
        "2t": "2m_temperature",
        "skt": "skin_temperature",
        "ci": "sea_ice_cover",
    }
    cds_variable = CDS_VAR_MAP.get(args.param, args.param)

    client = _cdsapi.Client()
    tmp_grib = tempfile.NamedTemporaryFile(delete=False, suffix=".grib2")
    tmp_grib.close()

    # 最大尝试次数：如果允许回退，我们尝试当前月和前一个月；否则只尝试一次
    max_attempts = 2 if allow_fallback else 1
    current_date = base_date
    last_error = None

    for attempt in range(max_attempts):
        year_str = str(current_date.year)
        month_str = str(current_date.month)

        request = {
            "originating_centre": "ecmwf",
            "system": "51",
            "variable": cds_variable,
            "product_type": "ensemble_mean",
            "year": year_str,
            "month": month_str.zfill(2),
            "leadtime_month": leadtime_months,
            "data_format": "grib",
        }

        print(f"尝试 CDS 请求 [第 {attempt + 1} 次/共 {max_attempts} 次]: dataset=seasonal-monthly-single-levels, "
              f"variable={cds_variable}, year={year_str}, month={month_str}, "
              f"leadtime_month={leadtime_months}", file=sys.stderr)

        try:
            client.retrieve(
                "seasonal-monthly-single-levels",
                request,
                tmp_grib.name,
            )
            print(f"CDS 下载成功: {tmp_grib.name}", file=sys.stderr)
            return tmp_grib.name
        except Exception as e:
            last_error = e
            # 只有当错误可能是因为未发布（400/Bad Request/combination）时才尝试回退
            is_bad_request = "400" in str(e) or "combination" in str(e).lower() or "invalid request" in str(e).lower()
            if allow_fallback and attempt == 0 and is_bad_request:
                # 回退到上个月
                # 先用第一天减去1天，就会得到上个月的最后一天，从而切换到上个月的年份和月份
                prev_month = base_date - timedelta(days=1)
                current_date = prev_month.replace(day=1)
                print(f"Warning: 当前月预报可能尚未发布，正在自动回退到上个月数据 ({current_date.strftime('%Y%m')})...", file=sys.stderr)
                continue
            else:
                break

    try:
        os.remove(tmp_grib.name)
    except OSError:
        pass
    raise RuntimeError(f"CDS 季节预报下载失败: {last_error}") from last_error


def download_field(args: argparse.Namespace) -> Tuple[str, str]:
    """Download a single GRIB file for non-monthly-mean requests (hourly forecasts etc.)."""
    sources_to_try = [args.source]
    for backup in ["aws", "azure", "google", "ecmwf"]:
        if backup not in sources_to_try:
            sources_to_try.append(backup)

    date_str = _compute_date(args)
    last_error: Exception | None = None

    for src in sources_to_try:
        try:
            client = _build_client(src, args.model)
            request_params: Dict[str, Any] = {
                "date": date_str,
                "time": args.time,
                "step": args.step,
                "param": args.param,
                "type": args.forecast_type,
            }

            if args.stream:
                request_params["stream"] = args.stream

            # 月度流不带 step（仍然保留兜底，万一用户手动传了 stream=mmsa 但不走 NAO 路径）
            if request_params.get("stream") in ["mmsa", "mmsf"]:
                request_params.pop("step", None)

            if args.levtype:
                request_params["levtype"] = args.levtype
            if args.levelist is not None:
                request_params["levelist"] = args.levelist

            path = _do_retrieve(client, request_params)
            return path, src
        except Exception as e:
            last_error = e
            print(f"Warning: 数据源 [{src}] 获取失败 ({e})，正在自动无感降级切换下一个镜像源...", file=sys.stderr)

    if last_error:
        raise last_error
    raise RuntimeError("所有 ECMWF 镜像源均尝试失败")


def _is_nao_monthly(args: argparse.Namespace) -> bool:
    """Check if this request is for NAO monthly-mean grid data."""
    # 条件1：显式 dataset+var_model 标记
    if args.dataset == "NAO" and args.var_model == "grid_NAO_MCD":
        return True
    # 条件2：stream=mmsa + param=msl（即使 Java 没传 dataset/var_model 也能兜底）
    if args.stream in ("mmsa", "mmsf") and args.param == "msl":
        return True
    return False


def main() -> None:
    args = parse_args()

    if _is_nao_monthly(args):
        # ===== NAO 月度平均气压场：通过 CDS API 获取季节预报 =====
        grib_path = download_nao_monthly(args)
        try:
            raw = decode_grib_single(grib_path)
            monthly_frames: List[Any] = []
            all_meta: List[Dict[str, Any]] = []

            for i, field_2d in enumerate(raw["fields"]):
                cropped = process_single_nao_frame(field_2d)
                monthly_frames.append(cropped)
                if i < len(raw["fieldMetadata"]):
                    meta = raw["fieldMetadata"][i]
                    meta["fcmonth"] = i + 1
                    all_meta.append(meta)

            if len(monthly_frames) < 6:
                print(f"Warning: 只成功解码了 {len(monthly_frames)} 个月的数据，期望 6 个", file=sys.stderr)

            metadata = {
                "source": "Copernicus CDS (SEAS5)",
                "model": "seas5",
                "stream": "seasonal-monthly-single-levels",
                "source_type": "fetch",
                "fcmonths_downloaded": len(monthly_frames),
                "fields": all_meta,
            }
            # data 是一个 6 元素的列表，每个元素是 13×27 的 2D 数组（hPa 单位）
            payload = {"data": monthly_frames, "metadata": metadata}
            with open(args.output, "w", encoding="utf-8") as output:
                json.dump(payload, output, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        finally:
            try:
                os.remove(grib_path)
            except OSError:
                pass
    else:
        # ===== 通用单文件路径 =====
        grib_path, actual_source = download_field(args)
        try:
            is_nao_msl = args.param == "msl"
            decoded = decode_grib(grib_path, is_nao_msl=is_nao_msl)
            metadata = {
                "source": actual_source,
                "model": args.model,
                "source_type": "fetch",
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
