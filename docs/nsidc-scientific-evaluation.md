# NSIDC SIC/SIE 科学评估方法

## 数据角色

| 对象 | 角色 | 来源 |
| --- | --- | --- |
| `SIC_Ice-BCNet` | 7 天 SIC 预测 | 本项目 `tj_sic` |
| `prediction_IceTFT` | 12 个月 SIE 预测 | 本项目 `tj_sie` |
| MASAM2 V2（G10005） | 每日 SIC 观测 | NSIDC，4 km，2012 至今，DOI `10.7265/bqd9-vm28` |
| Sea Ice Index V4（G02135） | 月平均北半球 SIE 观测 | NSIDC，DOI `10.7265/N5K072F8` |
| ECMWF Open Data | 独立真实气象预报场 | ECMWF；不参与本次海冰评分 |

官方产品页：

- MASAM2 V2：https://nsidc.org/data/g10005/versions/2
- Sea Ice Index V4：https://nsidc.org/data/g02135/versions/4
- ECMWF Open Data：https://www.ecmwf.int/en/forecasts/datasets/open-data
- Ice-BCNet 论文：https://doi.org/10.1016/j.ocemod.2024.102326
- 归一化 IIEE / binary accuracy 方法：https://doi.org/10.1038/s41467-021-25257-4

## SIC 匹配与指标

1. 按 Ice-BCNet 起报日期和 7 个时效确定 7 个有效日。
2. 下载覆盖这些日期的 MASAM2 V2 月度 NetCDF，并记录 URL、版本、访问时间和 SHA-256。
3. 使用文件自带的极射赤面投影参数，把 MASAM2 2550×2100 观测双线性插值到 `info_sic_latlon` 的 384×420 模型网格。
4. 任一插值邻点为陆地或无效标志时屏蔽目标格点；预测和观测统一为 0–1 浓度。
5. 从模型网格经纬度近似计算球面格点面积，所有指标仅使用双方均有效且面积为正的格点。

面积加权 RMSE：

```text
RMSE = sqrt(sum(area_i * (pred_i - obs_i)^2) / sum(area_i))
```

预测和观测均使用 0–1 的 SIC 分数，结果也保存为 0–1，和现有 `tj_sic` 历史数组一致；前端展示时乘 100 标成百分比。

BACC 按 Ice-BCNet 使用的归一化 IIEE 口径计算。先用 SIC > 15% 判定海冰，IIEE 是预测和观测冰区对称差的面积；分母是 NSIDC Sea Ice Index V4 在 1991–2020 年内、对应日历月的最大日 SIE：

```text
IIEE = false_positive_area + false_negative_area
BACC = 1 - IIEE / monthly_active_region_area
```

结果保存为 0–1；响应同时给出 IIEE、有效面积、月度 active-region 面积、灵敏度和特异度作为诊断。这个 BACC 不是通用分类学中 `(灵敏度+特异度)/2` 的 balanced accuracy，不能混用同名公式。

## SIE 匹配与指标

每条 `prediction_IceTFT` 记录代表一个月起报，数组长度必须为 12；索引 0 对应起报月。系统把同一年度的月起报按提前 1–12 月归组，并与 Sea Ice Index V4 相同有效月份的观测配对。每个时效至少需要两个非恒定样本。

对每个时效分别计算：

```text
error = prediction - observation
RMSD = sqrt(mean(error^2))
BAIS = mean(error)^2
VAR = mean((error - mean(error))^2)
CORRELATION = Pearson(prediction, observation)
OBS_STD = population_std(observation)
PRE_STD = population_std(prediction)
```

SIE、RMSD 和标准差的单位为百万平方公里；BAIS、VAR 为其平方。`BAIS` 是现有数据库白名单的历史拼写，因此接口保持该字段名，但来源记录会保存准确公式。

## 可追溯性与边界

- `PREVIEW` 不写库，适合核对日期、样本数和结果。
- `UPSERT` 写入评估表，并为每条记录写入 `evaluation_metric_provenance`。
- 来源表保存预测模型、NSIDC 数据集、版本以及包含 URL、SHA-256、匹配规则和公式的完整 JSON。
- 后续通过普通 CRUD 手工修改或删除指标时，旧来源记录同步失效/删除；再次执行 NSIDC UPSERT 才会生成新的可追溯来源，避免“值已改、来源仍冒充原计算”的情况。
- 本实现验证评估链路，不保证任意预测批次都有高分。2025 SIC 现有输入的真实分数较低，应排查模型产物或批次，不能通过改变观测、日期或公式掩盖。
- MASAM2 约 40% 以下浓度依产品设计受最小浓度限制；因此连续 SIC RMSE 和 15% 阈值 BACC 的产品口径必须随结果一起交付。

## 已核验样例（2026-08-05 运行）

- `SIC_Ice-BCNet`，起报 2023-04-22：lead 1–7 RMSE 为 `0.084098315, 0.098919229, 0.105173961, 0.114620542, 0.116508910, 0.119929401, 0.125317650`；BACC 为 `0.984021520, 0.974444380, 0.968473066, 0.962763090, 0.960757927, 0.958927358, 0.955491729`。
- `prediction_IceTFT`，2022 年 12 个起报样本：lead 1–12 RMSD 为 `0.191984, 0.179397, 0.211625, 0.224933, 0.210857, 0.204679, 0.217675, 0.241080, 0.255676, 0.267709, 0.253065, 0.236150`；相关系数约为 `0.996995–0.999142`。

这些数字是验收用真实运行证据，不应写成模型对所有年份的总体性能结论。
