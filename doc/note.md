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

首先运行src/main/resources/sql/admin_schema.sql，设置初始管理员账户密码

* **默认管理员账号**：`admin`

* **默认管理员密码**：`password` *(安全规则要求密码长度必须 >= 8 位)*

* **自定义初始管理员**：若需在本地改用其他初始账号密码，可在 `.vscode/launch.json` 的 `env` 节点中添加环境变量：
  
  ```json
  "ADMIN_BOOTSTRAP_USERNAME": "自定义账号",
  "ADMIN_BOOTSTRAP_PASSWORD": "自定义密码(>=8位)"
  ```

---

## 4. Copernicus CDS 数据凭据配置（气压网格抓取）

`demo_backend/scripts/ecmwf_fetch.py` 中气压网格场（`seasonal-monthly-single-levels` 季节预报）走 **Copernicus CDS API**，需要 CDS 平台账号的 API Key 认证，脚本内 `cdsapi.Client()` 会自动读取凭据文件。

### 4.1 本机（Windows）配置
凭据保存在当前用户主目录下的 `.cdsapirc` 文件（脚本运行时自动读取）：

```
C:\Users\<您的用户名>\.cdsapirc
```

文件内容为两行（`url` + `key`）：
```properties
url: https://cds.climate.copernicus.eu/api
key: <您的 CDS API Key>
```

> CDS API Key 在 [Copernicus CDS 官网](https://cds.climate.copernicus.eu/) 注册账号后在个人页面生成。

### 4.2 远程服务器配置（两种方式任选其一）
**方式 A：复制同一份凭据文件到远程用户主目录**
```bash
# 从本机上传到远程（Linux 服务器示例）
scp C:\Users\<您的用户名>\.cdsapirc root@<服务器IP>:~/.cdsapirc
# 或
rsync -av ~/.cdsapirc root@<服务器IP>:~/.cdsapirc
```

**方式 B：使用环境变量（优先级更高，便于 CI/CD 管理）**
```bash
export CDSAPI_URL="https://cds.climate.copernicus.eu/api"
export CDSAPI_KEY="<您的 CDS API Key>"
```

### 4.3 注意事项
* **凭据保密**：`.cdsapirc` 及环境变量中的 Key 属于敏感信息，严禁提交到 Git 仓库或写入文档/代码。
* **API 迁移**：Copernicus 正在迁移到 v2 API，若出现 401 报错，请前往官网重新生成 Key 并更新 `url`。
* **未配置时的行为**：未安装 `cdsapi` 或未配置凭据时，脚本会报错提示 `请执行 pip install cdsapi 并配置 ~/.cdsapirc`。
