# 天行后台系统团队合作与本地配置指南

为了防止多位合作者因本地数据库密码不同而导致代码冲突或敏感凭据泄露，本工程采用了**环境变量解耦与 Git 本地配置隔离**的设计。

---

## 1. 核心设计说明

1. **公共配置解耦**：
   在 `demo_backend/src/main/resources/application.properties` 中，数据库连接信息使用环境变量占位符：
   ```properties
   spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/web?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC}
   spring.datasource.username=${DB_USERNAME:root}
   spring.datasource.password=${DB_PASSWORD:}
   ```
   公共仓库中不保存任何硬编码密码。

2. **本地配置隔离**：
   项目根目录下的 `.gitignore` 已经将 `.vscode/` 目录添加到忽略规则中。因此，每位合作者可以在自己本地的 `.vscode/launch.json` 中配置个人专用的数据库密码和调试环境，且**绝不会被误提交或覆盖别人的配置**。

---

## 2. 合作者本地配置与运行步骤

### 方式一：VS Code 一键启动/调试（最推荐）

1. 在项目根目录下新建（或编辑）文件 `.vscode/launch.json`；
2. 填入如下内容（将 `DB_PASSWORD` 修改为您自己本地 MySQL 的真实密码）：

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

3. **运行方式 A（GUI 界面点击）**：在 VS Code 中打开任意 Java 文件，按 **`F5`** 键（或在左侧【运行与调试】面板中选择 `Launch MybatisDemoApplication` 并点击播放按钮 ▶），即可自动读取本地密码并一键启动后端！

4. **运行方式 B（命令行一键启动脚本 - 补充说明）**：
   如果您习惯在 PowerShell 命令行中运行，但又不想每次手动输入密码，可以直接在项目根目录下运行工程内置的启动脚本 `run.ps1`：
   ```powershell
   .\run.ps1
   ```
   *说明：该脚本会自动读取并解析您本地 `.vscode/launch.json` 中配置的环境变量（如 `DB_PASSWORD`），并直接拉起 Spring Boot 后端服务，无需手动敲命令或输入密码。*

---

### 方式二：手动传入环境变量启动

若需要在 CMD 终端或无 GUI 环境中手动启动：

* **Windows CMD 终端**：
  ```cmd
  cd demo_backend
  set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
  set DB_PASSWORD=你的本地数据库密码
  mvnw spring-boot:run
  ```

---

## 3. 后台管理员账户说明

* **默认管理员账号**：`admin`
* **默认管理员密码**：`12345678` *(注意：安全机制要求管理员密码长度必须 >= 8 位)*
* **数据库安全机制**：首次启动服务时，系统会自动在数据库 `admin_user` 表中以 **PBKDF2 哈希加密** 格式安全保存管理员密码。
* **自定义初始管理员**：若需使用其他初始账号密码，可在 `.vscode/launch.json` 的 `env` 节点中增加以下环境变量：
  ```json
  "ADMIN_BOOTSTRAP_USERNAME": "自定义账号",
  "ADMIN_BOOTSTRAP_PASSWORD": "自定义密码(>=8位)"
  ```
