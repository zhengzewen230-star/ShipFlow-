# 登录身份查询与权限加载实现说明

## 当前范围

本轮只实现登录身份查询、账户状态检查、BCrypt密码校验和一次性权限加载。当前没有创建Controller，不开放`POST /api/v1/auth/login`，不签发Access Token，不处理Refresh Token、Cookie或CSRF。

## 分层职责

- Mapper：通过参数绑定执行平台用户或租户用户的精确查询；通过一次用户-角色-权限JOIN加载当前有效权限。
- Service：根据`tenantCode`选择查询路径，校验用户与租户状态、密码、角色作用域和Mock系统账号限制，返回脱敏的`LoginIdentity`。
- Controller：下一小步实现，负责请求校验、统一响应、错误码和Trace ID，不直接写SQL。

平台登录只使用`tenant_id IS NULL`和精确username；租户登录先通过`tenant_code`关联`tenant`，再使用`sys_user.tenant_id = tenant.id`和精确username。不存在跨租户模糊查询或根据客户端`tenant_id`查询的路径。

权限由单次JOIN返回，服务内使用集合去重，不把角色或权限写入JWT。SQL同时限制用户、租户、角色状态及角色作用域，跨租户或作用域不匹配统一视为`AUTH-1001`。

## 安全约束

- 使用Spring Security `PasswordEncoder`/BCrypt，不实现哈希算法。
- 用户不存在时使用调用方注入的固定Dummy BCrypt摘要执行一次校验。
- Dummy摘要不对应生产账号；本轮单元测试动态生成摘要。
- 不猜测`init_data.sql`现有摘要对应的密码；明确的本地测试密码映射仍是连接数据库集成测试和登录实现前的阻塞项。
- `LoginCredentials`不实现包含密码的`toString`；`LoginIdentity`不包含passwordHash、明文密码、Token或Claims。
- Mapper、密码校验或驱动异常统一转换为安全的`AUTH-1001`内部异常，不向外泄露查询细节。

## 验证边界

Mapper XML仅进行了MyBatis配置解析、resultMap和statement映射检查，未连接MySQL，未执行SQL。接口自动化尚未开始。
