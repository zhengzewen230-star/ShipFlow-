# shipflow_http_test 受控初始化脚本

本目录是对既有 `database/schema.sql`、`database/init_data.sql` 和 V002 至 V006 的隔离副本；原文件没有被修改。

仅在以下前提全部满足时，按编号顺序执行 `01` 至 `07`：

- MySQL 客户端连接参数已显式指定 `--database=shipflow_http_test`；
- `SELECT DATABASE()` 返回 `shipflow_http_test`；
- 目标库的基表数为 `0`，且不存在残留业务数据或 `flyway_schema_history`；
- 执行账号拥有在该隔离库建表和写入种子数据的权限；
- 项目负责人已书面批准本目录的初始化脚本。

`01_schema_empty_target.sql` 删除了原始 `CREATE DATABASE` 和 `USE shipflow`，并将全部 `DROP TABLE` 源语句隔离为注释。`03_V002__add_api_support_tables.sql` 以 `IF NOT EXISTS` 保留 V002 来源，同时兼容已由当前 `schema.sql` 建立的同名基线表。`07_V006__add_logistics_master_data_permissions.sql` 只保留权限 DML：当前基线已经有 `transport_mode` 与检查约束，重复执行原始 V006 的 `ALTER TABLE` 会失败。

示例（只在批准后由执行者在其终端运行；不在本轮执行）：

```powershell
$env:MYSQL_PWD = $env:SHIPFLOW_HTTP_TEST_DB_PASSWORD
mysql.exe --protocol=TCP --host=<已核对主机> --port=<已核对端口> --user=<已核对账号> --database=shipflow_http_test --show-warnings < database/http-test-control/01_schema_empty_target.sql
```

每个文件成功后都先执行验证 SQL，再继续下一个文件；不得使用 `--force`。完成后立即清除临时 `MYSQL_PWD`。

`99_RESET_DESTRUCTIVE__APPROVAL_REQUIRED.sql.disabled` 是单独的破坏性重置预案，扩展名和首条 `SIGNAL` 都使它默认不可执行。它不属于初始化顺序，只有得到对该具体文件的明确批准、复核连接目标及备份条件后才可处理。
