# 2.7–2.9 前后端联调演示与交付

## 1. 一次性准备

### 先分清两个命令窗口

本文后续会明确标注命令应该在哪里运行：

- 看到 `PS C:\...>`：这是 **PowerShell**，只能运行 PowerShell 命令。
- 看到 `mysql>`：这是 **MySQL 客户端**，只能运行 `USE`、`SELECT`、`INSERT`、`SOURCE` 等 SQL。

不要把示例中的“你的密码”“粘贴哈希”等说明文字原样当成密码使用。

### MySQL 8：启动并检查数据

#### 第一步：在管理员 PowerShell 中运行


```powershell
Start-Service MySQL80
Get-Service MySQL80

& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' `
  -h 127.0.0.1 -P 3306 -u root -p
```

输入 MySQL root 密码后，提示符会变成 `mysql>`。

#### 第二步：仅在 `mysql>` 中运行

先检查 `web` 数据库是否已经导入：

```sql
SHOW DATABASES LIKE 'web';
USE web;
SHOW TABLES;
```

如果能看到 `obs_enso`、`tj_nao`、`tj_sic` 和 `tj_sie`，说明业务数据已经导入，**不要再次导入大 SQL**。

只有全新数据库、完全看不到上述表时，才在 `mysql>` 中运行：

```sql
CREATE DATABASE IF NOT EXISTS web CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE web;
SOURCE C:/VScodework/TianXingProject/three_in_one_deploy_package_20251023/mysql_backup_20251023.sql;
```

### 检查三个数据库升级脚本

`V001`、`V002`、`V003` 是数据库迁移的执行顺序，不是软件版本：

- `V001`：创建管理员表 `admin_user`。
- `V002`：给四类评估表增加唯一索引，防止重复发布。
- `V003`：创建科学指标来源表 `evaluation_metric_provenance`，保存 NSIDC 产品版本、URL、SHA-256 和计算细节。

继续在 `mysql>` 中运行：

```sql
SHOW TABLES LIKE 'admin_user';
SHOW TABLES LIKE 'evaluation_metric_provenance';

SELECT DISTINCT TABLE_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='web'
  AND INDEX_NAME IN (
    'uk_obs_enso_year',
    'uk_tj_nao_year_month_model',
    'uk_tj_sic_date_model',
    'uk_tj_sie_year_month_model'
  );
```

按结果处理：

- 能看到 `admin_user`：V001 已完成，不要重复执行。
- 能看到 `evaluation_metric_provenance`：V003 已完成，不要重复执行。
- 能看到 4 个不同的唯一索引名：V002 已完成，不要重复执行。
- 缺少 `admin_user`：在 `mysql>` 运行下面的 V001。
- 四个索引全部缺少：确认使用的是原始业务备份后，在 `mysql>` 运行下面的 V002。
- 只存在部分索引：停止操作，先检查数据库，不要重复运行整个 V002。

```sql
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V001__create_admin_user.sql;
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V002__add_evaluation_natural_key_indexes.sql;
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V003__add_evaluation_metric_provenance.sql;
```

### 创建本地演示管理员

先在 `mysql>` 输入 `EXIT;`，回到 `PS C:\...>`。然后在 **PowerShell** 中运行：

```powershell
cd C:\VScodework\TianXingProject\TianXing-Backend-2026
.\scripts\generate-bcrypt-hash.ps1
```

脚本会隐藏输入并要求确认密码。复制最后输出的 `$2a$...` 哈希，然后重新进入 MySQL，并且直接选中 `web` 数据库：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' `
  -h 127.0.0.1 -P 3306 -u root -p web
```

看到 `mysql>` 后运行下面的 SQL。必须把 `这里替换成刚才输出的完整哈希` 替换掉，不要原样复制该说明文字：

```sql
INSERT INTO admin_user(username, password_hash, enabled)
VALUES ('admin', '这里替换成刚才输出的完整哈希', 1)
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash), enabled=1;

