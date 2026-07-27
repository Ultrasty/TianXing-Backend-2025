# ECMWF 规范化评估数据入库契约

本接口不下载或解析 GRIB/NetCDF。上游程序完成计算后，将规范化 JSON 发送到：

```http
POST /admin/evaluations/import/batch
Authorization: Bearer <管理员JWT>
Content-Type: application/json
```

## 请求

```json
{
  "source": "ECMWF",
  "mode": "UPSERT",
  "category": "SIE",
  "records": [
    {
      "year": "2026",
      "month": "7",
      "varModel": "RMSD",
      "data": [0.12, 0.18, 0.21]
    }
  ]
}
```

- `source`：批量接口必须为 `ECMWF`（手动文件入口内部使用 `MANUAL`）。逐条 `source` 应省略；若提供，必须与批次一致。
- `mode=REJECT`：任一记录非法、文件内重复或数据库已存在自然键时，整批 0 写入。
- `mode=UPSERT`：自然键已存在则更新，否则新增；任一数据库操作失败时整批事务回滚。
- 单批默认最多 500 条，可由 `ADMIN_IMPORT_MAX_RECORDS` 调整。
- `data` 必须是非空 JSON 数组；不能是 JSON 字符串、`null`，也不能包含 `null`、空字符串、NaN 或 Infinity。
- 数字日期会被规范化，例如 month `"07"` 入库为 `"7"`。

## 各类别记录格式

- ENSO：`year + data`；禁止 month/day/varModel。
- NAO：固定 `year="all"`、`month="all"`，varModel 为 `corr_lead1-6_ECMWF/ECCC/NAO-MCD` 之一。
- SIC：`year + month + day + varModel + data`；年度模型中的年份前缀必须与 `year` 一致。
- SIE：`year + month + varModel + data`；禁止 day。

运行时可调用 `GET /admin/evaluations/meta` 获取同一份机器可读约束。白名单详情见 `evaluation-data-inventory.md`。

## 成功响应

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "category": "SIE",
    "source": "ECMWF",
    "mode": "UPSERT",
    "total": 1,
    "inserted": 1,
    "updated": 0
  }
}
```

失败响应的 `code` 稳定可机读；不要依赖中文 `message` 做程序分支。401 时重新登录，409 时按 mode/自然键处理，413 时拆小批次，500 时本批事务已经回滚，可修复原因后整批重试。
