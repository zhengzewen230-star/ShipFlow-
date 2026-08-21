# V018/V019 Flyway 历史最终人工审核包

状态：仅生成材料，未连接生产库执行。适用数据库：`shipflow`，MySQL `8.0.46`。

## 1. 目标与硬性边界

V018、V019 已由负责人通过 Navicat 人工执行，生产库结构已经存在，但 `flyway_schema_history` 缺少两条历史记录。本包只登记这两个已发生版本，不重复执行迁移正文。

禁止事项：

- checksum、description、版本顺序任一不一致时禁止执行；不得通过修改 checksum 强行登记。
- 不执行 V018/V019 迁移正文，不执行 baseline、repair 或自动 Flyway migrate。
- 不修改任何业务表，不删除业务数据。
- 本轮不连接生产库，不执行 SQL，不执行 `COMMIT`、`ROLLBACK`。
- 不提交、不推送 Git。

## 2. 迁移元数据

Flyway 依赖版本为 `11.7.2`。checksum 使用 Flyway 兼容规则计算：UTF-8、去除 BOM、逐行读取并去除换行后计算 CRC-32，写入 history 的 `checksum INT` 为 signed Java `int`。

| 执行顺序 | installed_rank | version | description | type | script | checksum（signed INT） | CRC-32 unsigned |
| ---: | ---: | --- | --- | --- | --- | ---: | ---: |
| 1 | 7 | `018` | `add order price confirmation scope` | `SQL` | `V018__add_order_price_confirmation_scope.sql` | `1587549683` | `1587549683` |
| 2 | 8 | `019` | `add exception processing evidence` | `SQL` | `V019__add_exception_processing_evidence.sql` | `-85667777` | `4209299519` |

当前工作区原始迁移文件的 checksum 已与生产库已登记的 V013-V017 进行算法交叉验证。由于 V018/V019 是人工执行，最终执行前仍必须用人工执行时留存的原始文件、Navicat 执行记录或 DBA 证据再次确认；只要证据中的 checksum 不等于本表，立即停止。

## 3. 当前生产 history 表结构

只读核验确认表名为 `flyway_schema_history`，字段如下：

| 顺序 | 字段 | 类型 | 可空 | 默认值 |
| ---: | --- | --- | --- | --- |
| 1 | `installed_rank` | `INT` | NO | 无 |
| 2 | `version` | `VARCHAR(50)` | YES | `NULL` |
| 3 | `description` | `VARCHAR(200)` | NO | 无 |
| 4 | `type` | `VARCHAR(20)` | NO | 无 |
| 5 | `script` | `VARCHAR(1000)` | NO | 无 |
| 6 | `checksum` | `INT` | YES | `NULL` |
| 7 | `installed_by` | `VARCHAR(100)` | NO | 无 |
| 8 | `installed_on` | `TIMESTAMP` | NO | `CURRENT_TIMESTAMP` |
| 9 | `execution_time` | `INT` | NO | 无 |
| 10 | `success` | `TINYINT(1)` | NO | 无 |

当前只读状态：最大 `installed_rank=6`，最新成功版本为 `017`，V018/V019 不存在。

## 4. 执行前备份与快照

执行补登记前，DBA 必须先完成已批准的生产备份，并把输出保存到受控位置。下面是人工审核后执行的命令示例，路径、主机和账号必须由 DBA 填入，不能把密码写入命令：

```powershell
mysqldump.exe --protocol=TCP --host=<host> --port=<port> --user=<user> --single-transaction --skip-lock-tables --no-create-info shipflow flyway_schema_history > flyway_schema_history_before_v018_v019.sql
```

该命令未由本轮执行。SQL 文件中的第一部分同时提供以下只读快照：

- `SELECT DATABASE(), VERSION()`
- `SHOW CREATE TABLE flyway_schema_history`
- history 全量行快照
- `information_schema.columns` 字段快照
- V018/V019 目标登记常量

不能用生产库内创建备份表替代备份流程。

## 5. 事务 SQL 说明

主 SQL 文件为 [`flyway-history-reconciliation-v018-v019.sql`](flyway-history-reconciliation-v018-v019.sql)。其人工审核顺序如下：

1. 执行前备份和只读快照。
2. 检查 `DATABASE()='shipflow'`。
3. 检查 V018/V019 版本不存在；任何已有行都停止，不能重复登记或 repair。
4. 检查 `MAX(installed_rank)=6`，且 rank 6 为成功的 V017。
5. 对照 version、description、script、signed checksum；checksum 不一致时禁止执行。
6. 在同一审核会话中创建临时管理过程并调用。过程启动事务，只插入两条 `flyway_schema_history` 记录，不执行迁移正文。
7. 过程检查 `ROW_COUNT()=2` 和两条完整元数据；不满足时 `SIGNAL`，异常处理器执行 `ROLLBACK` 并重新抛出异常。
8. 成功后查询 V018/V019，必须得到两个正确版本、rank、description、script、checksum 和 `success=1`。
9. DBA 人工确认结果后，才可在同一会话显式 `COMMIT`；结果不符合要求时显式 `ROLLBACK`。
10. 事务决定后删除临时管理过程，并再次执行只读核验。

SQL 文件不自动提交、不自动回滚；本轮不会执行其中任何语句。MySQL 顶层不能直接使用 `SIGNAL`，因此 SQL 使用临时存储过程承载异常闸门。创建/删除该临时过程属于管理 DDL，必须由 DBA 审核账号权限和清理步骤；它不触碰业务表。

## 6. 执行后核验

SQL 文件包含执行后查询，最低核验条件为：

```text
registered_count = 2
v018_valid_count = 1
v019_valid_count = 1
```

随后应再次只读查询：

```sql
SELECT installed_rank, version, description, type, script, checksum,
       installed_by, installed_on, execution_time, success
FROM flyway_schema_history
WHERE version IN ('018', '019')
ORDER BY installed_rank;
```

应确认 rank 7/8 连续、V018 在 V019 之前、checksum 与本包一致、`success=1`，并确认业务表结构未因本包发生变化。后续 Flyway `validate` 只能在独立审批后评估，不能在本包中自动开启生产 Flyway。

## 7. 回滚说明

本轮不自动执行回滚。若人工提交后发现登记值错误：

1. 立即暂停后续 Flyway 活动。
2. 取得书面批准，确认备份可恢复。
3. 仅针对精确匹配的 V018/V019 history 行制定 history-only 回滚方案。
4. 不执行 V018/V019 的 `DROP`/`ALTER` 逆向操作，不删除业务数据。
5. 回滚后重新执行 history 和业务结构只读核验。

回滚不是本包的自动步骤，也不应通过 `repair` 掩盖 checksum 或执行证据不一致。

## 8. 待确认项

- 负责人/DBA 确认人工执行时的原始脚本与本包 checksum 一致。
- 确认生产备份完成并可恢复。
- 确认执行账号可写 `flyway_schema_history`，且不会误写业务表。
- 确认采用 `installed_rank=7/8` 与当前 V017 前置状态。
- 确认临时管理过程的创建、调用、人工提交/回滚和删除步骤。

