# 评估数据管理 API

基址示例：`http://localhost:8888`。除登录外，所有接口都要携带：

```http
Authorization: Bearer <JWT>
```

## 接口一览

| Method | URL | 用途 | 成功状态 |
| --- | --- | --- | --- |
| POST | `/admin/auth/login` | 管理员登录 | 200 |
| GET | `/admin/evaluations/meta` | 获取类别、字段和白名单 | 200 |
| GET | `/admin/evaluations` | 分页查询 | 200 |
| GET | `/admin/evaluations/{category}/{id}` | 查询详情 | 200 |
| POST | `/admin/evaluations` | 发布单条数据 | 201 |
| PUT | `/admin/evaluations/{category}/{id}` | 更新完整记录 | 200 |
| DELETE | `/admin/evaluations/{category}/{id}` | 删除记录 | 200 |
| POST | `/admin/evaluations/import/manual` | 手动 JSON 文件导入 | 200 |
| POST | `/admin/evaluations/nsidc/evaluate` | 用现有 Ice-BCNet/IceTFT 预测和 NSIDC 观测计算 SIC/SIE 指标，可预览或发布 | 200 |
| POST | `/admin/evaluations/ecmwf/preview` | 下载并解析 ECMWF Open Data，生成不可直接发布的原始场预览 | 200 |
| POST | `/admin/evaluations/import/batch` | 受信上游已计算指标批量入库 | 200 |

## 登录

```http
POST /admin/auth/login
Content-Type: application/json
```

```json
{"username":"admin","password":"部署时设置的密码"}
```

成功返回 `data.token`、`data.tokenType="Bearer"` 和 `data.expiresIn`。账号不存在、密码错误或账号禁用均返回 `401 AUTH_LOGIN_FAILED`。

## 查询

```http
GET /admin/evaluations?category=SIE&year=2026&page=1&pageSize=20
```

参数：`category` 必填；`year/month/day/varModel` 可选；`page` 默认 1；`pageSize` 默认 20、最大 100。

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {"page":1,"pageSize":20,"total":1,"items":[]}
}
```

## 新增和更新

新增请求包含 `category`：

```json
{
  "category": "SIE",
  "year": "2026",
  "month": "7",
  "varModel": "RMSD",
  "data": [0.12, 0.18]
}
```

更新请求不包含 `category`，其余字段必须完整提供。自然键重复返回 `409 EVALUATION_DUPLICATE`；不存在或不属于评估白名单的 ID 返回 `404 EVALUATION_NOT_FOUND`。

删除成功返回被删除的完整记录；重复删除返回 404。

## 手动导入

```http
POST /admin/evaluations/import/manual?category=SIE&mode=REJECT
Content-Type: multipart/form-data
```

表单字段为 `file`。文件格式：

```json
{
  "category": "SIE",
  "records": [
    {"year":"2026","month":"7","varModel":"RMSD","data":[0.12,0.18]}
  ]
}
```

## NSIDC 科学评估

### SIC：Ice-BCNet 对 MASAM2 V2

```http
POST /admin/evaluations/nsidc/evaluate
Content-Type: application/json
```

```json
{
  "category": "SIC",
  "year": "2023",
  "month": "4",
  "day": "22",
  "leadStartOffsetDays": 0,
  "mode": "PREVIEW"
}
```

后端从 `tj_sic` 读取该日期的 `SIC_Ice-BCNet` 7 天预测，从 `info_sic_latlon` 读取 384×420 模型网格；下载 NSIDC G10005 MASAM2 V2 月度 NetCDF，将每日观测双线性重投影到模型网格并屏蔽无效/陆地邻点。输出：

- `{year}_RMSE`：按模型网格面积加权的 SIC 格点 RMSE，单位为百分点。
- `{year}_BACC`：以 SIC ≥ 15% 判定海冰，按面积计算灵敏度和特异度后取均值，单位为百分比。
- `diagnostics`：每个时效的有效日期、格点数、面积、灵敏度、特异度和 IIEE。

`leadStartOffsetDays=0` 表示数组第一个场对应起报当天；若数据生产约定第一个场对应次日，传 `1`。不允许猜测其它偏移。

### SIE：IceTFT 对 Sea Ice Index V4

```json
{
  "category": "SIE",
  "year": "2022",
  "mode": "PREVIEW"
}
```

后端读取该年的全部 `prediction_IceTFT` 月起报；每条 12 值数组的索引 0 对应起报月，索引 1–11 对应后续月份。观测来自 NSIDC G02135 Sea Ice Index V4 北半球月平均 extent。系统按提前 1–12 月归组，输出 `RMSD`、`BAIS`、`VAR`、`CORRELATION`、`OBS_STD`、`PRE_STD`；`BAIS` 延用历史表拼写，含义为平均偏差的平方。

`mode` 的含义：

- `PREVIEW`：计算并返回，不改数据库。
- `UPSERT`：在同一事务中新增或更新指标，同时写入 `evaluation_metric_provenance`。

响应固定包含 `source=NSIDC`、`dataKind=EVALUATION_METRIC`、预测模型、观测数据集/版本/DOI、访问时间、源 URL、SHA-256、匹配规则、公式说明和发布结果。完整方法见 `docs/nsidc-scientific-evaluation.md`。

## ECMWF 原始场预览与上游指标入库

第一步从 Open Data 下载 GRIB2，解析字段并生成原始场归约预览：

```http
POST /admin/evaluations/ecmwf/preview
Content-Type: application/json
```

```json
{
  "time": 0,
  "step": 24,
  "param": "2t",
  "levtype": "sfc",
  "model": "ifs",
  "provider": "ecmwf",
  "forecastType": "fc",
  "reducer": "MEAN",
  "maxPoints": 200
}
```

`date` 可留空以获取最新起报；`reducer` 支持 `MEAN`、`ROW_MEAN` 和 `SAMPLE`。服务会优先使用指定源，ECMWF 主站发生瞬时网络错误时依次尝试 AWS、Google 和 Azure 镜像。响应中的 `data.values` 是工程归约值，`data.metadata` 会记录实际数据源、模型、起报时间、网格数和字段单位。

预览响应固定包含以下安全标识，不能直接转为评估记录：

```json
{
  "source": "ECMWF",
  "dataKind": "RAW_FIELD_REDUCTION",
  "publishable": false,
  "values": [281.13],
  "metadata": {},
  "notice": "这是 ECMWF 原始预报场的工程归约结果，不是评估指标，不能直接写入评估表"
}
```

若将来有其它受信上游程序引入观测数据并完成变量、有效时间、网格、单位和掩膜匹配，计算完成的指标仍可调用批量接口：

```http
POST /admin/evaluations/import/batch
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

