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
| POST | `/admin/evaluations/ecmwf/preview` | 下载并解析 ECMWF Open Data，生成不可直接发布的原始场预览 | 200 |
| POST | `/admin/evaluations/import/batch` | 上游已计算的 ECMWF 评估指标批量入库 | 200 |

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

第二步必须由上游程序引入观测数据，完成变量、有效时间、网格、单位和掩膜匹配，再计算领域指标。只有计算完成的指标才能调用批量接口：

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
- 单批默认最多 500 条；ECMWF 入口的 `source` 必须为 `ECMWF`。
- `dataKind` 必须为 `EVALUATION_METRIC`；缺失该字段或提交 `RAW_FIELD_REDUCTION` 均返回 `400 IMPORT_FILE_INVALID`，不会写库。

注意：Open Data 原始场获取与评估指标发布是两条隔离的链路。空间平均或抽样值不自动等同于 RMSD、BACC、相关系数等科研检验指标；正式科研结果应由带观测数据和领域公式的上游评估程序生成，再复用批量入库接口。

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
```

ECMWF Python 依赖安装：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r scripts\requirements-ecmwf.txt
```

先在 PowerShell 中进入 MySQL 客户端：

```powershell
mysql -u root -p web
```

再在出现的 `mysql>` 提示符中执行迁移（`SOURCE` 不是 PowerShell 命令）：

```sql
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V001__create_admin_user.sql;
```

然后在仓库根目录运行 `scripts/generate-bcrypt-hash.ps1`，用输出的哈希创建管理员。执行 `V002` 前必须确认其中的重复检查无结果。
