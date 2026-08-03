# Refresh Token 集成测试记录

## 测试范围

Refresh Token 集成测试使用真实 Spring 容器、MyBatis Mapper 和 MySQL 8.0，验证会话持久化以及轮换事务边界。本阶段不接入 Controller、Cookie、CSRF、Logout 或登录流程。

## 已完成的真实验证

已由项目负责人手动执行：

```text
.\mvnw.cmd verify -Pintegration
```

环境为 Windows 连接 Ubuntu Docker 中的 MySQL 8.0，测试数据库为 `shipflow_test`。本次实际结果：

- Failsafe 集成测试：10
- Failures：0
- Errors：0
- Skipped：0
- BUILD SUCCESS
- 总耗时：30.148 秒
- `RefreshSessionMapperIT`：2 个测试通过

未记录数据库地址、密码、Refresh Token、Token 摘要或 HMAC 密钥。

## 新增 Service 事务测试

新增 `RefreshTokenSessionServiceIT`，不使用测试方法级 `@Transactional`，通过 Spring 容器中的真实 Service Bean 验证：

- 正常轮换后旧会话为 `ROTATED`，新会话为 `ACTIVE`；
- 新旧会话保持同一个 `family_id`；
- 新会话继承原绝对过期时间；
- 重放 `ROTATED` Token 抛出 `AUTH-1002` 后，family 撤销结果仍提交；
- 数据库唯一约束异常导致轮换事务回滚，旧会话保持 `ACTIVE`，不会产生第二个有效会话。

Service 集成测试已由项目负责人在真实 MySQL 环境执行通过。本轮 Failsafe 集成测试总数为 13，全部通过。

- Tests run：13
- Failures：0
- Errors：0
- Skipped：0
- BUILD SUCCESS
- 总耗时：26.810 秒

本次未记录数据库连接地址、密码、原始 Refresh Token、Token 摘要或 HMAC 密钥。

## 数据隔离和安全约束

- Service 集成测试使用随机租户、用户和 family 数据；
- 测试结束后通过 `finally` 生命周期清理会话、用户和租户数据；
- 测试不使用开发库固定账号，不保存原始 Refresh Token；
- HMAC 密钥仅在测试内存中随机生成；
- 普通 `test` 不执行 `*IT.java`；
- 远程 MySQL 集成测试由项目负责人手动执行，本阶段未由 Codex 连接数据库。

## 历史重复摘要定位（只读）

如需定位此前失败执行留下的记录，只能将实际报错中的摘要填入变量后执行查询；不得执行删除语句：

```sql
SET @duplicate_hash = '<填写报错中的64位摘要>';

SELECT id, family_id, previous_session_id, user_id, status, created_at
FROM auth_refresh_session
WHERE token_hash = @duplicate_hash
ORDER BY id;
```

当前测试已改为每次运行使用运行时随机 Token、HMAC 摘要、family 和用户标识。唯一约束异常测试只在同一测试内复用本次刚生成的摘要，不依赖历史数据。
