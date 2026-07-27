# 评估数据管理 API

本文档面向管理前端和 ECMWF 数据转换程序。接口基址示例为 `http://localhost:8888`。

## 1. 通用约定

除登录外，所有 `/admin/**` 接口都必须携带：

```http
Authorization: Bearer <JWT>
```

成功响应统一为：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

失败响应统一为：

```json
{
  "code": "EVALUATION_INVALID_DATA",
  "message": "请求参数或JSON格式不正确",
  "details": {}
}
```

`data` 必须是真正的非空 JSON 数组，不能是 JSON 字符串；数组内部不能包含 `null`、空字符串、`NaN` 或 `Infinity`。年份和月份在数据库中沿用字符串字段，但请求会经过格式及真实日期校验。

## 2. 登录

### `POST /admin/auth/login`

无需鉴权。

```json
{
  "username": "admin",
  "password": "由部署者设置的密码"
}
```

成功：`200 OK`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 7200
  }
}
```

用户名不存在、密码错误或账号被禁用统一返回 `401 AUTH_LOGIN_FAILED`，避免泄露账号状态。

## 3. 元数据

### `GET /admin/evaluations/meta`

返回四类数据各自的必填字段、允许的 `varModel`、自然键，以及单次导入限制。管理前端应使用该接口生成选项，不应另行硬编码白名单。

成功：`200 OK`。

## 4. 分页查询

### `GET /admin/evaluations`

Query：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `category` | 是 | `ENSO`、`NAO`、`SIC`、`SIE`，大小写不敏感 |
| `year` | 否 | 四位年份；NAO 固定为 `all` |
| `month` | 否 | `1`—`12`；NAO 固定为 `all` |
| `day` | 否 | 真实有效日，仅 SIC 使用 |
| `varModel` | 否 | 必须属于该类别的评估白名单 |
| `page` | 否 | 默认 `1` |
| `pageSize` | 否 | 默认 `20`，最大 `100` |

示例：

```http
GET /admin/evaluations?category=SIE&year=2022&page=1&pageSize=20
```

成功：`200 OK`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "items": [
      {
        "category": "SIE",
        "id": 123,
        "year": "2022",
        "month": "1",
        "day": null,
        "varModel": "RMSD",
        "data": [0.1, 0.2]
      }
    ]
  }
}
```

结果按时间降序、再按 `id DESC` 排序。

## 5. 详情

### `GET /admin/evaluations/{category}/{id}`

成功：`200 OK`，`data` 为一条 `EvaluationRecordResponse`。

记录不存在，或对应 ID 实际属于预测/未知 `var_model` 时返回 `404 EVALUATION_NOT_FOUND`。接口不会通过 ID 越过评估数据白名单。

## 6. 发布单条数据

### `POST /admin/evaluations`

成功：`201 Created`。

SIE 示例：

```json
{
  "category": "SIE",
  "year": "2026",
  "month": "7",
  "varModel": "RMSD",
  "data": [0.12, 0.18, 0.21]
}
```

ENSO 示例：

```json
{
  "category": "ENSO",
  "year": "2026",
  "data": [0.12, 0.18, 0.21]
}
```

自然键已存在返回 `409 EVALUATION_DUPLICATE`。

## 7. 更新数据（2.7）

### `PUT /admin/evaluations/{category}/{id}`

请求体为目标记录的完整内容，不是局部 PATCH。成功：`200 OK`，返回更新后的完整记录。

```json
{
  "year": "2026",
  "month": "7",
  "varModel": "RMSD",
  "data": [0.2, 0.3]
}
```

自然键与其他记录冲突返回 `409`；记录不存在或不属于评估白名单返回 `404`。

## 8. 删除数据（2.8）

### `DELETE /admin/evaluations/{category}/{id}`

成功：`200 OK`，返回删除前的完整记录。重复删除返回 `404 EVALUATION_NOT_FOUND`。

## 9. 手动 JSON 导入（2.9）

### `POST /admin/evaluations/import/manual`

`Content-Type: multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | UTF-8 JSON 文件 |
| `category` | text | 是 | 与文件中的 `category` 必须一致 |
| `mode` | text | 否 | `REJECT`（默认）或 `UPSERT` |

文件：

```json
{
  "category": "SIE",
  "records": [
    {
      "year": "2022",
      "month": "1",
      "varModel": "RMSD",
      "data": [0.1, 0.2]
    }
  ]
}
```

`REJECT`：文件内重复、数据库已有自然键或任何一条非法都会导致整批零写入。

`UPSERT`：数据库已有自然键时更新，否则新增；文件内部自然键重复仍会被拒绝，避免同一批中执行顺序产生歧义。

成功：`200 OK`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "category": "SIE",
    "mode": "UPSERT",
    "source": "MANUAL",
    "total": 1,
    "inserted": 1,
    "updated": 0
  }
}
```

