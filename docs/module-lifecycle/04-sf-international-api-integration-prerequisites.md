# 顺丰国际接口接入前置条件

## 当前结论

截至 2026-08-15，仓库中未发现顺丰国际接口的可复用 HTTP 客户端、签名工具、配置类、测试报文或历史实现。代码也未发现 `partnerID`、`checkWord`、客户编码、月结账号或顺丰专用持久化字段。

因此当前不实现、不调用、不声称联调以下服务：

| serviceCode | 用途 | 当前状态 |
| --- | --- | --- |
| `COM_RECE_IUOP_CREATE_ORDER` | 创建国际物流订单 | 缺少官方请求/响应契约与沙箱凭据 |
| `COM_RECE_IUOP_PRINT_ORDER` | 获取发货面单 | 缺少面单响应格式与保存策略 |
| `COM_RECE_IUOP_QUERY_ORDER` | 查询物流订单 | 缺少官方响应字段和状态映射 |
| `COM_RECE_IUOP_CANCEL_ORDER` | 取消物流订单 | 缺少取消条件、幂等和响应契约 |
| `COM_RECE_IUOP_UPLOAD_CERTIFY` | 上传清关资料 | 缺少资料格式、大小限制和审计要求 |

## 受控配置变量

只允许通过受控密钥配置提供变量值，仓库、SQL、日志和文档不得记录具体值：

- `SF_API_BASE_URL`：顺丰接口地址，默认仅允许沙箱白名单地址。
- `SF_PARTNER_ID`：顺丰开放平台合作方标识。
- `SF_CHECK_WORD`：顺丰签名校验材料。
- `SF_CUSTOMER_CODE`：客户编码。
- `SF_MONTHLY_ACCOUNT`：月结账号。
- `SF_API_TIMEOUT_MS`：请求超时时间。
- `SF_API_RETRY_LIMIT`：可重试错误的最大重试次数。
- `SF_CALLBACK_SECRET`：回调签名共享密钥。
- `SF_SANDBOX_ENABLED`：是否允许沙箱调用的显式开关。
- `SF_TEST_ORDER_ID`：仅用于人工确认的沙箱测试订单标识。
- `SF_TEST_TRACKING_NO`：仅用于人工确认的沙箱测试运单号。

## 需要业务方补充的官方资料

1. 顺丰官方沙箱账号及授权范围；
2. `partnerID`；
3. `checkWord` 或官方签名密钥；
4. 客户编码和月结账号；
5. 国际件产品/服务代码及目的地服务范围；
6. 五个 `serviceCode` 的官方请求、响应和业务错误码定义；
7. 面单格式、下载方式和保存期限；
8. 清关资料格式、字段、大小限制和重传规则；
9. 查询响应中的外部订单号、运单号、状态和轨迹字段；
10. 回调地址、签名方式、时间窗口和 IP 白名单；
11. 是否允许创建、取消和查询可追踪的沙箱测试件；
12. 沙箱测试件的人工清理责任人和保留期限。

## 安全边界

- `SF_API_BASE_URL` 必须通过 URL 白名单校验，生产地址在沙箱开关关闭时拒绝。
- 日志只记录 `serviceCode`、`requestID`、HTTP 状态码、业务返回码和本地 `traceId`；不得记录签名、密钥、完整报文或个人地址。
- 顺丰调用只能由受控后端服务发起，浏览器不得读取或提交 `partnerID`、`checkWord` 等凭据。
- 创建、取消和上传必须使用服务端幂等键、事务边界和审计记录；查询与重试也必须可审计。
- 未取得上述官方资料前，不新增猜测性的 `msgDigest` 算法或供应商字段映射。

## 本轮学习记录

顺丰协议的公共参数名不足以确定签名算法。必须以官方文档和沙箱验证结果为准，再设计 provider adapter、请求/响应 DTO、重试策略和持久化字段。
## 2026-08-16 实施更新

后端已按顺丰沙箱协议实现签名和受控调用链。配置变量统一为 `SF_OPEN_URL`、`SF_OPEN_PARTNER_ID`、`SF_OPEN_CHECK_WORD`、`SF_OPEN_CUSTOMER_CODE`、`SF_OPEN_TIMEOUT_MS`、`SF_OPEN_RETRY_LIMIT`、`SF_OPEN_SANDBOX_ENABLED`。`SF_OPEN_URL` 仅允许 `https://sfapi-sbox.sf-express.com/std/service`，且必须显式开启沙箱开关。

`SfSignUtil` 使用 `Base64(MD5(URLEncoder.encode(msgData + timestamp + checkWord, "UTF-8")))`，`timestamp` 为十进制 Unix epoch 秒字符串。创建订单报文由当前租户订单的地址、商品和客户编码生成；清关上传要求 `certName`、`certCardNo`、`certType`、`frontPic`、`backPic` 字段。日志仅记录 serviceCode、requestID、HTTP 状态码、业务返回码和 traceId，不记录密钥、签名、完整报文或完整响应。

本次代码测试已通过，但当前 Codex 进程未提供上述变量，因此没有执行真实沙箱请求；若顺丰响应字段与已确认字段不同，必须先补充官方响应示例再联调。
