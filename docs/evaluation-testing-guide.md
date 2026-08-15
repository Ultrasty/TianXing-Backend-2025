# 2.7–2.9 测试说明

本文只讲测试。环境准备、管理员创建和正式演示顺序见 `evaluation-admin-demo-and-delivery.md`。

## 1. 测试分层

| 层级 | 是否连接本地 MySQL | 是否访问外网 | 验证内容 |
| --- | --- | --- | --- |
| 自动化测试 | 否，使用 H2 | 否 | 鉴权、CRUD、导入、事务、NSIDC 发布与来源留痕 |
| Python 指标测试 | 否 | 否 | SIC 单位/BACC 公式、SIE 时效匹配 |
| 前端生产构建 | 否 | 否 | Vue/TypeScript/打包完整性 |
| 本地联调 | 是 | 否 | 浏览器到 Spring Boot 再到 MySQL 的闭环 |
| 真实数据测试 | 是 | 是 | NSIDC 下载与指标计算 |

提交前至少完成前三项；演示和交付前完成后两项。

## 2. 一键执行离线自动化测试

### 后端和指标测试

在 PowerShell 中运行：

```powershell
cd C:\VScodework\TianXingProject\TianXing-Backend-2026\demo_backend
.\mvnw.cmd test
py -3.12 scripts\test_nsidc_evaluation.py
```

通过标准：

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
Ran 4 tests
OK
```

Maven 日志中出现一次 H2 `Check constraint violation` 堆栈是批量事务回滚用例故意制造的失败。只要最终是 `BUILD SUCCESS` 且 14 个测试零失败，就属于通过。

### 前端构建

打开另一个 PowerShell：

```powershell
cd C:\VScodework\TianXingProject\TianXing-Frontend-2026
pnpm install --frozen-lockfile
pnpm build
```

通过标准是命令退出码为 0，并出现 `built in ...`。现有 Sass legacy API 和 chunk 超过 500 kB 是警告，不是构建失败。

## 3. 本地 MySQL 联调前检查

先在 PowerShell 进入 MySQL；看到提示符变成 `mysql>` 后再输入 SQL：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' `
  -h 127.0.0.1 -P 3306 -u root -p web
```

只在 `mysql>` 中运行：

```sql
USE web;

SHOW TABLES LIKE 'admin_user';
SHOW TABLES LIKE 'evaluation_metric_provenance';

SELECT DISTINCT INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='web'
  AND INDEX_NAME IN (
    'uk_obs_enso_year',
    'uk_tj_nao_year_month_model',
    'uk_tj_sic_date_model',
    'uk_tj_sie_year_month_model'
  );

SELECT COUNT(*) AS sic_prediction_count
FROM tj_sic
WHERE year='2023' AND month='4' AND day='22'
  AND var_model='SIC_Ice-BCNet';

SELECT COUNT(*) AS sie_prediction_count
FROM tj_sie
WHERE year='2022' AND var_model='prediction_IceTFT';
```

预期结果：

- 能看到 `admin_user` 和 `evaluation_metric_provenance`。
- 查询得到 4 个不同的唯一索引名。
- SIC 2023-04-22 至少 1 条；SIE 2022 至少 2 条，当前备份应为 12 条。

如果缺少来源表，只执行 V003：

```sql
USE web;
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V003__add_evaluation_metric_provenance.sql;
SHOW TABLES LIKE 'evaluation_metric_provenance';
```

不要因为缺 V003 而重复执行大备份或已经完成的 V001/V002。

## 4. 启动测试环境

### 后端 PowerShell

```powershell
cd C:\VScodework\TianXingProject\TianXing-Backend-2026\demo_backend
.\.venv\Scripts\python.exe -m pip install -r scripts\requirements-nsidc.txt

$env:DB_URL='jdbc:mysql://127.0.0.1:3306/web?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC'
$env:DB_USERNAME='root'
$mysqlPassword = Read-Host '输入本机 MySQL root 密码' -AsSecureString
$env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', $mysqlPassword).Password
$env:ADMIN_JWT_SECRET=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$env:NSIDC_PYTHON=(Resolve-Path .\.venv\Scripts\python.exe).Path
$env:NSIDC_CACHE_DIR=(Join-Path $env:LOCALAPPDATA 'TianXing\nsidc-cache')
.\mvnw.cmd spring-boot:run
```

通过标准：日志出现 Spring Boot started，且没有数据库连接或 V003 表不存在错误。

### 前端 PowerShell

```powershell
cd C:\VScodework\TianXingProject\TianXing-Frontend-2026
Set-Content .env.local 'VITE_API_BASE_URL=http://localhost:8888'
pnpm dev
```

打开 `http://localhost:5173/tianxing/#/admin/login`。

