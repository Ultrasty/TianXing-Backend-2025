# 2.7–2.9 前后端联调演示与交付

## 1. 一次性准备

### MySQL 8

在管理员 PowerShell 中启动已安装的服务：

```powershell
Start-Service MySQL80
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root -p
```

在 `mysql>` 中执行（若 `web` 已有完整业务数据，跳过备份导入）：

```sql
CREATE DATABASE IF NOT EXISTS web CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE web;
SOURCE C:/VScodework/TianXingProject/three_in_one_deploy_package_20251023/mysql_backup_20251023.sql;
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V001__create_admin_user.sql;
```

执行 V002 前，先单独运行其中四段重复检查；只有四段都返回空结果时，才执行四条 `ALTER TABLE`，或直接 `SOURCE` 完整文件：

```sql
SOURCE C:/VScodework/TianXingProject/TianXing-Backend-2026/database/migrations/V002__add_evaluation_natural_key_indexes.sql;
```

在仓库根目录生成管理员 BCrypt 哈希：

```powershell
cd C:\VScodework\TianXingProject\TianXing-Backend-2026
$env:ADMIN_PASSWORD='换成至少12位的演示密码'
.\scripts\generate-bcrypt-hash.ps1
Remove-Item Env:ADMIN_PASSWORD
```

复制输出的哈希，在 MySQL 中创建账号（不要把明文密码或真实哈希提交到 Git）：

```sql
INSERT INTO admin_user(username, password_hash, enabled)
VALUES ('admin', '粘贴BCrypt哈希', 1)
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash), enabled=1;
```

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
$env:DB_PASSWORD='你的MySQL密码'
$env:ADMIN_JWT_SECRET='换成至少32字节且不提交到Git的联调密钥'
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
5. **真实 ECMWF（约 2 分钟）**：在 SIE 页签点击“ECMWF 获取”，填写一个远期入库键（例如 `year=2097`、`month=10`、`varModel=RMSD`），保留 `2t / IFS / ECMWF / MEAN`，起报日期留空。点击“获取并生成预览”，展示返回的实际来源、字段单位、网格点数量和转换数组；确认后点击“以 UPSERT 方式入库”，再从列表查询该记录。
6. **2.8 删除和清理（约 50 秒）**：分别删除 2099 和 ECMWF 演示记录，确认列表消失；最后退出登录并说明无 Token 接口返回 401。

演示 ECMWF 时应准确表述：系统已完成真实 Open Data 下载、GRIB2 解析、字段归约和标准入库闭环；`MEAN/ROW_MEAN/SAMPLE` 是工程转换规则，不应冒充 RMSD、BACC 或相关系数等领域检验公式。正式科研指标应由带观测数据的评估任务生成后再入库。

## 4. 任务验收映射

| 任务 | 可见操作 | 后端证据 | 验收结果 |
| --- | --- | --- | --- |
| 2.7 更新评估数据 | 列表“更新”弹窗 | `PUT /admin/evaluations/{category}/{id}`，完整校验并按 ID 更新 | 更新后列表与 MySQL 值一致 |
| 2.8 删除评估数据 | 二次确认后删除 | `DELETE /admin/evaluations/{category}/{id}`，不存在返回 404 | 删除后查询不到记录 |
| 2.9 发布评估数据 | 单条发布、JSON 文件导入、ECMWF 预览后 UPSERT | `POST /admin/evaluations`、`/import/manual`、`/ecmwf/preview`、`/import/batch` | 发布后可查询；重复键按 REJECT/UPSERT 规则处理 |

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
- 前端分支 `feat/evaluation-admin-integration`：登录、Token/401、真实 API 封装、四类动态表单、CRUD/导入/ECMWF UI、路由守卫和环境变量示例。
- 测试证据：后端单元/集成测试结果、前端生产构建结果、真实 MySQL CRUD/导入记录、一次实际 ECMWF 下载的 metadata 截图或 JSON 摘要。

不要提交 `.env.local`、数据库密码、JWT 密钥、管理员明文密码、`.venv`、MySQL 数据目录或运行日志。建议前后端各建一个 PR，评审通过后再合入主分支。
