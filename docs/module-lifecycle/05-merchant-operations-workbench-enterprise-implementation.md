# 商家业务员工作台企业化改造：第二阶段设计与实施契约

更新时间：2026-08-17（Asia/Shanghai）  
实施阶段：第二阶段“统一业务状态和数据契约”  
本轮实施模块：订单费用确认与状态契约

## 1. 本轮业务流程

仓库复称后计算出新的当前费用。若费用上涨，订单进入 `PENDING_PRICE_CONFIRMATION`，并生成一条未确认的 `fee_adjustment`。

商家业务员只能在自己被授权的店铺范围内提交“费用确认申请”，申请只改变费用调整记录的申请状态并写审计，不改变订单状态。财务人员最终确认，租户管理员作为备用审批人；服务端重新校验租户、店铺、角色、费用调整归属、期望金额和订单版本。确认成功后，在同一事务中写入 `confirmed_fee`、确认人和确认时间，并将订单状态原子地推进到 `READY_FOR_OUTBOUND`。

失败路径包括：跨租户或跨店铺资源统一按资源不存在处理；非申请人角色调用申请接口返回 403；业务员调用最终确认接口由 RBAC 拒绝；订单状态、调整类型、金额或版本不匹配返回 409/422；相同幂等键重复请求重放原结果，不同请求体复用幂等键返回 409。

## 2. 本轮范围与不变边界

本轮只实现订单费用确认模块及其必要的店铺授权边界：

- 新增费用确认申请、费用确认查询和财务最终确认接口。
- 复用 `shipment_order.confirmed_fee`、`fee_adjustment`、`audit_log` 和 `api_idempotency_record`。
- 为费用调整补充申请状态、申请人和申请时间；为商家业务员补充最小店铺授权关系。
- 修正角色权限矩阵，使业务员不能持有 `order:price-confirm`，财务人员和租户管理员可以最终确认。
- 订单、报价、仓库、物流、异常、账单页面的其他缺口不在本轮实现。
- 不执行数据库迁移，不连接生产数据库，不调用顺丰生产接口，不提交或推送 Git。

## 3. 状态契约

| 对象 | 当前状态 | 操作 | 下一状态 | 执行角色 | 规则 |
|---|---|---|---|---|---|
| 订单 | `PENDING_PRICE_CONFIRMATION` | 提交费用确认申请 | 订单状态不变 | 商家业务员 | 必须拥有该 `store_id` 的授权；只改变费用调整申请状态 |
| 费用调整 | `PENDING_CONFIRMATION` | 提交费用确认申请 | `REQUESTED` | 商家业务员 | 只允许 `INCREASE`；金额必须等于订单当前费用调整后的金额 |
| 订单 | `PENDING_PRICE_CONFIRMATION` | 最终确认费用 | `READY_FOR_OUTBOUND` | 财务人员/租户管理员 | `expectedFee = after_amount`，版本乐观锁，事务内更新订单和调整记录 |
| 费用调整 | `PENDING_CONFIRMATION`/`REQUESTED` | 最终确认费用 | `CONFIRMED` | 财务人员/租户管理员 | 只允许一次确认；保存 `confirmed_by`、`confirmed_at` |

`LABEL_READY` 是物流商面单/贴标状态的唯一业务契约。本轮不把它混入订单状态机，也不新增第二个贴标状态。

## 4. 权限矩阵与店铺资源边界

| 角色 | 权限 | 费用申请 | 最终确认 | 店铺范围 |
|---|---|---:|---:|---|
| `MERCHANT_OPERATOR` 商家业务员 | `order:price-request` | 允许 | 禁止 | 仅 `sys_user_store_scope` 中的启用店铺 |
| `FINANCE_OPERATOR` 财务人员 | `order:price-confirm` | 不需要 | 允许 | 本租户启用店铺，仍校验订单 `tenant_id + store_id` 归属 |
| `MERCHANT_ADMIN` 租户管理员 | `order:price-confirm` | 可作为业务补充 | 允许 | 本租户全部启用店铺 |

前端菜单和按钮只负责体验，后端 `SecurityConfig`、应用服务和 Mapper SQL 都必须保留 `scope:TENANT`、`tenant_id`、`store_id`、角色/权限和资源归属检查。业务员没有授权店铺时，订单查询和费用申请不得通过 URL 或订单 ID 绕过。

## 5. 数据库来源与最小结构

现有结构已确认可复用：

- `shipment_order`：`tenant_id`、`store_id`、`current_status`、`current_fee`、`confirmed_fee`、`version`。
- `fee_adjustment`：费用前后金额、币种、调整类型、订单和复称关联、确认人和确认时间。
- `audit_log`：追加式操作审计，时间按 UTC 保存。
- `api_idempotency_record`：按租户、操作、幂等键和请求哈希记录重复请求。

