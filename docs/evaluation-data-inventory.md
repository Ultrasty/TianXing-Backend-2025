# 评估数据盘点

依据后端 `demo@b6cd4bd`、数据库备份及原展示接口确定以下管理边界：

| 类别 | 表 | 可管理数据 | 自然键 |
| --- | --- | --- | --- |
| ENSO | `obs_enso` | 整表观测序列 | `year` |
| NAO | `tj_nao` | 3 个 `corr_lead1-6_*` 相关系数 | `year+month+var_model` |
| SIC | `tj_sic` | 年度 BACC/RMSE 和 4 个 DA/BC 误差系列 | `year+month+day+var_model` |
| SIE | `tj_sie` | 6 个误差分解指标 | `year+month+var_model` |

## 白名单

- NAO：`corr_lead1-6_ECMWF`、`corr_lead1-6_ECCC`、`corr_lead1-6_NAO-MCD`；历史键为 `all/all`。
- SIC：`{year}_BACC`、`{year}_per_BACC`、`{year}_RMSE`、`{year}_per_RMSE`、`MITgcm(with DA)withBC_RMSE`、`withDA_withoutBC_RMSE`、`withoutDA_withBC_RMSE`、`withoutDA_withoutBC`。
- SIE：`RMSD`、`BAIS`、`VAR`、`CORRELATION`、`OBS_STD`、`PRE_STD`。

## 明确排除

- ENSO：`tj_enso` 中的预测序列。
- NAO：`tj_nao.index_NAO_MCD/grid_NAO_MCD` 和 `obs_nao`。
- SIC：`SIC_Ice-BCNet`。
- SIE：`prediction_IceTFT/mean_IceTFT/upper_IceTFT/lower_IceTFT`。
- 含义不足的旧值统一标为 `UNMANAGED`，不开放 CRUD。

管理端使用四个 Adapter 和固定表 Mapper；没有客户端表名或动态 `${table}` SQL。详情、更新、删除 SQL 本身也带白名单条件。

原 dump 的自然键扫描未发现重复。目标库执行 `V002` 前仍须运行脚本内的重复检查。
