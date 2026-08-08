# ShipFlow 数据库验证报告

## 1. 验证结论

ShipFlow 数据库已在 Ubuntu Linux 的 MySQL 8.0 Docker 容器中实际执行并验证通过。

- `schema.sql` 执行无 ERROR。
- `init_data.sql` 执行无 ERROR。
- `verify.sql` 全部检查 PASS，包括 V003 注释修复检查。
- V003 已在 Ubuntu 的 MySQL 8.0 Docker 容器中实际执行成功。
- 数据库当前共30张表；两张 API 支撑表共26个字段注释已修复。
- 本报告不记录服务器 IP、数据库密码或其他敏感连接信息。

## 2. 验证环境

| 项目 | 实际环境 |
|---|---|
| 操作系统 | Ubuntu Linux |
| 数据库 | MySQL 8.0 |
| 容器名称 | `my-mysql-docker` |
| 容器内部端口 | `3306` |
| Ubuntu 主机端口 | `3307` |
| 数据库 | `shipflow` |

服务器上的 MySQL 5.7 容器和 Jira 容器未被操作、停止、删除或重建。

## 3. 执行顺序

实际按以下顺序执行：

1. 将 `database/schema.sql` 上传到现有 `my-mysql-docker` 容器。
2. 将 `database/init_data.sql` 上传到现有容器。
3. 将 `database/migrations/V003__repair_v002_comments.sql` 和 `database/verify.sql` 上传到现有容器。
4. 进入容器内 MySQL 客户端，由执行人员交互输入数据库密码，并设置连接字符集。
5. 使用 `SOURCE` 依次执行 V003 和 verify：

```sql
SOURCE /tmp/shipflow-schema.sql;
SOURCE /tmp/shipflow-init_data.sql;
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SOURCE /tmp/shipflow-V003.sql;
SOURCE /tmp/shipflow-verify.sql;
```

密码未写入 SQL、文档或 Git。

## 4. 验证结果

| 检查项 | 实际结果 | 结论 |
|---|---:|---|
| `database_charset` | PASS | 通过 |
| `table_count` | 30/30 | PASS |
| `table_collation` | 30 张表全部正确 | PASS |
| `user_count` | 10/10 | PASS |
| `role_count` | 10/10 | PASS |
| `permission_count` | 10/10 | PASS |
| `store_count` | 4/4 | PASS |
| `provider_count` | 2/2 | PASS |
| `channel_count` | 4/4 | PASS |
| `service_country_count` | 7/7 | PASS |
| `price_rule_count` | 5/5 | PASS |
| `price_rule_tier_count` | 10/10 | PASS |
| `active_channel_without_price_rule` | 0/0 | PASS |
| `finance_role_permissions` | 6/6 | PASS |
| `mock_callback_permission` | 1/1 | PASS |
| `required_unique_indexes` | 15/15 | PASS |
| `approved_order_status_values` | 0 个异常值 | PASS |
| `support_tables_exist` | 2/2 | PASS |
| `support_table_comments` | 2/2 | PASS |
| `support_column_comments` | 26/26 | PASS |
| `support_comment_mojibake` | 0 | PASS |
| `key_comment_values` | 2/2 | PASS |
| V003 执行 | 无 ERROR | PASS |
| `schema.sql`执行 | 无 ERROR | PASS |
| `init_data.sql`执行 | 无 ERROR | PASS |
| `verify.sql`执行 | 全部 PASS | PASS |

## 5. 验证范围说明

本次实际验证覆盖：

- 数据库字符集和表排序规则；
- 表数量和核心初始化数据数量；
- 平台、商家、仓库、财务和 Mock 系统角色权限；
- 物流商、物流渠道和服务国家；
- 价格规则和价格阶梯；
- ACTIVE 渠道是否存在有效价格规则；
- 关键唯一索引；
- 订单状态字段值；
- schema 和初始化数据执行过程中的错误。

## 6. 当前范围和限制

- 本次验证只覆盖数据库脚本和初始化数据，不代表后端业务接口已经实现。
- 尚未开发 Controller、Service 或接口自动化代码。
- 报价快照不可变、价格阶梯发布校验、跨租户业务校验仍需后续后端实现和测试验证。
- 初始化 BCrypt 值仅用于测试环境，不是生产密码。

## 7. V002 迁移状态

V002 已在 Ubuntu 的 `my-mysql-docker` 容器中实际执行并验证通过，数据库现为30张表。

| 检查项 | 实际结果 |
|---|---|
| `table_count` | 30/30 PASS |
| `table_collation` | 30张表全部正确 PASS |
| `required_unique_indexes` | 18/18 PASS |
| `support_tables_exist` | 2/2 PASS |
| `support_table_collation` | 2/2 PASS |
| `refresh_foreign_keys` | 3/3 PASS |
| `support_status_checks` | 2/2 PASS |
| `approved_order_status_values` | 0个异常值 PASS |
| 原有初始化数据 | 数量保持不变 |
| 执行过程 | 无 ERROR |

本次记录不包含服务器 IP、数据库密码、备份文件或其他敏感信息。

## 8. V003 迁移状态

V003 已在 Ubuntu Linux 的 Docker MySQL 8.0 容器 `my-mysql-docker` 中实际执行成功。V003 只修复注释，没有删除、重建表或修改业务数据。

乱码原因是执行 V002 时 MySQL 客户端连接字符集不正确。数据库或表使用 `utf8mb4` 不能代替客户端连接字符集设置。以后手工执行含中文 SQL 必须使用：

```bash
mysql --default-character-set=utf8mb4
```

并在 MySQL 会话中执行：

```sql
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

`api_idempotency_record` 的15个字段和 `auth_refresh_session` 的11个字段注释均已验证正常；原有 verify.sql 检查全部 PASS。