确实缺失且本轮最小新增：

- `fee_adjustment.confirmation_status`：`PENDING_CONFIRMATION`、`REQUESTED`、`CONFIRMED`。
- `fee_adjustment.requested_by`、`fee_adjustment.requested_at`。
- `sys_user_store_scope`：`tenant_id`、`user_id`、`store_id`、启停状态、唯一约束和租户/用户/店铺外键。

`schema.sql` 和连续 Flyway 迁移保持同一结构语义；本轮只写入文件并做静态/单元验证，不执行迁移。

## 6. API 契约

时间字段统一使用 UTC ISO-8601；前端转换为 `Asia/Shanghai` 展示。金额使用 `BigDecimal`/JSON decimal，客户端只能提交 `expectedFee` 供服务端比对，不能决定最终费用。

| 方法 | 路径 | 权限 | 幂等 | 成功结果 |
|---|---|---|---|---|
| `GET` | `/api/v1/orders/{orderId}/price-confirmation` | 租户订单读取权限 | 无 | 返回订单、费用调整、申请状态和版本 |
| `POST` | `/api/v1/orders/{orderId}/price-confirmation-requests` | `scope:TENANT + order:price-request` | `Idempotency-Key` + 请求哈希 | 返回申请后的费用确认视图，订单仍为 `PENDING_PRICE_CONFIRMATION` |
| `POST` | `/api/v1/orders/{orderId}/price-confirmation` | `scope:TENANT + order:price-confirm` | `Idempotency-Key` + 请求哈希 | 返回 `READY_FOR_OUTBOUND` 和确认费用 |

最终确认必须校验：调整记录属于当前租户和订单、类型为 `INCREASE`、金额等于 `after_amount`、未确认、订单当前状态为 `PENDING_PRICE_CONFIRMATION`、请求版本匹配。订单和调整记录任一更新失败即回滚。

## 7. 错误与审计契约

- `COMMON-1004`：调用者缺少租户/店铺/角色边界；RBAC 层优先返回 403。
- `COMMON-1006`：订单、店铺或费用调整不属于当前租户/授权店铺，统一返回 404。
- `COMMON-1005`：订单版本已变化，返回 409。
- `ORDER-1010`：订单不在费用确认状态或调整状态不允许操作，返回 409。
- `ORDER-1011`：期望费用与服务端费用调整不一致，返回 422。
- `COMMON-1009`：同一幂等键对应不同请求体，返回 409。
- `COMMON-1010`：相同幂等键仍在处理中，返回 409。

申请、确认、拒绝和失败均写入 `audit_log`，只记录租户、操作者、订单、请求 ID、结果和必要业务原因，不记录密码、Token、Cookie、私钥或完整敏感请求体。

## 8. 实施结果与验证

### 8.1 已完成实现

- 已新增费用确认查询、商家业务员申请和财务/租户管理员最终确认的后端 Controller、应用服务、Mapper/XML 和前端 service/工作台入口。
- 已在订单查询、费用确认查询和写操作中校验 `tenant_id`、`store_id`、角色/权限、资源归属和启用的 `sys_user_store_scope`；业务员只能访问授权店铺，租户管理员覆盖本租户店铺。
- 已通过 `Idempotency-Key` 与请求体哈希处理重复请求；相同请求重放原结果，同一幂等键对应不同请求体返回冲突。
- 已使用订单版本乐观锁和事务条件更新，确保订单推进到 `READY_FOR_OUTBOUND` 与费用调整进入 `CONFIRMED` 要么同时成功，要么回滚。
- 已将 `LABEL_READY` 保持为物流商面单/贴标状态，不增加或混入订单状态机。
- 已完成账单导入组合幂等：按租户+操作+`Idempotency-Key` 绑定 `providerId + 文件内容 SHA-256` 请求摘要，同时保留批次表的租户+物流商+文件哈希唯一约束；文件名不参与重复判断。

### 8.2 已执行验证

- 后端 Maven 全量测试：使用 JDK `21.0.11`，`369` 个测试通过，`0` 失败，`0` 错误，`BUILD SUCCESS`。
- 费用确认后端定向测试：`9/9` 通过，覆盖申请、最终确认、角色拒绝、租户/店铺隔离、金额校验、版本冲突和 Mapper/XML 契约。
- 前端 `npm run test:unit`：`12` 个测试文件、`42` 个测试通过。
- 前端 `npm run build`：`vue-tsc -b` 和 Vite 生产构建通过。
- OpenAPI 静态结构检查：`85` 个 paths、`100` 个 HTTP 方法、`76` 个 operationId，未发现重复 operationId；费用申请和最终确认路径已与 Controller、SecurityConfig、前端 service 对齐。
- `git diff --check`：通过。
- 敏感信息静态扫描：未发现私钥头、常见云厂商访问键或 `sk-/pk-` 形式 provider key；该结果不替代运行时凭证检查。
- 账单模块定向后端测试：`21` 个测试通过，覆盖组合幂等重放、同键不同内容冲突、处理中冲突、文件哈希重复、行级重复和 Controller 权限契约；前端账单 service 已补导入幂等请求头测试。

