# V018/V019 Flyway 历史补登记：Navicat 人工执行说明

## 1. 用途

本 SQL 用于把已经由 Navicat 人工执行、且已通过生产库结构只读核验的 V018、V019 补登记到 `shipflow.flyway_schema_history`。

这是 Flyway 历史补登记，不是重新执行迁移。SQL 不包含 V018/V019 原始迁移正文，只写入两条 Flyway history 记录，不修改任何业务表。

核对值：

| 版本 | 执行顺序 | version | description | script | checksum |
| --- | ---: | --- | --- | --- | ---: |
| V018 | 1 | `018` | `add order price confirmation scope` | `V018__add_order_price_confirmation_scope.sql` | `1587549683` |
| V019 | 2 | `019` | `add exception processing evidence` | `V019__add_exception_processing_evidence.sql` | `-85667777` |

目标数据库为 `shipflow`，目标表为 `flyway_schema_history`，当前最新成功版本必须为 V017，`installed_rank=6`。

## 2. 执行前备份

执行任何 history 写入前，由 DBA 按批准流程备份 `flyway_schema_history`，并保存备份文件。示例命令仅供人工审核，不能把密码写入命令：

```powershell
mysqldump.exe --protocol=TCP --host=<host> --port=<port> --user=<user> --single-transaction --skip-lock-tables --no-create-info shipflow flyway_schema_history > flyway_schema_history_before_v018_v019.sql
```

同时在 Navicat 中先执行 SQL 前半部分的只读快照：

```sql
SELECT DATABASE(), VERSION();
SHOW CREATE TABLE flyway_schema_history;

SELECT installed_rank, version, description, type, script, checksum,
       installed_by, installed_on, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

必须确认：数据库为 `shipflow`；history 表存在；最新成功版本为 V017；V018、V019 尚不存在。

## 3. Navicat 执行顺序

1. 打开 `flyway-history-reconciliation-v018-v019-navicat.sql`，核对目标库、版本号、description、script 和 checksum。
2. 完成执行前备份和只读快照。
3. 执行前置 gate，三个结果必须均为 `PASS`：`database_gate`、`latest_v017_gate`、`target_absence_gate`。
4. 在确认 V018/V019 不存在且当前最新为 V017 后，人工执行事务存储过程部分。
5. 事务内唯一业务表写操作是向 `flyway_schema_history` 插入 V018、V019 两条记录；不会执行 V018/V019 原始迁移正文，也不会修改任何业务表。
6. SQL 不自动执行数据库连接，不包含自动 `COMMIT`。过程成功后保持人工审核边界，由 DBA 按批准 runbook 在同一会话决定提交或回滚。

## 4. checksum 不一致处理

V018 必须为 `1587549683`，V019 必须为 `-85667777`。checksum 必须来自项目原始迁移文件的 Flyway 兼容计算结果，并与人工执行时的原始文件或执行证据一致。

任何 checksum 不一致、缺失、文件内容无法证明一致，均禁止执行补登记。事务过程在写入后的核验中发现 checksum 或元数据不匹配时使用 `SIGNAL SQLSTATE '45000'` 中止，异常处理器执行 `ROLLBACK`；不得修改 checksum，不得重放迁移正文。

## 5. 执行后核验

执行后查询 V018、V019：

```sql
SELECT installed_rank, version, description, type, script, checksum,
       installed_by, installed_on, execution_time, success
FROM flyway_schema_history
WHERE version IN ('018', '019')
ORDER BY installed_rank;
```

再执行 SQL 文件末尾的聚合核验，必须得到：

```text
registered_count = 2
v018_valid_count = 1
v019_valid_count = 1
```

并确认 V018 为 rank 7、V019 为 rank 8，两个 `success=1`，description、script 和 checksum 均准确。

## 6. 回滚方式

- 过程发生异常时，其异常处理器会对当前事务执行 `ROLLBACK` 并继续抛出错误。
- 后置核验不通过时，DBA 不得提交，应在同一会话按批准 runbook 回滚并停止。
- 已经人工提交后发现错误时，先暂停后续 Flyway 活动，取得书面批准，依据备份制定仅针对错误 history 行的回滚方案。
- 回滚只针对 Flyway history 对账，不回退 V018/V019 已存在的业务结构，不删除业务数据，不重放迁移正文。

## 7. 明确边界

- 本文件和 SQL 仅供人工审核；本轮未连接数据库，未执行 SQL。
- 不自动连接数据库，不自动执行 SQL，不自动 `COMMIT`。
- 不重复执行 V018/V019 原始迁移正文。
- 不修改任何业务表。
- 不执行 baseline、repair 或自动 Flyway migrate。
- 不提交、不推送 Git。
