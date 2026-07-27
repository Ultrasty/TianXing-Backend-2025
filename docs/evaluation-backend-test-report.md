# 评估数据管理后端测试报告

## 1. 基线与环境

- 日期：2026-07-27。
- 功能分支：`feat/evaluation-admin-backend`。
- 基线分支：`demo`（仓库默认分支不是 `master`）。
- 基线提交：`b6cd4bd`。
- Maven 根目录：`demo_backend/`。
- Spring Boot：2.7.9。
- 测试运行时：Java 17.0.2、项目 Maven Wrapper 3.8.6。
- 数据库结构依据：`three_in_one_deploy_package_20251023/mysql_backup_20251023.sql`。

开始开发时后端工作树无未提交改动；前端仓库在整个任务中保持只读，最终 `git status --short` 仍为空。

## 2. 自动化测试结果

执行：

```powershell
cd demo_backend
.\mvnw.cmd test
.\mvnw.cmd clean package
```

两次测试结果一致：

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

`clean package` 成功生成：

```text
demo_backend/target/MybatisDemo-0.0.1-SNAPSHOT.jar
```

原 `pom.xml` 设置了 `testFailureIgnore=true`，会让失败测试不阻断构建；本分支已移除该配置，因此上述成功是严格成功。

## 3. 覆盖范围

### 认证与鉴权

- 正确用户名和密码可登录并获得 JWT。
- 错误密码、不存在用户、禁用用户统一返回 `AUTH_LOGIN_FAILED`。
- 无 Token 返回 `AUTH_REQUIRED`。
- 非法 Token 返回 `AUTH_INVALID_TOKEN`。
- 过期 Token 返回 `AUTH_TOKEN_EXPIRED`。
- 管理员被禁用后，其未到期旧 Token 立即失效。
- 密码校验使用 BCrypt；测试管理员每次使用随机密码及动态生成的哈希。

### 四类 CRUD

- ENSO、NAO、SIC、SIE 均覆盖 POST、分页 GET、详情 GET、PUT、DELETE。
- 响应中的 `data` 为 JSON 数组，不是二次编码字符串。
- 删除后再次详情查询返回 404。
- 日期、类别、字段形状和 `varModel` 白名单均有验证测试。

### 安全边界

- 通过 SIC 管理接口读取、修改或删除 `SIC_Ice-BCNet` 预测记录均返回 404。
- 非法及历史未知评估模型会被校验层拒绝。
- Mapper 的详情、更新和删除 SQL 本身也包含固定白名单谓词。
- 未使用客户端表名或 `${table}` 动态 SQL。

### 批量导入与事务

- 手动 multipart JSON 的合法导入。
- ECMWF 标准 JSON 的 UPSERT（同一自然键执行 UPDATE）。
- `REJECT` 遇到数据库重复时整批零写入。
- 文件内部自然键重复被拒绝。
- 测试数据库对批次第 2 条注入约束失败，确认第 1 条也被回滚。

测试输出中会出现一次预期的 H2 约束异常堆栈；它来自上述故障注入用例，最终测试结果为通过。

### 旧接口回归

- 原普通用户接口 `/seaice/error` 不需要管理员 Token。
- 测试写入一条 SIC 评估数据后，旧接口能够按原响应结构读取。
- 未修改 `EnsoController`、`Tj_naoController`、`Tj_sicController`、`Tj_sieController` 或 `ImgsController`。

## 4. 可执行 Jar 运行态验证

使用生成的 Spring Boot 可执行 Jar、测试作用域 H2 驱动和测试 schema 实际启动服务，启动成功：

```text
Started MybatisDemoApplication in 4.543 seconds
```

运行态 HTTP 检查：

| 请求 | 结果 |
| --- | --- |
| `GET /admin/evaluations/meta`（无 Token） | `401 AUTH_REQUIRED` |
| `GET /seaice/error?year=2023&month=1`（无 Token） | `200 {}` |

验证结束后已停止该 Java 进程，端口没有遗留监听。

## 5. 数据与迁移检查

- 从原 SQL dump 盘点了 `obs_enso`、`obs_nao`、`tj_nao`、`tj_sic`、`tj_sie` 的建表及数据。
- 对 dump 中可解析的四类管理自然键做了重复扫描，未发现冲突。
- `V002` 仍在每个唯一索引前提供重复查询；部署者必须在目标库执行并确认查询结果为空，再添加索引。
- 本机 `127.0.0.1:3306` 没有 MySQL 服务，因此没有执行“导入 580MB SQL 后连接真实 MySQL”的运行测试。H2 以 MySQL 兼容模式验证了全部 Mapper、事务、Web 接口和 schema 约束。

## 6. 安全审查

- `git diff --check` 通过，仅有 Windows 的 LF/CRLF 提示，无空白错误。
- 主配置不再包含数据库明文密码或 SSL keystore 明文密码，改为环境变量。
- JWT 密钥不写入仓库；`prod` profile 缺失密钥时拒绝启动，开发环境缺失时仅生成进程级临时密钥并警告。
- 测试资源只包含明确标注的测试专用 JWT 密钥。
- 原仓库基线的 Git 历史中已经出现过数据库和 keystore 明文凭据；即使当前文件已清除，部署负责人仍应立即轮换这些旧凭据，并按团队流程清理历史。

## 7. 尚未执行的外部验证

- 未连接真实部署 MySQL，未在远程服务器运行迁移。
- 未用真实 ECMWF 下载/解析程序调用批量入口；已用同契约的标准 JSON 完成接口与事务测试。
- 未测试管理前端，因为本任务明确不修改或实现前端。

这些项目需要部署环境或另一位协作者的程序；不影响后端本地构建、API 契约及自动化验收结果。