## 10. ECMWF 规范化批量入库（2.9）

### `POST /admin/evaluations/import/batch`

`Content-Type: application/json`

```json
{
  "source": "ECMWF",
  "mode": "UPSERT",
  "category": "SIE",
  "records": [
    {
      "year": "2026",
      "month": "7",
      "day": null,
      "varModel": "RMSD",
      "data": [0.12, 0.18, 0.21]
    }
  ]
}
```

该入口只接收 `source: ECMWF`。它不下载或解析 GRIB/NetCDF；调用方须先转换为本文档定义的标准 JSON。该入口与手动导入共用校验、自然键、事务和入库逻辑。更详细的协作契约见 `docs/evaluation-batch-import-contract.md`。

## 11. 类别规则

| 类别 | 表 | 必填字段 | 自然键 | 允许的 `varModel` |
| --- | --- | --- | --- | --- |
| ENSO | `obs_enso` | `year,data` | `year` | 不使用，必须省略 |
| NAO | `tj_nao` | `year,month,varModel,data` | `year+month+varModel` | `corr_lead1-6_ECMWF`、`corr_lead1-6_ECCC`、`corr_lead1-6_NAO-MCD`；年/月固定 `all/all` |
| SIC | `tj_sic` | `year,month,day,varModel,data` | `year+month+day+varModel` | `{year}_BACC`、`{year}_per_BACC`、`{year}_RMSE`、`{year}_per_RMSE` 以及 meta 返回的 4 个固定 RMSE 类型 |
| SIE | `tj_sie` | `year,month,varModel,data` | `year+month+varModel` | `RMSD`、`BAIS`、`VAR`、`CORRELATION`、`OBS_STD`、`PRE_STD` |

注意：`BAIS` 是现有数据库及旧接口使用的历史拼写，不能改成 `BIAS`，否则旧展示接口读不到该数据。

## 12. 错误码与 HTTP 状态

| HTTP | 错误码 | 含义 |
| --- | --- | --- |
| 400 | `EVALUATION_INVALID_CATEGORY` | 类别不在白名单 |
| 400 | `EVALUATION_INVALID_VAR_MODEL` | `varModel` 不属于该评估类别 |
| 400 | `EVALUATION_INVALID_DATE` | 日期字段非法或不适用于该类别 |
| 400 | `EVALUATION_INVALID_DATA` | 请求/JSON/data 非法 |
| 400 | `IMPORT_FILE_EMPTY` | 文件为空 |
| 400 | `IMPORT_FILE_INVALID` | 文件结构、类别或导入来源非法 |
| 400 | `IMPORT_BATCH_FAILED` | 批量校验或写入失败，事务已回滚 |
| 401 | `AUTH_LOGIN_FAILED` | 登录失败 |
| 401 | `AUTH_REQUIRED` | 未携带 Bearer Token |
| 401 | `AUTH_INVALID_TOKEN` | Token 非法，或对应管理员已不存在/禁用 |
| 401 | `AUTH_TOKEN_EXPIRED` | Token 已过期 |
| 404 | `EVALUATION_NOT_FOUND` | 记录不存在或不属于可管理评估数据 |
| 409 | `EVALUATION_DUPLICATE` | 自然键冲突 |
| 413 | `IMPORT_FILE_TOO_LARGE` | 上传文件超过限制 |
| 413 | `IMPORT_TOO_MANY_RECORDS` | 超过配置的批量条数 |
| 500 | `DATABASE_OPERATION_FAILED` | 未预期数据库或服务错误；响应不泄露堆栈 |

## 13. 部署配置

| 环境变量 | 必填性/默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | 默认本机 `tianxing_dev` | JDBC URL |
| `DB_USERNAME` | 默认 `root` | 数据库账号 |
| `DB_PASSWORD` | 无安全默认值 | 数据库密码，不提交 Git |
| `ADMIN_JWT_SECRET` | 生产必填 | 至少 32 字节；生产缺失时拒绝启动 |
| `ADMIN_JWT_EXPIRE_SECONDS` | 默认 `7200` | Token 有效秒数 |
| `ADMIN_IMPORT_MAX_RECORDS` | 默认 `500` | 单批最大记录数 |
| `ADMIN_IMPORT_MAX_FILE_SIZE` | 默认 `10MB` | Spring multipart 限制 |
| `ADMIN_IMPORT_MAX_FILE_SIZE_BYTES` | 默认 `10485760` | 业务层文件大小限制，应与上一项一致 |

先执行 `database/migrations/V001__create_admin_user.sql`。用 `scripts/generate-bcrypt-hash.ps1` 交互生成 BCrypt 哈希，再人工插入管理员；脚本和迁移中均不包含默认明文密码。`V002` 的唯一索引必须在其前置重复查询返回空结果后执行。