## 5. 2.7 更新测试

1. 登录后进入 SIE 页签。
2. 发布临时记录：`year=2099`、`month=12`、`varModel=RMSD`、`data=[0.12,0.18,0.21]`。
3. 点击该记录“更新”，改成 `[0.20,0.25,0.30]`。
4. 刷新列表。

通过标准：页面显示新数组；后端发送 `PUT /admin/evaluations/SIE/{id}`；数据库值与页面一致。不要用真实 NSIDC 记录做手工更新，因为手工更新会按设计清除旧来源留痕。

## 6. 2.8 删除测试

删除上一节创建的 2099 临时记录并确认。

通过标准：页面列表不再显示该记录；数据库查询为 0；重复删除同一 ID 时 API 返回 `404 EVALUATION_NOT_FOUND`。

```sql
SELECT COUNT(*)
FROM tj_sie
WHERE year='2099' AND month='12' AND var_model='RMSD';
```

## 7. 2.9 与真实 NSIDC 测试

### SIE 快速测试

1. 点击“NSIDC 科学评估”。
2. 选择 `SIE`，年份填 `2022`。
3. 先点“只计算预览”。
4. 检查预测模型为 `prediction_IceTFT`、观测为 `G02135 V4`、状态为“仅预览”。
5. 再点“计算并发布”。

通过标准：生成 6 条记录；每条 12 个时效；发布结果的 `inserted + updated = 6`。真实样例 RMSD 应约为 `0.179397–0.267709` 百万 km²，相关系数约 `0.996995–0.999142`。

### SIC 真实测试

1. 选择 `SIC`，日期填 `2023-04-22`。
2. “数组第 1 帧对应日期”选择“起报当天”。
3. 第一次先点“只计算预览”；下载可能需要 1–5 分钟。
4. 检查有效日期为 2023-04-22 至 2023-04-28，再执行发布。

通过标准：

- 生成 `2023_RMSE` 和 `2023_BACC` 两条记录，各 7 个值。
- RMSE 库内值约 `0.084098315–0.125317650`，前端显示约 8.41%–12.53%。
- BACC 库内值约 `0.984021520–0.955491729`，前端显示约 98.40%–95.55%。
- 响应包含 MASAM2 V2 URL/SHA-256，以及 G02135 V4 的 BACC active-region 基准。

不要把 2025 低分当作接口故障。已验证 2025 现有预测批次与观测差异较大，演示固定使用 2023-04-22。

### 发布后来源验证

在 `mysql>` 中运行：

```sql
SELECT category, source, prediction_model,
       observation_dataset, observation_version,
       created_at, updated_at
FROM evaluation_metric_provenance
ORDER BY updated_at DESC
LIMIT 10;
```

通过标准：SIC 显示 `SIC_Ice-BCNet / G10005 / 2`，SIE 显示 `prediction_IceTFT / G02135 / 4`，来源均为 `NSIDC`。

## 8. 常见失败判断

| 现象 | 原因与处理 |
| --- | --- |
| `Table 'web.evaluation_metric_provenance' doesn't exist` | 在 `mysql>` 执行 V003 |
| `NSIDC_PREDICTION_NOT_FOUND` | 日期/年份没有对应 Ice-BCNet 或 IceTFT 预测；使用 2023-04-22 / 2022 |
| `NSIDC_GRID_NOT_FOUND` | 备份未包含 `info_sic_latlon` 的 id=1 网格 |
| 首次 SIC 请求超时 | 检查外网，将 `NSIDC_TIMEOUT_SECONDS` 提高到 1200，并保留缓存目录后重试 |
| 后续 SIC 仍每次下载 | 检查 `NSIDC_CACHE_DIR` 是否固定且可写 |
| 页面 BACC 显示约 0.98% | 前端版本过旧；当前版本会把库内 0–1 分数乘 100 显示 |
| Maven 日志有 H2 约束堆栈 | 查看最终是否 `BUILD SUCCESS`；这是回滚测试的预期日志 |

## 9. 交付证据截图

建议保留以下 5 张截图：登录成功、SIE NSIDC 结果、SIC NSIDC 结果及来源详情、2.7 更新前后、2.8 删除后。另保存三段命令末尾：Maven 14/0/0、Python 4 tests OK、Vite build success。
