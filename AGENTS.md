# TianXing-Backend Agent 指南与协作规范

本文件为参与 `TianXing-Backend-2026` 项目开发的 AI Agent 及协同开发者提供架构与开发规范指南。

## 1. 项目简介
- **项目名称**：天行平台后端 Demo (TianXing-Backend-2026)
- **技术栈**：Spring Boot 2.7.9 + MyBatis + MySQL + Maven + Swagger2
- **核心业务**：天气事件预报数据的存储、查询与格式转换（处理高维数组 JSON 数据）。

## 2. 核心架构与包结构
代码目录位于 `demo_backend/src/main/java/com/tongji/enso/mybatisdemo/`:
- `controller/`: RESTful API 路由层（如 `MeteoController.java`）
- `service/`: 业务逻辑接口与实现类（如 `MeteoService.java`, `MeteoServiceImpl.java`）
- `mapper/`: MyBatis 数据库操作接口
- `entity/`: 数据库实体对象映射类（如 `Meteo.java`）
- `resources/mapping/`: XML SQL 映射文件

## 3. 数据规范与开发约束
1. **数据库凭据**：
   - 严禁在 `application.properties` 中硬编码数据库真实密码。
   - 使用环境变量替代：`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`。
2. **气象高维数组处理**：
   - 数据库中 `data` 字段为 JSON 格式。
   - 格点预报数据格式为 `[time, lat, lon]` 高维数组，须使用 Jackson (`ObjectMapper`) 进行序列化与反序列化。
3. **安全与版本控制**：
   - 保持 `.vscode/`、`.agents/` 以及敏感本地配置在 `.gitignore` 中屏蔽。

## 4. 运行与验证指令
- **编译/打包**：
  ```powershell
  cd demo_backend
  .\mvnw.cmd clean package
  ```
- **启动服务**：
  ```powershell
  $env:DB_PASSWORD="your_password"
  .\mvnw.cmd spring-boot:run
  ```
- **访问接口测试**：
  - Swagger 接口文档：`http://localhost:8888/swagger-ui.html`
  - Meteo 查询全部：`http://localhost:8888/meteo/findAll`
