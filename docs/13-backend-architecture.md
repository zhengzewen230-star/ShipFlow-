# ShipFlow 后端基础架构说明

## 1. 技术选型与版本

本阶段采用以下基线：

- Java 17；Spring Boot 3.5.16。该版本是当前 3.5.x 的稳定补丁版本，并要求至少 Java 17。
- Maven Wrapper，默认 Maven 3.9.11；开发和 CI 优先使用 `./mvnw` 或 `mvnw.cmd`。
- MyBatis Spring Boot Starter 3.0.5。
- MySQL Connector/J 运行时驱动，目标数据库 MySQL 8.0。
- Spring Security 仅加入基础配置，本阶段不实现登录、JWT、Refresh Token 和权限校验。
- Jakarta Validation，用于请求参数约束。
- Spring Boot Actuator，当前开放健康检查。
- Flyway，用于后续数据库迁移治理；本阶段不执行 V001、V002。
- springdoc-openapi 2.8.17，与 OpenAPI 3.0.3 契约配合。
- JUnit 5、Mockito 和 Spring Boot Test，用于公共基础设施测试。

本机验证使用 Maven 3.9.16 和 JDK 21.0.11 运行，项目编译目标保持 Java 17；不要求运行时必须使用 JDK 17。

## 2. 分层原则

后端按请求入口、应用编排、领域规则和基础设施分层：

1. Web/API 层：负责 HTTP 路由、请求 DTO、参数校验和响应映射。
2. Application 层：负责事务边界、幂等处理、权限入口和跨领域流程编排。
3. Domain 层：负责状态机、金额计算、费用调整和领域不变量。
4. Infrastructure 层：负责 MyBatis Mapper、数据库访问、外部物流商和异步消息适配。
5. Common 层：负责统一响应、错误码、Trace ID、时间、异常和通用工具。

Controller 不直接编写 SQL，Domain 不依赖 HTTP 类型；写接口必须同时设计幂等、事务、重复请求和审计行为。

## 3. 包结构

根包为 `com.shipflow`：

```text
com.shipflow
├── common       # 统一响应、异常、Trace ID、通用类型
├── config       # Jackson、Clock、Web 和基础配置
├── security     # Spring Security 基础配置；后续承载 JWT 和权限
├── auth         # 认证与会话，后续实现
├── tenant       # 租户、用户和角色，后续实现
├── quote        # 报价和价格规则，后续实现
├── shipment     # 物流订单、地址、包裹和商品，后续实现
├── warehouse    # 入库、复称和出库，后续实现
├── tracking     # 轨迹回调和轨迹查询，后续实现
├── billing      # CSV 账单导入，后续实现
├── reconciliation # 费用对账，后续实现
└── exceptioncase   # 异常件和索赔，后续实现
```

每次只实现一个业务模块；本轮只实现公共基础设施，不创建大量空 Controller、Service、Mapper 或 Entity。

## 4. 配置与环境变量

配置文件：

- `application.yml`：安全默认值和通用配置，不写真实密码。
- `application-local.yml.example`：本地配置模板，只包含变量引用和示例结构。
- `application-test.yml`：测试隔离配置，不依赖 Ubuntu MySQL。
- `application-local.yml`：禁止提交，由开发者在本地按需创建。

数据库配置统一从环境变量读取：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。密码不得写入 YAML、测试代码、日志或 Git。所有业务时间使用 UTC；数据库连接和 JSON 输出也按 UTC 处理。

## 5. 统一响应、错误码与 Trace ID

成功响应使用 `success=true`、`traceId`、`message` 和 `data`。错误响应使用 `success=false`、`traceId` 和 `error.code/message/details`。

`TraceIdFilter` 接收 `X-Trace-Id`，不存在时生成 UUID；将其写入 MDC 和响应头，并由响应结构读取。请求结束后必须清理 MDC，避免线程复用造成链路污染。

参数错误使用 `COMMON-1001` 或 `COMMON-1008`，未登录使用 `COMMON-1002/1003`，权限不足使用 `COMMON-1004`，并发版本冲突使用 `COMMON-1005`，资源不存在使用 `COMMON-1006`，内部错误使用 `COMMON-1007`。最终业务错误码以 `docs/10-error-codes.md` 为准。

## 6. 后续实施顺序

1. 基础骨架：统一响应、异常、Trace ID、配置和健康检查。
2. 数据库接入治理：设计 Flyway 的现有数据库基线流程和全新数据库初始化流程。
3. 认证与租户上下文：登录、tenantCode、JWT、Refresh Token 和跨租户隔离。
4. 租户与用户：租户、用户、角色和审计安全日志。
5. 报价：价格规则、报价有效期、金额和报价快照。
6. 订单：幂等下单、状态机和订单履约主流程。
7. 仓库：入库、复称费用调整和出库。
8. 轨迹、账单、对账、异常件和索赔。
9. 每完成一个模块生成学习笔记，并同时补充接口断言和数据库断言测试。

## 7. 本轮明确不实现

- 不实现登录、JWT、Refresh Token、角色权限和跨租户授权逻辑。
- 不实现报价、物流订单、复称、出库、轨迹、账单、对账或索赔接口。
- 不连接或修改 Ubuntu MySQL，不执行 V001、V002。
- 不开启 `baselineOnMigrate`，不复制或改写既有数据库迁移脚本。
- 不安装前端、测试平台或生产部署依赖。
- 不提交 `application-local.yml`、密码、Token、真实客户数据、日志或数据库备份。

## 8. 本轮验证结果

- `mvn -U clean test` 已在 Windows 本机实际执行成功。
- Surefire 统计：Tests run 6，Failures 0，Errors 0，Skipped 0。
- Spring 测试上下文启动成功。
- test 配置启动成功；`GET /actuator/health` 返回 HTTP 200，响应为 `{"status":"UP"}`。
- Jenkins 服务 PID 7084 使用 JDK 21 运行 `jenkins.war` 并监听9999端口，不属于 ShipFlow，本轮未停止。
- Springdoc 和 Mockito 的提示仅记录为依赖/运行时警告性质，不在本轮升级或降级依赖，未影响本轮验收。
- 本次工作区检查未发现密码、Token、`.env`、`application-local.yml`、日志或数据库备份进入 Git；`target/` 由 `.gitignore` 忽略。

Swagger UI 和 OpenAPI JSON 的 HTTP 访问结果未在本次工具会话中独立取得，因此不将其写成已验证通过；待实际访问后补录具体 HTTP 状态和响应结果。
