# 商家业务员工作台第四阶段：店铺管理企业级优化

更新时间：2026-08-18（Asia/Shanghai）

## 1. 阶段边界

本阶段只完善店铺管理真实 API、权限边界和企业级交互，不改变认证、租户隔离、订单、报价、物流、异常、账单和对账既有能力。不执行数据库写入、Flyway、顺丰生产调用或 Git 提交。

店铺读取流程保持为：认证 → `scope:TENANT` 与 `store:read` → 后端校验 `tenant_id`、`user_id`、角色和 `active sys_user_store_scope` → 资源归属查询。商家业务员只能看到授权店铺；未授权资源继续统一返回 404。

## 2. P4-01 列表契约

### 2.1 已实现

`GET /api/v1/stores` 现在支持：

- `page`、`pageSize`；
- `storeCode` 模糊查询；
- `storeName` 模糊查询；
- `platformCode` 精确筛选；
- `status`：`ACTIVE`、`DISABLED`；
- `sortBy` 白名单：`storeCode`、`storeName`、`platformCode`、`status`、`updatedAt`；
- `sortDirection`：`ASC`、`DESC`。

排序字段由 Service 白名单校验后再由 Mapper XML 的 `choose` 分支映射，不能把请求参数直接拼接到 SQL。列表和数量查询都复用 `pageForUser`、`countForUser`，没有恢复 tenant-only 查询。

前端店铺页已接入真实 `GET /stores`：

- 编码、名称、平台和状态筛选；
- 分页和排序；
- URL query 白名单解析；
- 刷新后恢复筛选条件；
- 加载、空数据、失败、Trace ID 和重试状态。

### 2.2 明确未纳入

国家/地区、默认发货地址、默认物流渠道、详情抽屉、历史订单数量和审计摘要需要额外后端真实字段或聚合契约，本子阶段不使用前端假数据代替。

## 3. 权限矩阵基线

| 操作 | 前端入口 | 后端要求 | 资源边界 |
|---|---|---|---|
| 列表/详情 | `store:read` | `scope:TENANT + store:read` | `tenant_id + user_id + role + active store scope` |
| 创建/编辑/启停 | `store:manage` | `scope:TENANT + store:manage` | 当前租户和资源归属 |
| 商家业务员 | 店铺列表 | 后端重新校验 scope | 仅授权店铺 |
| 租户级角色 | 店铺列表 | 沿用现有 SQL 角色分支 | 本租户范围，待业务确认是否全部保留 |

`FINANCE_OPERATOR`、`WAREHOUSE_OPERATOR`、`CUSTOMER_SERVICE_OPERATOR` 的租户级全量可见性沿用现有实现，本阶段没有擅自修改权限数据或 SQL 角色集合。

## 4. 已发现的后续缺口

- OpenAPI 原店铺列表声明为数组，实际后端返回分页对象；P4-01 已改为 `ApiSuccessStorePage`。
- 编辑接口 OpenAPI 声明 `Idempotency-Key`，当前 Controller 尚未实际使用；留待维护子阶段。
- 启停接口尚无幂等键；留待维护子阶段。
- 停用店铺后，订单创建服务还需要重新校验报价所属店铺为 `ACTIVE`；留待订单规则子阶段，并需补测试。
- 当前数据库没有店铺默认地址、默认渠道和国家/地区字段或关联表；不得直接执行迁移。

## 5. 本轮验证

- 前端单元测试：68/68 通过；
- 前端生产构建：通过；
- 后端 JDK 21.0.11 店铺定向测试：14/14 通过；
- OpenAPI：106 个唯一 `operationId`，重复 0；店铺列表新增筛选、分页、排序参数及分页响应 schema；
- `git diff --check`：通过；
- 浏览器：BLOCKED。当前受控浏览器无登录态，访问 `/app/stores` 被导向登录页，未填写或读取凭证。

## 6. 阶段结论

P4-01 代码和自动化验证通过，真实浏览器验收因登录环境不可用保留 BLOCKED。下一步应在可复用的已授权浏览器会话中复核店铺筛选、分页、排序、刷新和返回；之后再进入 P4-02 店铺详情只读契约。

## 7. P4-02 店铺详情只读能力（已完成）

详情接口 `GET /api/v1/stores/{storeId}` 继续使用 `tenant_id + user_id + role + active sys_user_store_scope` 的后端可见性查询；未授权店铺在聚合历史订单和审计摘要前统一返回 `COMMON-1006/404`。详情响应不再返回原始 `platformAccount`，仅返回服务端生成的 `platformAccountMasked`。

本阶段复用现有 `shipment_order.tenant_id/store_id` 统计历史订单数量，复用 `audit_log` 返回最近 10 条店铺审计摘要（操作类型、结果、UTC 发生时间），不返回审计明细、请求体或敏感字段。国家/地区、默认发货地址、默认物流渠道在当前数据库没有字段或可靠关联，本阶段以 `null + unavailableFields` 明确标记“后端暂未提供”，不使用前端假数据。

前端新增受保护详情路由和详情入口，真实读取详情 API，提供加载、空数据、失败/重试、Trace ID、Asia/Shanghai 时间展示；返回列表时保留原筛选 query。详情资源 ID 采用正整数白名单解析，非法参数不发起请求。

