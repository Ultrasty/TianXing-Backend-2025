# 评估数据管理联调测试报告

验证日期：2026-08-01。

## 自动化结果

- 后端 `mvnw.cmd test`：15 个测试全部通过。
- 前端 `pnpm install --frozen-lockfile`：通过。
- 前端 `pnpm build`：通过；仅有现有 Sass legacy API 和大 chunk 警告。
- 后端测试覆盖管理员登录/JWT、四类数据 CRUD、手动导入、ECMWF UPSERT、重复拒绝、批次回滚、字段白名单，以及 ECMWF 请求参数安全校验。
- 测试日志中的一次 H2 约束异常是用于验证整批回滚的预期场景，最终结果仍为成功。

## 真实 MySQL 8 验证

使用 MySQL 8.0.46 导入 `mysql_backup_20251023.sql`，四张表的自然键重复检查均为 0；V001/V002 迁移执行成功。

运行态 HTTP 验证：

| 场景 | 结果 |
| --- | --- |
| 无 Token 请求 `/admin/evaluations/meta` | `401 AUTH_REQUIRED` |
| 管理员登录 | 成功返回 Bearer JWT |
| ENSO/NAO/SIC/SIE 元数据和分页查询 | 成功 |
| SIE 新增、更新、按条件查询、删除 | 成功，清理后记录为 0 |
| SIE 手动 JSON 导入 | 新增 1 条，查询成功，演示数据已删除 |
| ECMWF 预览后批量 UPSERT | 新增 1 条，查询成功，演示数据已删除 |

## 真实 ECMWF Open Data 验证

使用 ECMWF Open Data Client 下载最新 IFS `2t`、step 0 的 GRIB2 数据并通过 ecCodes 解码：

- 起报时间：2026-07-31 00 UTC。
- 网格：1440 × 721，共 1,038,240 点。
- 字段单位：K。
- `MEAN` 归约结果：281.1339938590111。
- 经 `POST /admin/evaluations/ecmwf/preview` 返回标准记录，再经 `/admin/evaluations/import/batch` 以 UPSERT 写入 MySQL，闭环成功。
- 主站发生瞬时 SSL 错误时，脚本会按顺序尝试 AWS、Google、Azure 镜像；本次最终实际 provider 为 `ecmwf`。

该结果证明真实下载、GRIB2 解析、转换和入库工程链路可用。空间平均/逐行平均/抽样不是领域评估公式，正式 RMSD、BACC、相关系数等指标仍需带观测数据的上游评估程序计算。

## 前端浏览器联调

- 未登录路由守卫和管理员登录成功。
- 登录后可见 ENSO、NAO、SIC、SIE 四个页签和数据库真实数据。
- 通过页面实际新增并删除 ENSO 演示记录成功。
- 修复了原全局 `.el-button` 样式导致所有普通按钮绝对定位、点击区域重叠的问题；箭头样式现只作用于图表左右切换按钮。

## 配置安全

数据库密码、JWT 密钥、管理员密码、`.env.local`、Python 虚拟环境和运行日志均不进入 Git。原仓库历史曾包含数据库及 keystore 明文凭据，部署负责人仍应轮换旧凭据。
