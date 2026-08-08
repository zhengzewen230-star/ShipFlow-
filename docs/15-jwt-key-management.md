# ShipFlow JWT 密钥管理说明

## 1. 密钥职责

Access Token 使用 RS256。签发侧使用 RSA 私钥，验证侧使用 RSA 公钥；私钥只用于签发，不进入 JWT Claims、日志、异常消息或数据库。公钥可以分发给资源服务用于验签。

第一版使用固定 `activeKid` 标识当前签发密钥。轮换期间保留旧公钥和新公钥，签发只使用新私钥；旧公钥在旧 Access Token 全部过期后再下线。

## 2. PEM 格式

- 私钥必须是 PKCS#8：`BEGIN PRIVATE KEY` / `END PRIVATE KEY`；
- 公钥必须是 X.509 SubjectPublicKeyInfo：`BEGIN PUBLIC KEY` / `END PUBLIC KEY`；
- 只接受 RSA，密钥长度至少 2048 位；
- 文件不存在、格式错误、算法错误或密钥长度不足时启动失败；
- 不在启动时随机生成生产密钥，避免重启导致全部 Token 无法验证。

## 3. 配置与挂载

配置只保存路径和标识，不保存 PEM 内容：

- `SHIPFLOW_JWT_ENABLED`
- `SHIPFLOW_JWT_ISSUER`
- `SHIPFLOW_JWT_AUDIENCE`
- `SHIPFLOW_JWT_ACCESS_TOKEN_TTL`
- `SHIPFLOW_JWT_CLOCK_SKEW`
- `SHIPFLOW_JWT_ACTIVE_KID`
- `SHIPFLOW_JWT_PRIVATE_KEY_LOCATION`
- `SHIPFLOW_JWT_PUBLIC_KEY_LOCATION`

本地和 Ubuntu 环境使用外部挂载 PEM 文件；后续容器部署使用只读 Secret/文件挂载。私钥文件应由运行应用的专用用户读取，建议权限为 `0600`，公钥可使用更严格的只读权限。私钥不得进入 Git、镜像层、日志、备份或示例配置。

## 4. 测试边界

单元测试可以在内存中生成临时 RSA 2048 位密钥对，不写入项目文件、不连接外部数据库、不生成生产密钥。