P4-02 验证：后端 JDK 21.0.11 店铺定向测试 17/17，前端单元测试 72/72，前端构建通过，OpenAPI 店铺详情 schema 已更新，未执行数据库写入、Flyway、顺丰生产调用或 Git 提交。真实浏览器若无授权登录态仍标记 BLOCKED，不以静态 HTTP 结果代替。

## 8. P4-03 店铺维护、幂等与停用下单规则（已完成）

店铺编辑和启停均要求 `Idempotency-Key`，请求哈希绑定请求体；同键同请求重放原结果，同键不同请求体返回 409。数据库版本号用于条件更新，避免并发覆盖。两个写接口先执行当前调用人的店铺资源归属校验，再进入幂等和写入流程，继续保留 `tenant_id + user_id + role + active sys_user_store_scope` 边界。

订单创建前复核报价所属店铺仍为 `ACTIVE`；停用店铺返回 `STORE-1002/422` 且不创建新订单，历史订单查询链路不变。前端仅对 `store:manage` 显示编辑/启停，启停二次确认，编辑不回显平台账号，处理中禁用重复提交并展示真实错误 Trace ID。

P4-03 验证：JDK 21.0.11 后端定向测试 39/39，前端单元测试 74/74，前端构建通过，OpenAPI 静态检查 106/106，`git diff --check` 通过。真实浏览器编辑/启停未执行，未执行数据库写入、Flyway、顺丰生产调用和 Git 提交。

## 9. P4-04 店铺资源模型设计与迁移方案（设计完成，暂缓实现）

已完成现有 `merchant_store`、`shipment_address`、`logistics_channel`、服务国家、订单/报价关联、`audit_log`、迁移 V002-V019 和订单创建逻辑的只读核对。结论是：店铺没有国家/地区、默认地址和默认渠道结构；订单地址是不可回写的历史快照；平台渠道不能改成租户数据。

推荐新增 `merchant_store_address` 和 `merchant_store_channel` 两张最小关联表，并使用 `tenant_id + store_id` 复合资源约束、默认值唯一约束、版本号、启用状态、操作人和 UTC 审计字段。详情扩展、默认地址读写、店铺渠道读写的 API、权限矩阵、迁移、回滚和测试方案详见 [P4-04 资源模型与迁移方案](07-merchant-store-management-p404-resource-model-and-migration-plan.md)。

当前 BLOCKED：业务字段口径、平台管理员边界、回填来源和 DBA 迁移授权尚未确认。待确认项包括默认地址/渠道唯一性、业务员和租户管理员维护权限、停用行为及历史配置版本策略。本阶段未创建 Flyway、未写数据库、未修改生产 Controller/Service/Mapper/XML，现有 `StoreMapper.xml` 权限过滤保持不变。

### 9.1 P4-04 迁移前门禁结论

- 业务确认清单已写入 P4-04 设计文档：默认地址/渠道数量、商家业务员与租户管理员读写边界、平台管理员边界、停用行为和历史配置版本。
- 迁移仅保留计划：结构迁移拟使用待 DBA 确认的版本号；回填迁移仅在存在正式来源且单独获批时制定；当前未创建迁移文件。
- 当前迁移目录最高为 V019；未执行 Flyway、未连接数据库、未写入业务或权限数据。
- 进入 P4-05 前必须取得业务规则确认、DBA 外键/唯一索引/版本方案确认、正式回填来源确认和明确 Flyway 执行授权。
## P4-05 实施补充（2026-08-19）

P4-05 已完成 V020 结构迁移和最小后端资源契约实现：目标库为 `shipflow`，迁移前 V019、迁移后 V020，V021 因无可靠批准来源而跳过，资源表保持 0 行。当前结论以 `07-merchant-store-management-phase4-p405-acceptance.md` 为准；不得把未配置资源显示为默认地址或默认渠道。

## 主数据对账阻塞（2026-08-19）

最新只读对账确认租户、平台编码、仓库主数据和地区关系均存在差异。P4-05/P4-06 继续保持 `BLOCKED`，不执行 V021、不创建缺失表、不修正租户或平台编码。
## P4-05/P4-06 对账确认版（2026-08-19）

自动匹配 0，本轮业务数据写入 0；`STORE_JP_001` 归属不唯一，`STORE_EU_001` 保持 `TENANT_DEMO_002`，平台映射未批准，`warehouse` 和 `region_carrier_relation` 不存在。后续拆分为两个独立结构迁移及仅承担纯数据回填的 V021。当前不创建 SQL、不执行 Flyway、不修改数据库或业务代码，阶段继续 `BLOCKED`。
## P4-06 现有契约收口（2026-08-19）

P4-06 已在现有真实 Schema、接口和数据范围内完成：店铺与物流资料只读展示、详情不可用字段提示、订单/报价/轨迹/异常/账单页面状态、工作台跳转和 query 回显均完成复核。店铺权限继续使用 tenant_id + user_id + role + active sys_user_store_scope，未授权资源和仓库权限边界未放宽。

V021/V022、warehouse、merchant store 默认地址、店铺渠道绑定和地区关系仍延期；本阶段数据库写入 0。后续恢复条件是业务主数据、DBA Schema/回滚方案和测试库执行权限全部确认。

验收状态：P4-06 现有契约范围 PASS；数据库资源迁移 DEFERRED/BLOCKED；401、网络失败、refresh 清理、Cookie 面板等环境受限场景继续 BLOCKED。
