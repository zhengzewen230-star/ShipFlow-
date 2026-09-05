# V022 异常与索赔运行时迁移学习笔记

## 迁移目标

异常详情 Mapper 使用 `exception_case.assigned_to_user_id` 作为正式当前负责人字段；因此运行应用前，目标库必须已安装 V022。不能再从审计 JSON 推断当前负责人。

## 2026-08-24 受控执行记录

- 执行前以 `SELECT DATABASE()` 确认目标为 `shipflow`，Flyway 当前版本为 V021。
- V022 通过 Flyway 11.7.2 的 `filesystem:database/migrations` 受控扫描路径执行，结果为 V022 成功。
- 迁移只包含 `ALTER TABLE`、索引/外键/检查约束和 `claim_evidence_reference` 建表；不包含 `INSERT`、`UPDATE`、`DELETE`、`REPLACE` 或 Flyway history 修复。
- 执行前后 `exception_case`、`exception_evidence_attachment`、`claim_record` 均为 0 行；`audit_log` 保持 208 行。迁移没有更新或删除历史业务数据。

## 验证与回滚边界

- 已验证 `assigned_to_user_id`、证据 `description`、索赔审核/财务字段、4 个异常筛选索引及关联外键/唯一约束存在。
- 回滚仅允许在批准的维护窗口按依赖反向删除 V022 新增的表、外键、索引、检查约束和列；不得删除或改写 `flyway_schema_history`，也不得在存在依赖业务数据时执行结构回滚。
- 本地运行时仍要求提供 JWT 私钥和公钥位置；缺失时必须保持启动失败，不能以关闭 JWT 的方式绕过认证。
