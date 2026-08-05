# ECMWF 评估数据入库契约

上游程序负责下载、解析、观测匹配和指标计算；后端只接收已经完成科学计算的标准 JSON：

```http
POST /admin/evaluations/import/batch
Authorization: Bearer <JWT>
Content-Type: application/json
```

```json
{
  "source": "ECMWF",
  "dataKind": "EVALUATION_METRIC",
  "mode": "UPSERT",
  "category": "SIE",
  "records": [
    {"year":"2026","month":"7","varModel":"RMSD","data":[0.12,0.18]}
  ]
}
```

规则：

- `source` 固定为 `ECMWF`。
- `dataKind` 固定为 `EVALUATION_METRIC`。`RAW_FIELD_REDUCTION` 或缺失值会被拒绝，防止把 ECMWF 原始场归约值冒充评估指标。
- `mode` 为 `REJECT` 或 `UPSERT`。
- `REJECT` 遇到非法或重复数据时整批零写入。
- `UPSERT` 存在则更新，不存在则新增；任何失败都会整批回滚。
- `data` 必须是非空 JSON 数组，不能包含空值、`NaN` 或 `Infinity`。
- 默认单批最多 500 条；413 时拆分批次。

本接口不会代替科学计算。上游必须至少记录预报变量和有效时间、观测数据源及版本、空间匹配/重网格方法、区域掩膜和指标算法版本，以便结果可复现。

类别字段：

| 类别 | records 字段 |
| --- | --- |
| ENSO | `year,data` |
| NAO | `year="all",month="all",varModel,data` |
| SIC | `year,month,day,varModel,data` |
| SIE | `year,month,varModel,data` |

允许的 `varModel` 以 `GET /admin/evaluations/meta` 返回值为准。

成功响应中的统计字段为：`total`、`inserted`、`updated`。失败时根据 HTTP 状态和 `code` 处理，不要依赖中文 `message`。
