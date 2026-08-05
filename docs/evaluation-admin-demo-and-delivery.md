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

### 检查两个数据库升级脚本

这两个文件不是软件版本：

- `V001`：创建管理员表 `admin_user`。
- `V002`：给四类评估表增加唯一索引，防止重复发布。

继续在 `mysql>` 中运行：

```sql
SHOW TABLES LIKE 'admin_user';

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
- 能看到 4 个不同的唯一索引名：V002 已完成，不要重复执行。
- 缺少 `admin_user`：在 `mysql>` 运行下面的 V001。
- 四个索引全部缺少：确认使用的是原始业务备份后，在 `mysql>` 运行下面的 V002。
- 只存在部分索引：停止操作，先检查数据库，不要重复运行整个 V002。

```sql
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V001__create_admin_user.sql;
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V002__add_evaluation_natural_key_indexes.sql;
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

### ECMWF Python 环境

```powershell
cd C:\VScodework\TianXingProject\TianXing-Backend-2026\demo_backend
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r scripts\requirements-ecmwf.txt
```

ECMWF Open Data 是滚动实时数据，通常只保留最近约 2–3 天的起报。演示时将“起报日期”留空，系统会请求最新可用数据。

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

## 3. 推荐的 6 分钟演示

1. **鉴权闭环（约 40 秒）**：直接访问管理地址，展示未登录会跳到登录页；登录后进入四类页签，说明请求都带 Bearer Token。
2. **2.9 发布（约 60 秒）**：进入 SIE，点击“发布数据”，填写 `year=2099`、`month=12`、`varModel=RMSD`、`data=[0.12,0.18,0.21]`。保存后查询并展示 MySQL 返回的真实记录。
3. **2.7 更新（约 40 秒）**：点击刚发布记录的“更新”，把数组改为 `[0.20,0.25,0.30]`；保存并展示列表刷新结果。
4. **JSON 批量发布（约 50 秒）**：选择 SIE 页签和 `examples/demo-sie-manual-import.json`，用 `REJECT` 导入；说明 `UPSERT` 可覆盖自然键相同的记录。
5. **真实 ECMWF（约 2 分钟）**：点击“ECMWF 原始场”，保留 `2t / IFS / ECMWF / MEAN`，起报日期留空。点击“获取并生成预览”，展示实际来源、字段单位、网格点数量、归约数组，以及 `RAW_FIELD_REDUCTION / publishable=false` 标识；可下载预览 JSON，但页面不会把它发布为 RMSD 等评估指标。
6. **2.8 删除和清理（约 50 秒）**：删除步骤 2 和步骤 4 创建的 2099、2098 演示记录，确认列表消失；最后退出登录并说明无 Token 接口返回 401。

演示 ECMWF 时应准确表述：系统已完成真实 Open Data 下载、GRIB2 解析、字段归约和安全预览；`MEAN/ROW_MEAN/SAMPLE` 是工程转换规则，系统明确禁止把这些结果直接冒充 RMSD、BACC 或相关系数入库。正式科研指标应由带观测数据的评估任务生成，再以 `dataKind=EVALUATION_METRIC` 调用批量接口。

## 4. 任务验收映射

| 任务 | 可见操作 | 后端证据 | 验收结果 |
| --- | --- | --- | --- |
| 2.7 更新评估数据 | 列表“更新”弹窗 | `PUT /admin/evaluations/{category}/{id}`，完整校验并按 ID 更新 | 更新后列表与 MySQL 值一致 |
| 2.8 删除评估数据 | 二次确认后删除 | `DELETE /admin/evaluations/{category}/{id}`，不存在返回 404 | 删除后查询不到记录 |
| 2.9 发布评估数据 | 单条发布、JSON 文件导入、上游已计算指标批量发布 | `POST /admin/evaluations`、`/import/manual`、`/import/batch` | 发布后可查询；重复键按 REJECT/UPSERT 规则处理；原始 ECMWF 场不可发布 |

`POST /admin/evaluations/ecmwf/preview` 是独立的 ECMWF 原始场获取能力，不作为 2.9 科学评估指标发布的替代品。

四类数据 ENSO、NAO、SIC、SIE 的字段和指标白名单由 `/admin/evaluations/meta` 返回，前端不再使用 mock 数据，也不再把 SIC/SIE 合成 SeaIce。

## 5. 提交和交付清单

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

- 后端分支 `feat/evaluation-admin-backend`：鉴权、CRUD/导入、ECMWF 获取服务与脚本、迁移、测试、API 和本演示文档。
- 前端分支 `XuYichen-2.7-2.9`：登录、Token/401、真实 API 封装、四类动态表单、CRUD/导入/ECMWF UI、路由守卫和环境变量示例。
- 测试证据：后端单元/集成测试结果、前端生产构建结果、真实 MySQL CRUD/导入记录、一次实际 ECMWF 下载的 metadata 截图或 JSON 摘要。

不要提交 `.env.local`、数据库密码、JWT 密钥、管理员明文密码、`.venv`、MySQL 数据目录或运行日志。建议前后端各建一个 PR，评审通过后再合入主分支。
