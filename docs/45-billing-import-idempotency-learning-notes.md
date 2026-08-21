# 账单导入组合幂等模块学习笔记

更新时间：2026-08-17（Asia/Shanghai）

## 1. 本次模块

本次只实现账单 CSV 导入的重复判定契约。账单内容使用文件字节的 SHA-256；请求同时必须携带 `Idempotency-Key`。文件名不参与重复判断。

## 2. 业务规则

- 同一租户、同一导入操作、同一幂等键只能绑定同一 `providerId + fileHash`。
- 同一幂等键再次提交相同物流商和相同文件内容时，已完成请求重放原账单批次；处理中请求返回 `COMMON-1010`；不同内容或物流商返回 `COMMON-1009`。
- 更换幂等键但提交相同租户、物流商和文件内容时，由 `bill_import_batch` 的 `(tenant_id, provider_id, file_hash)` 唯一约束阻止第二个批次，并将新幂等键绑定到已存在批次。
- 幂等记录和账单批次创建、明细导入、批次完成在同一事务中执行；数据库时间按 UTC 存储。

## 3. 实现位置

- `BillingController` 将真实 `Idempotency-Key` 传入应用服务，不再丢弃请求头。
- `BillingApplicationService` 生成规范请求摘要，执行幂等记录抢占、重放、冲突和完成。
- `BillingMapper`/XML 复用通用 `api_idempotency_record`，没有新增表或未经确认的迁移。
- OpenAPI 明确组合规则、文件名不参与判断和 UTC 时间契约；前端 service 使用统一 `writeConfig` 生成请求头。

## 4. 验证与边界

- 账单后端定向测试 `21` 个通过；覆盖成功导入、自动零差异、行级重复、文件哈希重复、同键不同内容和处理中冲突。
- 前端账单 service 测试覆盖导入请求的 `Idempotency-Key` 和 `X-Request-ID`。
- 未连接数据库，未执行迁移，未导入生产账单，未执行生产调用，未提交或推送 Git。

## 5. 后续模块

- 异常处理记录、责任方、证据附件和索赔关联仍需单独模块实现。
- 运营概览按 `Asia/Shanghai` 自然日统计和全页面时间展示统一仍需单独模块实现。