SELECT id, username, enabled FROM admin_user;
EXIT;
```

能看到 `admin` 且 `enabled=1`，数据库准备即完成。明文密码和真实哈希都不要提交到 Git；本地演示密码不要用于云端。

### NSIDC 与 ECMWF Python 环境

```powershell
cd C:\VScodework\TianXingProject\TianXing-Backend-2026\demo_backend
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r scripts\requirements-ecmwf.txt
.\.venv\Scripts\python.exe -m pip install -r scripts\requirements-nsidc.txt
```

两条功能共用这个虚拟环境，但数据含义不同：

- NSIDC：为现有 `SIC_Ice-BCNet`、`prediction_IceTFT` 提供真实观测，计算并发布科学评估指标。
- ECMWF Open Data：获取真实气象预报场并预览，不冒充 SIC/SIE 评估指标。

ECMWF Open Data 是滚动实时数据。演示时将“起报日期”留空，系统会请求最新可用数据。

## 2. 启动联调环境

### 后端（端口 8888）

```powershell
cd C:\VScodework\TianXingProject\TianXing-Backend-2026\demo_backend
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/web?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC'
$env:DB_USERNAME='root'
$mysqlPassword = Read-Host '输入本机 MySQL root 密码' -AsSecureString
$env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', $mysqlPassword).Password
$env:ADMIN_JWT_SECRET=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$env:ECMWF_PYTHON=(Resolve-Path .\.venv\Scripts\python.exe).Path
$env:NSIDC_PYTHON=(Resolve-Path .\.venv\Scripts\python.exe).Path
$env:NSIDC_CACHE_DIR=(Join-Path $env:LOCALAPPDATA 'TianXing\nsidc-cache')
.\mvnw.cmd spring-boot:run
```

### 前端（Vite）

在另一个 PowerShell 中执行：

```powershell
cd C:\VScodework\TianXingProject\TianXing-Frontend-2026
Set-Content .env.local 'VITE_API_BASE_URL=http://localhost:8888'
pnpm install --frozen-lockfile
pnpm dev
```

打开 `http://localhost:5173/tianxing/#/admin/login`，使用刚创建的管理员账号登录。`.env.local` 已被忽略，不应提交。

## 3. 演示前预热一次 NSIDC 缓存

首次 SIC 评估要下载约几十 MB 的月度 MASAM2 文件，网络时间可能超过 1 分钟。正式演示前登录管理页，打开“NSIDC 科学评估”，用下面参数点一次“仅计算预览”：

```text
类别：SIC
起报日期：2023-04-22
首个数组元素：当天（leadStartOffsetDays=0）
```

文件会保存在 `NSIDC_CACHE_DIR`。后续同月评估会校验文件大小和 SHA-256 后复用缓存。缓存是可重新下载的本地文件，不属于 MySQL，也不要提交到 Git。

## 4. 推荐的 8 分钟演示

1. **鉴权闭环（约 30 秒）**：直接访问管理地址，展示未登录跳到登录页；登录后进入四类页签。
2. **真实 SIE 科学评估与 2.9 发布（约 90 秒）**：点击“NSIDC 科学评估”，选择 `SIE / 2022 / 仅计算预览`。说明系统读取 2022 年现有 `prediction_IceTFT` 月起报，匹配 NSIDC Sea Ice Index V4；展示 12 个时效的 RMSD、BAIS、VAR、相关系数、观测标准差和预测标准差。再切换 `UPSERT` 发布，展示来源与写库条数。
3. **真实 SIC 科学评估（约 90 秒）**：选择 `SIC / 2023-04-22 / 当天 / 仅计算预览`。展示 Ice-BCNet 7 天预测、MASAM2 V2 观测、384×420 与 2550×2100 网格匹配，以及 RMSE、BACC、有效格点和有效面积。缓存已预热时无需重新下载。
4. **2.7 更新（约 45 秒）**：进入 SIE，手工发布一条临时记录 `year=2099, month=12, varModel=RMSD, data=[0.12,0.18,0.21]`；点击“更新”改为 `[0.20,0.25,0.30]`，展示列表刷新。不要手工改真实 NSIDC 指标。
5. **真实 ECMWF 独立功能（约 90 秒）**：点击“ECMWF 原始场”，保留 `2t / IFS / ECMWF / MEAN`，起报日期留空。展示实际来源、单位、网格点数量、归约数组和 `RAW_FIELD_REDUCTION / publishable=false`。明确它是气象场获取，不是 SIC/SIE 指标。
6. **2.8 删除和清理（约 45 秒）**：删除第 4 步的 2099 临时记录，确认列表消失；退出登录并说明无 Token 返回 401。

