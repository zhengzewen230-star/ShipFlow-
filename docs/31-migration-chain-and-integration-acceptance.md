# 82 接口迁移链与集成验收准备

## 结论

原始 Flyway 目录不能从空 MySQL 8.0 直接执行：仓库缺少 V001，最早的 V002 依赖 `tenant`、`sys_user` 等基表；当前 `schema.sql` 又已经包含 V002 的两张表及 V006 的 `transport_mode`，所以不能在当前基线后直接执行原始 V002/V006。

当前可执行的空库初始化路径是受控基线链 `database/http-test-control/01` 至 `07`：01 非破坏性创建当前 30 张基表，02 载入演示数据，03–07 以兼容方式补齐 V002–V006 内容。静态 Mapper 扫描未发现引用缺表，覆盖认证/RBAC、物流主数据、报价/订单/仓库、轨迹、账单/对账、异常/索赔、审计和运营聚合。

未新增 V007：晚到版本不能修复“V002 先于不存在的 V001 执行”的历史顺序问题；不得伪造 V001 或改写 V002–V006。若要实施 Flyway-only 新库初始化，需负责人批准独立的版本化基线策略。

## 原始迁移检查

| 顺序 | 文件 | 用途 | 与当前 schema 的关系 | 风险/处理 |
|---:|---|---|---|---|
| 1 | V001 | 缺失，原应创建业务基表 | 当前 `schema.sql` 是该基线的唯一来源 | 阻塞空库原始 Flyway |
| 2 | V002 | API 幂等与 Refresh 会话表 | 当前 schema 已包含同构表 | 受控 03 使用 `IF NOT EXISTS` |
| 3 | V003 | V002 中文注释修复 | 不改变字段、索引、外键或数据 | 依赖 V002 |
| 4 | V004 | 平台租户读/管权限 | 与权限 Mapper 名称一致 | DML 幂等 |
| 5 | V005 | 店铺、用户、RBAC 权限矩阵 | 与安全配置和权限名称一致 | DML 幂等 |
| 6 | V006 | 渠道运输方式、物流主数据权限 | 当前 schema 已有字段与约束 | 受控 07 跳过重复 ALTER，只补 DML |

## 下一轮在 `shipflow_test` 的执行顺序

本轮没有连接或执行数据库。得到负责人批准后：

1. 只读预检：`SELECT DATABASE(), VERSION();` 必须返回 `shipflow_test` 与 MySQL 8.0；确认业务表数为 0、无业务数据和 `flyway_schema_history`。
2. 依次执行 `database/http-test-control/01_schema_empty_target.sql`、`02_init_data.sql`、`03_V002__add_api_support_tables.sql`、`04_V003__repair_v002_comments.sql`、`05_V004__add_tenant_management_permissions.sql`、`06_V005__add_tenant_rbac_permissions.sql`、`07_V006__add_logistics_master_data_permissions.sql`。每步先查 `SHOW WARNINGS` 和关键表/权限计数；禁止 `--force`。
3. 设置 `SHIPFLOW_IT_DB_URL`、`SHIPFLOW_IT_DB_USERNAME`、`SHIPFLOW_IT_DB_PASSWORD` 指向 `shipflow_test`，执行 `mvn verify -Pintegration`。测试数据以事务回滚，禁止连接 `shipflow` 或 `shipflow_qa`。
4. 数据库集成测试通过后，才启动受控后端实例进行 HTTP 验收。

## 回滚与测试计划

02–07 为 `INSERT ... WHERE NOT EXISTS` 或元数据修复，不能用通用反向 DML 回滚。任一步失败立即停止并保留错误输出；仅空测试库且负责人单独批准 `99_RESET_DESTRUCTIVE__APPROVAL_REQUIRED.sql.disabled` 后才能重置。不得删除业务数据或篡改 Flyway history。

集成测试须验证：30 张表、关键外键/唯一键/CHECK、UTC 与 `BigDecimal` 精度、每个 Mapper 的租户隔离、订单/仓库/轨迹/异常/索赔/对账状态机与幂等、权限矩阵、审计脱敏。HTTP 验收最后进行，且不替代数据库断言。
