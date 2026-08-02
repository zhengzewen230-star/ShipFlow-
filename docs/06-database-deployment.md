# ShipFlow 数据库部署说明

## 1. 部署边界

目标环境为 Ubuntu Linux，Docker 已安装并正常运行。

本项目只将 SQL 导入已有的 MySQL 8.0 容器：

- 容器名称：`my-mysql-docker`
- 容器内部端口：`3306`
- Ubuntu 主机映射端口：`3307`

服务器上另外存在 MySQL 5.7 容器，占用主机 `3306`，以及 Jira 容器。两者均禁止操作和删除。

本说明不会创建新的 MySQL 容器，不创建 `compose.yaml`，不停止、删除或重建任何现有容器，也不执行 `docker system prune`、`docker volume prune` 等清理命令。

## 2. 上传 SQL 文件

在 Ubuntu 项目目录执行：

```bash
docker cp database/schema.sql my-mysql-docker:/tmp/shipflow-schema.sql
docker cp database/init_data.sql my-mysql-docker:/tmp/shipflow-init_data.sql
docker cp database/verify.sql my-mysql-docker:/tmp/shipflow-verify.sql
```

确认文件已上传：

```bash
docker exec my-mysql-docker ls -l \
  /tmp/shipflow-schema.sql \
  /tmp/shipflow-init_data.sql \
  /tmp/shipflow-verify.sql
```

上述命令只复制和读取文件，不会影响其他容器。

## 3. 进入 MySQL 并依次执行

密码必须由用户在终端交互输入，不得写入命令、脚本、文档或 Git。

进入 MySQL 客户端：

```bash
docker exec -it my-mysql-docker mysql -uroot -p
```

在出现 `Enter password:` 后手动输入密码，然后依次执行：

```sql
SOURCE /tmp/shipflow-schema.sql;
SOURCE /tmp/shipflow-init_data.sql;
SOURCE /tmp/shipflow-verify.sql;
```

执行顺序为：

1. `schema.sql`：创建 `shipflow` 数据库、表、外键和索引；
2. `init_data.sql`：写入测试租户、用户、角色、权限、渠道和价格规则；
3. `verify.sql`：只读检查表数量、初始化数据、权限、渠道、价格规则、字符集和排序规则。

也可以在 Ubuntu 主机上逐个执行，每条命令都会交互提示密码：

```bash
docker exec -i my-mysql-docker mysql -uroot -p < database/schema.sql
docker exec -i my-mysql-docker mysql -uroot -p shipflow < database/init_data.sql
docker exec -i my-mysql-docker mysql -uroot -p shipflow < database/verify.sql
```

不要把密码拼接到命令中。

## 4. 验证内容和预期数量

`database/verify.sql` 会为每项检查输出实际值、期望值和 `PASS`/`FAIL`：

- 数据库字符集是否为 `utf8mb4`；
- 28 张表是否统一使用 `utf8mb4_0900_ai_ci`；
- 用户数量：10；
- 角色数量：10；
- 权限数量：10；
- 店铺数量：4；
- 物流商数量：2；
- 物流渠道数量：4；
- 渠道服务国家数量：7；
- 价格规则数量：5；
- 价格阶梯数量：10；
- 所有 ACTIVE 渠道是否存在有效价格规则；
- 每个财务角色是否拥有账单导入、费用对账和审计权限；
- Mock 物流系统账号是否拥有 `tracking:callback`；
- 关键唯一索引是否存在；
- 订单状态是否属于已确认状态集合。

## 5. 端口说明

从 Ubuntu 主机上的其他程序连接该 MySQL 时使用：

```text
host: 127.0.0.1
port: 3307
database: shipflow
```

本文推荐使用 `docker exec`，直接进入现有 MySQL 8.0 容器，不访问主机 `3306`，不会影响 MySQL 5.7 容器或 Jira 容器。

## 6. 安全和执行限制

- `init_data.sql` 中的 BCrypt 值仅用于测试环境。
- 密码只能由用户在终端交互输入。
- 不执行远程部署。
- 不停止、删除、重建任何现有容器。
- 不执行 Docker 清理命令。
- 已在 Ubuntu Linux 的 MySQL 8.0 容器 `my-mysql-docker` 中实际执行并验证通过。实际执行结果记录于 [07-database-verification-report.md](07-database-verification-report.md)。