演示时可以使用这句总结：

> Ice-BCNet 和 IceTFT 是预测值，NSIDC 是观测值；平台完成时空匹配、领域指标计算、来源留痕和发布。ECMWF Open Data 是另一条真实气象场获取链路，不参与这次 SIC/SIE 评分。

已验证的真实样例：

- SIC 2023-04-22：7 天面积加权 RMSE 为 `8.409832–12.531765` 个百分点，BACC 为 `99.003666%–97.226418%`。
- SIE 2022：每个时效有 12 个匹配样本，12 个时效 RMSD 为 `0.179397–0.267709` 百万平方公里，相关系数约 `0.996995–0.999142`。

现有 2025 SIC 预测与 NSIDC 的真实得分明显较低。这是预测输入质量或批次问题，不是下载/重投影失败；演示应使用已核验的 2023 样例，也不要修改公式来美化 2025 结果。

## 5. 任务验收映射

| 任务 | 可见操作 | 后端证据 | 验收结果 |
| --- | --- | --- | --- |
| 2.7 更新评估数据 | 列表“更新”弹窗 | `PUT /admin/evaluations/{category}/{id}`，完整校验并按 ID 更新 | 更新后列表与 MySQL 值一致 |
| 2.8 删除评估数据 | 二次确认后删除 | `DELETE /admin/evaluations/{category}/{id}`，不存在返回 404 | 删除后查询不到记录 |
| 2.9 发布评估数据 | 单条发布、JSON 导入、NSIDC 科学评估发布 | `POST /admin/evaluations`、`/import/manual`、`/nsidc/evaluate` | NSIDC 指标可 UPSERT 并保存来源；原始 ECMWF 场不可发布 |

`POST /admin/evaluations/nsidc/evaluate` 是 SIC/SIE 科学评估闭环；`POST /admin/evaluations/ecmwf/preview` 是独立的 ECMWF 原始场获取能力。

四类数据 ENSO、NAO、SIC、SIE 的字段和指标白名单由 `/admin/evaluations/meta` 返回，前端不再使用 mock 数据，也不再把 SIC/SIE 合成 SeaIce。

## 6. 本地数据库与云端数据库

你现在操作的 `127.0.0.1:3306/web`、管理员账号、V001–V003 和 NSIDC 缓存都在本机，**不会自动影响最终云数据库**。上云时要在云数据库单独备份、检查重复键并按顺序执行 V001–V003，然后用云端独立密码、JWT 密钥和缓存目录启动服务；不要复制本地 MySQL 数据目录。

## 7. 提交和交付清单

交付前分别在两个仓库执行：

```powershell
# 后端
cd C:\VScodework\TianXingProject\TianXing-Backend-2026\demo_backend
.\mvnw.cmd test

# 前端
cd C:\VScodework\TianXingProject\TianXing-Frontend-2026
pnpm install --frozen-lockfile
pnpm build
```

PR 应包含：

- 后端分支 `feat/evaluation-admin-backend`：鉴权、CRUD/导入、NSIDC 科学评估、ECMWF 获取、V001–V003、测试、API 和本演示文档。
- 前端分支 `XuYichen-2.7-2.9`：登录、Token/401、真实 API、四类动态表单、CRUD/导入、NSIDC/ECMWF UI、路由守卫和环境变量示例。
- 测试证据：后端测试、Python 指标测试、前端生产构建、真实 NSIDC SIC/SIE 结果、一次 ECMWF metadata 截图或 JSON 摘要。

不要提交 `.env.local`、数据库密码、JWT 密钥、管理员明文密码、`.venv`、NSIDC 缓存、MySQL 数据目录或运行日志。建议前后端各建一个 PR，评审通过后再合入主分支。
