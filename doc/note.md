# 天行平台项目团队合作与前后端启动登录指南

为了方便团队合作者快速上手，以及防止不同合作者因本地数据库密码不同而导致代码冲突或敏感凭据泄露，本工程采用了**环境变量解耦与 Git 本地配置隔离**的设计。

---

## 1. 后端启动与配置指南 (`TianXing-Backend-2026`)

### 1.1 核心设计与个人数据库密码隔离
在 `demo_backend/src/main/resources/application.properties` 中，数据库连接信息使用环境变量占位符：
```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/web?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
```
项目根目录下的 `.gitignore` 已经屏蔽了 `.vscode/` 目录。每位合作者在自己本地配置的 `.vscode/launch.json` 绝不会被误提交或覆盖别人的设置。

---

### 1.2 后端启动方式

#### 方式 A：一键启动脚本（全终端通用，最推荐！）
本工程内置了一键启动脚本，会自动解析您本地 `.vscode/launch.json` 中配置的环境变量（如 `DB_PASSWORD`）并自动启动，无需手动敲长命令或密码。

* **如果在 CMD 命令行（或双击运行）**：
  ```cmd
  run.bat
  ```
* **如果在 PowerShell 命令行**：
  ```powershell
  .\run.ps1
  # 或同样使用 run.bat
  ```

#### 方式 B：VS Code 图形界面一键启动
1. 在项目根目录下新建或编辑文件 `.vscode/launch.json`：
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
2. 在 VS Code 中按 **`F5`** 键（或选择 `Launch MybatisDemoApplication` 点击 ▶ 播放按钮）启动。

* **后端服务地址**：`http://localhost:8888`
* **Swagger 接口文档**：`http://localhost:8888/swagger-ui.html`

---

## 2. 前端启动与登录指南 (`TianXing-Frontend-2026`)

### 2.1 依赖安装与启动
在 `TianXing-Frontend-2026` 根目录下执行：

```cmd
# 1. 安装依赖 (推荐 pnpm，如使用 npm 需带 --legacy-peer-deps 参数)
pnpm install

# 2. 启动本地开发服务
pnpm dev
```

---

### 2.2 页面访问与后台登录

* **前端首页地址**：[http://localhost:5173/tianxing/](http://localhost:5173/tianxing/) *(注意末尾带斜杠 `/`)*
* **进入后台登录页**：在浏览器直接输入后台登录地址 [http://localhost:5173/tianxing/#/admin/login](http://localhost:5173/tianxing/#/admin/login)。

---

## 3. 后台管理员登录凭据与账号说明

* **默认管理员账号**：`admin`
* **默认管理员密码**：`admin123`
* **密码哈希存储**：管理员信息保存在数据库 `admin_users` 表中，密码使用 **BCrypt 哈希加密** 存储，会话基于无状态 HS512 JWT Token 认证。
* **初始化 SQL 脚本**：可在 `demo_backend/src/main/resources/sql/admin_schema.sql` 或 `database/migrations/V001__create_admin_user.sql` 中查看与初始化管理员表结构。
