# 物流商轨迹回调学习笔记

## 业务边界

本模块沿用既有 `POST /api/v1/integrations/logistics/{providerCode}/tracking-events` 契约，不创建替代接口。回调只写 `tracking_event`、订单当前状态和 `audit_log`，不修改报价快照、费用、仓库复称或历史金额。

HMAC 签名原文是 `timestamp + "." + rawRequestBody`。时间戳使用 Unix 秒并限制在服务端时钟前后 300 秒。物流商凭证从 `shipflow.tracking.callback.credentials` 外部配置绑定 `providerCode`、平台系统账号 ID 和 Base64 HMAC 密钥；密钥至少 32 字节，不写入仓库、日志或审计。签名通过后，仍实时查询账号、平台角色和 `tracking:callback` 权限。

## 幂等与事务

数据库唯一键 `(provider_id, tracking_no, event_id)` 是最终幂等屏障。处理前按物流商和物流单号锁定出库订单；相同原始报文的重复事件返回 `DUPLICATE`，不插入、不推进状态、不写审计。相同业务键但原始报文不同返回 `TRACK-1003`，且不覆盖首次报文。

新事件、状态推进和首次成功审计在同一事务中执行。非法事件码或非法跳转保存为 `RETRY` 后返回统一 `TRACK-1002`；事务对此业务异常不回滚，以保留人工重试所需的事件时间、接收时间和原始报文。

## 状态映射与乱序

- `PICKED_UP`、`DEPARTED`、`IN_TRANSIT` 映射为 `IN_TRANSIT`。
- `DELIVERED`、`RETURNED`、`LOST` 映射为同名订单状态。
- 当前状态相同或已经处于终态时，新业务事件仍可保存，但不更新订单。
- 只有既有订单状态机允许的路径才推进；例如 `OUTBOUND → IN_TRANSIT`、`IN_TRANSIT → DELIVERED`。
- 迟到的低阶事件不能让 `DELIVERED`、`RETURNED` 或 `LOST` 回退。

## 验证边界

本模块使用单元测试覆盖 HMAC、权限、幂等、乱序和非法映射，使用静态 Mapper XML 测试覆盖租户谓词、唯一业务键和禁止修改财务历史，使用 MockMvc 覆盖 202、401、403 与 422 的统一响应。按本轮约束不连接 MySQL、不执行迁移、数据库集成测试或真实 HTTP 调用。

## 提交前问题

业务问题：生产物流商是否会使用不同于规范事件码的私有码表；如会，应由配置维护映射版本，还是由适配层先归一化？

技术问题：多实例部署时，HMAC 凭证由环境变量、外部密钥文件还是集中密钥管理服务下发，并如何完成无中断轮换？