- `REJECT`：任何非法或重复记录都会使整批零写入。
- `UPSERT`：存在则更新，不存在则新增；任一写入失败时整批回滚。
- 单批默认最多 500 条；这个兼容入口当前要求 `source=ECMWF`。
- `dataKind` 必须为 `EVALUATION_METRIC`；缺失该字段或提交 `RAW_FIELD_REDUCTION` 均返回 `400 IMPORT_FILE_INVALID`，不会写库。

注意：ECMWF Open Data 原始场获取与 NSIDC 海冰评估是两条隔离的链路。空间平均或抽样值不等于 RMSD、BACC 或相关系数，也不能提交给 NSIDC 评估入口。

成功结果：

```json
{"category":"SIE","source":"ECMWF","mode":"UPSERT","total":1,"inserted":1,"updated":0}
```

## 类别约束

| 类别 | 必填字段 | 自然键 | `varModel` |
| --- | --- | --- | --- |
| ENSO | `year,data` | `year` | 不使用 |
| NAO | `year,month,varModel,data` | `year+month+varModel` | `corr_lead1-6_ECMWF/ECCC/NAO-MCD`，年月固定 `all/all` |
| SIC | `year,month,day,varModel,data` | `year+month+day+varModel` | `{year}_BACC`、`{year}_per_BACC`、`{year}_RMSE`、`{year}_per_RMSE` 及 meta 返回的固定类型 |
| SIE | `year,month,varModel,data` | `year+month+varModel` | `RMSD`、`BAIS`、`VAR`、`CORRELATION`、`OBS_STD`、`PRE_STD` |

`data` 必须是非空 JSON 数组，不能是字符串，也不能包含 `null`、空字符串、`NaN` 或 `Infinity`。`BAIS` 是历史库真实拼写。

## 通用响应与错误

成功：

```json
{"code":0,"message":"success","data":{}}
```

失败：

```json
{"code":"EVALUATION_INVALID_DATA","message":"错误说明","details":{}}
```

常用状态：400 参数非法，401 未登录或 Token 无效，404 数据不存在，409 自然键重复，413 批量/文件过大，500 数据库错误。

## 部署配置

必须配置数据库连接；生产环境还必须设置至少 32 字节的 `ADMIN_JWT_SECRET`。常用变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
ADMIN_JWT_SECRET
ADMIN_JWT_EXPIRE_SECONDS=7200
ADMIN_IMPORT_MAX_RECORDS=500
ADMIN_IMPORT_MAX_FILE_SIZE=10MB
ECMWF_PYTHON=python
ECMWF_SCRIPT_PATH=scripts/ecmwf_evaluation_fetch.py
ECMWF_TIMEOUT_SECONDS=240
ECMWF_MAX_OUTPUT_BYTES=5242880
NSIDC_PYTHON=python
NSIDC_SCRIPT_PATH=scripts/nsidc_evaluation.py
NSIDC_CACHE_DIR=<可写的持久缓存目录>
NSIDC_TIMEOUT_SECONDS=900
NSIDC_MAX_OUTPUT_BYTES=5242880
NSIDC_DOWNLOAD_WORKERS=12
```

Python 依赖安装：

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r scripts\requirements-ecmwf.txt
.\.venv\Scripts\python.exe -m pip install -r scripts\requirements-nsidc.txt
```

先在 PowerShell 中进入 MySQL 客户端：

```powershell
mysql -u root -p web
```

再在出现的 `mysql>` 提示符中执行迁移（`SOURCE` 不是 PowerShell 命令）：

```sql
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V001__create_admin_user.sql;
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V002__add_evaluation_natural_key_indexes.sql;
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V003__add_evaluation_metric_provenance.sql;
```

然后在仓库根目录运行 `scripts/generate-bcrypt-hash.ps1`，用输出的哈希创建管理员。执行 `V002` 前必须确认其中的重复检查无结果。不要在 PowerShell 提示符直接输入 SQL。
