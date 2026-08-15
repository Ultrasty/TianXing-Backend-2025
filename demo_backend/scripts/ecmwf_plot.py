import argparse
import os
import sys


def load_data(path, variable, file_format):
    try:
        import xarray as xr
    except ImportError as exc:
        raise RuntimeError("缺少 xarray，请安装 xarray cfgrib netCDF4 matplotlib") from exc
    if file_format.upper() == "NETCDF" or path.lower().endswith((".nc", ".nc4", ".netcdf")):
        dataset = xr.open_dataset(path)
    else:
        try:
            dataset = xr.open_dataset(path, engine="cfgrib")
        except Exception as exc:
            raise RuntimeError("GRIB 解析失败，请确认已安装 cfgrib、eccodes 和 matplotlib") from exc
    if variable not in dataset:
        candidates = list(dataset.data_vars)
        if not candidates:
            raise RuntimeError("数据文件中没有可绘制变量")
        variable = candidates[0]
    return dataset, dataset[variable]


def render(data_array, output_prefix, title):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    value = data_array
    while value.ndim > 2:
        value = value.isel({value.dims[0]: 0})
    figure = plt.figure(figsize=(12, 6.75), dpi=150)
    axis = figure.add_subplot(111)
    image = value.plot(ax=axis, cmap="coolwarm", add_colorbar=True)
    image.colorbar.set_label(str(value.name or "value"))
    axis.set_title(title)
    axis.set_xlabel("")
    axis.set_ylabel("")
    figure.tight_layout()
    output = output_prefix + "-01.png"
    figure.savefig(output, bbox_inches="tight")
    plt.close(figure)
    return output


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--variable", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--format", default="GRIB")
    args = parser.parse_args()
    dataset, data_array = load_data(args.input, args.variable, args.format)
    output = render(data_array, args.output, args.title)
    print(output)
    dataset.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
