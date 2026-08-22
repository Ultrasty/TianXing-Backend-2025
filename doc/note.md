# 天行平台项目团队合作与前后端启动登录指南

为避免不同成员的本地数据库配置相互覆盖或将敏感凭据提交到仓库，后端数据库连接统一通过环境变量注入。

---

## 1. 后端启动与配置指南（`TianXing-Backend-2026`）

### 1.1 数据库环境变量

`demo_backend/src/main/resources/application.properties` 只引用下面三个环境变量，仓库中不保存数据库地址、用户名或密码：

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

可在终端、CI 或未提交的 IDE 启动配置中设置它们。例如 PowerShell：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/web?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='你的本地数据库密码'
```

项目根目录的 `.gitignore` 已屏蔽 `.vscode/`、`.idea/`、Python 缓存和本地环境；本地启动配置不能提交。

### 1.2 已泄露凭据的更换流程

历史提交中出现过的数据库凭据不能仅靠删除当前文件失效。数据库管理员应在数据库服务端更换该账号密码（必要时创建新账号并撤销旧账号），更新部署环境/CI/本地未提交配置中的 `DB_PASSWORD`，再验证应用可连接；旧凭据应立即停止使用。

---

### 1.3 后端启动方式

#### 方式 A：一键启动脚本（推荐）

本工程的一键启动脚本会读取本地未提交的 VS Code 启动配置中的环境变量并启动服务：

```cmd
run.bat
```

在 PowerShell 中也可使用：

```powershell
.\run.ps1
```

#### 方式 B：VS Code 图形界面启动

在项目根目录创建未提交的 `.vscode/launch.json`：

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Launch MybatisDemoApplication",
      "request": "launch",
      "mainClass": "com.tongji.enso.mybatisdemo.MybatisDemoApplication",
      "projectName": "mybatisdemo",
      "env": {
        "DB_USERNAME": "root",
        "DB_PASSWORD": "你的本地数据库密码",
        "DB_URL": "jdbc:mysql://127.0.0.1:3306/web?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
      }
    }
  ]
}
```

在 VS Code 按 `F5` 或选择 `Launch MybatisDemoApplication` 启动。

- 后端服务：`http://localhost:8888`
- Swagger：`http://localhost:8888/swagger-ui.html`

---

## 2. 前端启动与登录指南（`TianXing-Frontend-2026`）

### 2.1 依赖安装与启动

在前端根目录执行：

```cmd
pnpm install
pnpm dev
```

### 2.2 页面访问与后台登录

- 前端首页：`http://localhost:5173/tianxing/`
- 后台登录：`http://localhost:5173/tianxing/#/admin/login`

---

## 3. 后台管理员账号说明

- 默认管理员账号：`admin`
- 默认管理员密码：`admin123`
- 管理员密码存于 `admin_users` 表的 BCrypt 哈希字段；会话使用无状态 JWT。
- 初始化 SQL 见 `demo_backend/src/main/resources/sql/admin_schema.sql` 或 `database/migrations/V001__create_admin_user.sql`。
