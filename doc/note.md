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
