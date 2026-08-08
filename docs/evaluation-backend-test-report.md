# 评估数据管理后端测试报告

## 结果

- 分支：`feat/evaluation-admin-backend`。
- 基线：`demo@b6cd4bd`。
- `mvn test`：12 个测试全部通过。
- `mvn clean package`：成功。
- Jar 实际启动：成功，约 5 秒。
- 前端及原展示 Controller：未修改。

运行态检查：

| 请求 | 结果 |
| --- | --- |
| 无 Token 请求 `/admin/evaluations/meta` | `401 AUTH_REQUIRED` |
| 无 Token 请求原 `/seaice/error?year=2023&month=1` | `200` |

## 覆盖内容

- 正确、错误、不存在及禁用管理员登录。
- 无效、过期 Token，以及账号禁用后旧 Token 失效。
- ENSO、NAO、SIC、SIE 的新增、查询、更新和删除。
- 预测类 `varModel` 无法通过评估接口读取或修改。
- 手动 JSON 导入、ECMWF UPSERT、重复拒绝和文件内重复。
- 批次中途数据库失败时完整回滚。
- 原普通用户评估接口保持免管理员 Token。
- `prod`/`production` 缺少 JWT 密钥时拒绝启动。

测试日志中有一次预期的 H2 约束异常，用于验证整批回滚；最终测试结果仍为成功。

## 未覆盖的外部环境

本机没有 MySQL 服务，因此未执行真实 MySQL 导入和迁移。全部 Mapper、接口与事务已在 H2 MySQL 兼容模式通过；部署时仍需在目标 MySQL 执行迁移和 HTTP 演示。

原仓库历史曾包含数据库及 keystore 明文凭据。当前配置已改为环境变量，但部署负责人仍应轮换旧凭据。
