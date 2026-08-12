# MySQL 8.0 集成测试基础设施

## 范围

本阶段验证当前 82 个 OpenAPI operationId 的 MyBatis 映射、MySQL 字段映射、租户隔离、权限 JOIN、状态机和幂等约束。空库初始化顺序以 `31-migration-chain-and-integration-acceptance.md` 为准。

## 单元测试与集成测试

- 单元测试使用 Mock Mapper 和内存 BCrypt 摘要，不需要数据库。
- 集成测试使用真实 MySQL 8.0、真实 MyBatis Mapper XML 和真实数据库事务，验证 SQL、resultMap、租户条件和 JOIN 结果。
- `mvn test` 只执行 Surefire 单元测试；`*IT.java` 由 Failsafe 在 `verify` 阶段执行。

## 环境变量

集成测试只能读取以下变量，项目文件不包含真实连接信息、密码或环境变量实际值：

```text
SHIPFLOW_IT_DB_URL
SHIPFLOW_IT_DB_USERNAME
SHIPFLOW_IT_DB_PASSWORD
```

集成测试不会自动执行 `schema.sql` 或迁移；`spring.flyway.enabled=false`，Spring SQL 初始化也被关闭。执行前必须由受控初始化链准备空测试库。

## 测试数据隔离

每个测试方法在 Spring 事务中插入最小测试数据。租户编码、用户名、权限编码和角色编码使用随机后缀，密码摘要运行时由 BCrypt 生成，不依赖 `init_data.sql` 固定账号。

测试使用 `shipflow_test` 数据库，测试数据在事务结束时回滚，未修改 `shipflow` 开发库中的已有数据。

## 执行命令

普通单元测试：

```bash
./mvnw test
```

集成测试：

```bash
./mvnw verify -Pintegration
```

集成环境实测时，Windows 通过 `172.29.128.47:3307` 连接 Ubuntu Docker 中的 MySQL 8.0，使用数据库 `shipflow_test`。

## 实际验证结果

实际执行命令：`./mvnw.cmd verify -Pintegration`

- 操作系统：Windows
- 数据库：Ubuntu Docker MySQL 8.0
- 测试数据库：`shipflow_test`
- Surefire 单元测试：54
- Failsafe 集成测试：8
- Failures：0
- Errors：0
- Skipped：0
- 构建结果：`BUILD SUCCESS`
- 总耗时：23.094 秒

报告文件位于 `backend/target/surefire-reports` 和 `backend/target/failsafe-reports`。本次集成测试使用随机测试数据和事务回滚，未修改 `shipflow` 开发库。

## 注意事项

如果连接失败、SQL 执行失败或事务回滚失败，必须如实报告失败原因，不得将测试结果伪造为通过。当前仍未实现登录 Controller、JWT 登录编排、Refresh Token、Cookie、CSRF 或其他业务模块。