### 8.3 验证边界

- 当前环境没有可用的本地 YAML 解析器、`swagger-parser` 或 `swagger-cli`，因此 OpenAPI 只声明通过结构扫描，不把完整 YAML 解析校验写成已通过。
- `V018__add_order_price_confirmation_scope.sql` 已设计并写入工作区，但未执行 Flyway/数据库迁移；未连接生产数据库，因此没有真实数据库断言或租户数据验收结果。
- 未调用顺丰生产接口，未执行生产数据写入，未创建 Git commit 或推送。
- 异常处理记录/证据附件/索赔关联、全工作台统一 `Asia/Shanghai` “今日”统计仍属于后续模块，不在本轮实现。

## 9. 后续模块（不在本轮实现）

- 商家业务员店铺授权管理页面和完整订单/报价查询按授权店铺过滤。
- 异常处理记录、证据附件和索赔关联的最小数据模型。
- 运营概览按 `Asia/Shanghai` 自然日统计以及所有页面时间展示统一化。

## 10. 本轮实现：异常处理、证据和索赔关联

### 10.1 业务流程

1. 创建或查询异常时，服务端从订单读取所属店铺，并校验 `tenant_id + store_id + 当前用户角色 + 店铺授权`；URL 中的异常 ID 不能绕过资源归属。
2. 分派异常时写入 `responsible_party` 结构化责任方，状态由 `OPEN` 进入 `PROCESSING`；之后的处理记录只追加，不覆盖历史事实。
3. 处理记录可以登记商家、物流商、海关、客户或其他责任方的处理动作；物流商等待期间进入 `WAITING_PROVIDER_FEEDBACK`，反馈后回到 `PROCESSING`，处理完成进入 `RESOLVED`。
4. 证据上传计算内容 SHA-256，保存附件元数据和内容本体。同一租户、同一异常、相同内容哈希不重复新增；不同文件名但内容相同仍视为重复。列表和下载接口再次校验租户及店铺资源归属。
5. `claim_record.exception_case_id` 继续关联索赔。索赔创建、提交、审核和关闭沿用现有状态机，异常进入 `PENDING_FINANCE_CONFIRMATION` 时等待财务处理；不复制索赔表或另建关联表。

### 10.2 最小数据结构

- `exception_case.responsible_party`：结构化责任方，允许 `MERCHANT`、`PROVIDER`、`CUSTOMS`、`CUSTOMER`、`OTHER`。
- `exception_handling_record`：`tenant_id`、`exception_case_id`、`handled_by_user_id`、`record_type`、`content`、`created_at`，通过幂等键避免重复写入。
- `exception_evidence_attachment`：`tenant_id`、`exception_case_id`、上传人、文件名、媒体类型、字节数、SHA-256、`content_blob`、UTC 时间；唯一键为租户、异常和内容哈希。

### 10.3 API 契约和时间约定

- `POST /api/v1/exceptions/{exceptionId}/handling-records`：追加处理记录，要求 `Idempotency-Key`。
- `GET /api/v1/exceptions/{exceptionId}/handling-records`：按创建时间倒序返回处理记录。
- `POST /api/v1/exceptions/{exceptionId}/evidence`：multipart 上传证据，要求 `Idempotency-Key`，按内容哈希去重。
- `GET /api/v1/exceptions/{exceptionId}/evidence`：返回附件元数据。
- `GET /api/v1/exceptions/{exceptionId}/evidence/{attachmentId}/content`：下载附件内容。

所有 API 时间字段为 UTC ISO-8601；前端将其转换为 `Asia/Shanghai`。数据库迁移文件只写入工作区，必须由负责人另行确认后执行。

### 10.4 异常路径

- 跨租户、跨店铺、无授权店铺或资源不存在统一返回 404；缺少异常管理权限返回 403。
- 同一幂等键对应不同请求体返回 409；同一幂等键仍处理中返回 409；附件内容哈希重复返回原附件元数据。
- 状态版本不匹配、异常已关闭或状态跳转非法返回 409；空文件、超限文件、非法责任方或非法记录类型返回 400/422。
- 附件内容不写入日志和审计明细，只审计附件 ID、哈希和文件大小等非敏感元数据。
