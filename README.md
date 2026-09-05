# ShipFlow

ShipFlow 是一个面向跨境物流与电商履约场景的单体 ERP 练习项目。它覆盖多租户、店铺范围授权、报价与订单履约、仓库作业、物流轨迹、异常/索赔、账单导入与对账、审计与接口自动化测试。

> 当前仓库包含持续迭代中的实现。已存在的测试通过并不等同于生产验收完成；真实供应商回调重放、死信治理、账单逐费用项/汇率快照核算和完整浏览器写操作验收仍须按环境与权限单独执行。

## 模块与能力

| 模块 | 核心能力 |
| --- | --- |
| 身份与权限 | JWT/刷新会话、CSRF、租户 RBAC、店铺范围、审计与 Trace ID |
| 报价与订单 | 报价、订单创建/提交/取消、版本锁、幂等和状态流转 |
| 仓库与物流 | 入库、测量、费用确认、出库、面单、物流渠道、轨迹查询和签名回调 |
| 异常与索赔 | 异常登记、证据、分派、处理记录、索赔与财务确认 |
| 账单与对账 | CSV 批次、错误行/重复行、明细、零差异自动关闭、差异确认/驳回/说明 |
| 运营工作台 | 后端口径的指标、同谓词下钻、待办、风险与最近订单 |

## 技术栈

- 后端：Java 21、Spring Boot、MyBatis、Spring Security、Flyway、MySQL 8
- 前端：Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios
- 契约：OpenAPI 3.0，源文件为 [`openapi/shipflow-api.yaml`](openapi/shipflow-api.yaml)
- 测试：JUnit/MockMvc/Mapper XML、Vitest、数据库驱动 API 自动化

## 本地运行

### 前置条件

- JDK `21.0.11`
- Node.js 18+、npm 9+
- MySQL 8.0
- 本地 `.env`（从 `.env.example` 创建；不得提交）

### 后端

```powershell
Set-Location D:\Projects\shipflow\backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
mvn test
```

应用配置中 Flyway 默认关闭。执行迁移前必须先确认目标数据库、当前 `flyway_schema_history`、影响范围、备份和回滚方案；不得修改历史迁移或向共享环境写入演示数据。

### 前端

```powershell
Set-Location D:\Projects\shipflow\frontend
npm install
npm run typecheck
npm run test:unit
npm run build
npm run dev
```

开发访问链路：`浏览器 :5173 -> Vite /api 代理 -> Nginx -> Spring Boot :8080 -> MySQL`。

## 接口与生命周期文档

- [接口文档与契约使用说明](docs/API_REFERENCE.md)
- [后端、前端、数据库与接口自动化测试生命周期](docs/PROJECT_LIFECYCLES.md)
- [领域和数据库设计](docs/04-domain-model.md) / [数据库部署](docs/06-database-deployment.md)
- [认证与 RBAC 设计](docs/14-auth-rbac-design.md)
- [项目模块生命周期记录](docs/module-lifecycle/)
- [企业 ERP 差距审计](docs/audits/2026-08-30-enterprise-erp-gap-audit.md)

## 安全与发布规则

- 不提交 `.env`、JWT/第三方密钥、真实客户数据、数据库数据目录、构建输出、测试报告或浏览器会话数据。
- 所有写接口必须具备租户范围、权限、幂等性、事务/版本控制、审计与 Trace ID。
- 金额使用 `BigDecimal`；重量使用 kg；数据库事实时间以 UTC 存储，业务日按 `Asia/Shanghai` 计算。
- 推送前执行 `git diff --check`、后端与前端测试，并只显式暂存源代码、迁移、文档和测试文件。

## 验证基线

最近一次本地自动化基线：JDK 21 Maven 97 个套件、421 项测试通过；前端 Vitest 30 个文件、122 项测试通过，typecheck 与生产构建通过。运行态/浏览器验收须在隔离环境中另行记录，不能由此基线替代。
