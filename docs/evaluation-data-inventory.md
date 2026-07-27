# 评估数据盘点

盘点基线：后端 `demo` 分支 `b6cd4bd`；数据库来源为 `mysql_backup_20251023.sql`。结论同时对照了 SQL 表结构与数据、旧 Controller/Mapper，以及前端评估页面实际读取的字段。管理 API 只开放下表中明确标为“评估”的记录；含义不明的数据默认 `UNMANAGED`。

## 管理边界

| category | 管理表 | 字段 | 评估数据判定 | 自然唯一键 |
| --- | --- | --- | --- | --- |
| ENSO | `obs_enso` | `id, year, data` | 整表为 ENSO 评估计算所需观测序列 | `year` |
| NAO | `tj_nao` | `id, year, month, data, var_model` | 仅 3 个 `corr_lead1-6_*` 相关系数结果 | `year+month+var_model` |
| SIC | `tj_sic` | `id, year, month, day, var_model, data` | 年度 BACC/RMSE 与 4 个 DA/BC 误差系列 | `year+month+day+var_model` |
| SIE | `tj_sie` | `id, year, month, var_model, data` | 仅 6 个误差分解指标 | `year+month+var_model` |

所有已管理表的 `data` 都是 MySQL `JSON`。现有数据及旧页面均按数组读取，因此管理 API 要求 `data` 为非空 JSON 数组，并以真正的 JSON 返回，避免双重字符串。

## ENSO

- `obs_enso`：观测 Niño 3.4 年序列。旧 `/enso/predictionExamination/**` 用它和 `tj_enso` 预测序列动态计算逐月比较、误差、箱线图及相关系数。
- 管理字段：`year`、`data`；没有 `month/day/var_model`。
- `tj_enso` 中的 `nino34_asc`、`nino34_gtc`、`nino34_mc`、`nino34_mean` 等是预测数据，属于 2.1—2.3，本 API 禁止修改。

## NAO

管理的 `tj_nao.var_model` 白名单：

| var_model | 含义 |
| --- | --- |
| `corr_lead1-6_ECMWF` | ECMWF 提前 1—6 月 NAOI 预测技巧相关系数 |
| `corr_lead1-6_ECCC` | ECCC 提前 1—6 月 NAOI 预测技巧相关系数 |
| `corr_lead1-6_NAO-MCD` | 平台 NAO-MCD 提前 1—6 月预测技巧相关系数 |

这 3 条历史记录使用 `year=all, month=all`，API 保留这一真实键格式。`tj_nao` 的 `index_NAO_MCD`、`grid_NAO_MCD` 是预测数据；`obs_nao` 是观测输入。它们均不在本次“评估结果数据”CRUD 范围。

## SIC

管理白名单：

- 与记录年份一致的 `{year}_BACC`、`{year}_per_BACC`、`{year}_RMSE`、`{year}_per_RMSE`。
- `MITgcm(with DA)withBC_RMSE`。
- `withDA_withoutBC_RMSE`。
- `withoutDA_withBC_RMSE`。
- `withoutDA_withoutBC`（历史库真实名称，虽无 `_RMSE` 后缀仍按旧页面保留）。

`SIC_Ice-BCNet` 是预测数据，任何详情、更新、删除、创建或导入尝试都会失败。旧代码中按年份模糊匹配及按 `DA` 模糊匹配的逻辑只用于普通用户展示；管理端使用严格白名单/正则，不复用模糊边界。

## SIE

管理白名单：`RMSD`、`BAIS`（历史库拼写）、`VAR`、`CORRELATION`、`OBS_STD`、`PRE_STD`。

明确禁止的预测系列包括 `prediction_IceTFT`、`mean_IceTFT`、`upper_IceTFT`、`lower_IceTFT`。`beat_Ice_TFT`、`best`、`mean`、`mean_Ice_TFT`、`tarnew` 等旧值含义没有足够证据，标为 `UNMANAGED`，不会因为知道 ID 而开放。

## Mapper 与约束

管理 CRUD 由 `AdminEvaluationMapper` 中四组固定 SQL 和四个独立 Adapter 实现。没有客户端表名参数、`${table}` 或通用动态表 SQL。每条 SIC/SIE/NAO 的读写/删除 SQL 自身也带评估白名单谓词，形成校验层之外的第二道边界。

迁移 `V002__add_evaluation_natural_key_indexes.sql` 为四张表增加数据库唯一键。对 2025-10-23 dump 的检测结果为：`tj_nao` 捕获 109 条键、`tj_sic` 24 条、`tj_sie` 88 条，重复组均为 0；`obs_enso.year` 也无重复。目标数据库若已有新数据，执行迁移前仍必须运行脚本开头的四组重复检查。
