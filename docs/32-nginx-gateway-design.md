# ShipFlow Nginx 网关设计与配置

## 1. 阶段范围

本阶段只建立 Nginx 网关配置和运维说明，不修改 Spring Boot 业务代码、OpenAPI、数据库、迁移、API 测试或 Jenkins，也不安装或启动 Nginx。当前 OpenAPI 基线为 82 个 operationId，前端尚未开始开发。

## 2. 参与方与调用关系

```text
浏览器
  │ 加载页面、同源 /api 请求
  ▼
Vue（未来由 Nginx 提供静态文件）
  │ 相对地址 /api，不感知后端端口
  ▼
Nginx（默认 localhost:80）
  │ /api/*、健康检查、开发文档反向代理
  ▼
Spring Boot（127.0.0.1:8080）
  │ 认证、授权、事务、幂等、租户隔离及数据访问
  ▼
MySQL
```

浏览器未来只访问 Nginx。Nginx 不连接 MySQL；MySQL 也不对浏览器或 Vue 暴露。后端仍是认证、RBAC、租户边界、状态变化、幂等和事务的唯一决策者。

## 3. 路由与异常路径

| 浏览器路径 | Nginx 行为 | 目标/结果 |
| --- | --- | --- |
| `/api/` | 保留原 URI 反向代理 | `http://127.0.0.1:8080/api/` |
| `/actuator/health` | 仅开放精确健康检查路径 | `GET http://127.0.0.1:8080/actuator/health` |
| `/v3/api-docs`、`/v3/api-docs/` | 开发期文档代理 | Springdoc OpenAPI |
| `/swagger-ui/` | 开发期 UI 资源代理 | Springdoc Swagger UI |
| `/` 及其他前端路径 | 当前不代理后端 | 明确文本 `404` |

连接后端超时、后端拒绝连接或读取超时由 Nginx 返回网关错误，不能伪装为业务成功。当前 `location /` 不依赖不存在的 `dist`；未来有 Vue 构建产物后，才替换为静态目录和 SPA history fallback。

请求体上限为 `20m`，连接超时为 `5s`，普通 API 发送/读取超时为 `60s`，健康检查为 `15s`，开发文档为 `30s`。如实际导入接口需要更大请求体，应根据已确认的业务上限单独调整，而不是取消限制。

## 4. 登录与认证信息流

### 4.1 CSRF Cookie

1. Vue 通过 Nginx 请求 `GET /api/v1/auth/csrf`。
2. Spring Security 生成可由浏览器端读取的 `XSRF-TOKEN` Cookie，经 Nginx 的 `Set-Cookie` 响应头返回浏览器。
3. Vue 在登录、刷新和退出请求中读取该 Cookie，并把同值放入 `X-XSRF-TOKEN` 请求头；浏览器同时发送 Cookie。
4. Nginx 原样转发 Cookie 和 `X-XSRF-TOKEN`，不解析、不比较令牌；Spring Security 完成校验。

### 4.2 HttpOnly Refresh Cookie

登录成功后，Spring Boot 通过 `Set-Cookie` 返回 Refresh Cookie。Nginx 将其透传给浏览器。该 Cookie 为 HttpOnly 时 Vue JavaScript 不能读取，这是预期的安全边界；浏览器按 Cookie 属性在刷新、退出请求中自动携带，Nginx 再原样转发。Refresh Session 的轮换、撤销、重复刷新处理和事务均由后端完成。

### 4.3 Access Token

Spring Boot 在登录或刷新响应 JSON 中返回 Access Token，Nginx 只转发响应。Vue 应在内存中持有 Token，并在需要认证的 API 请求中发送 `Authorization: Bearer ...`。Nginx 原样传递 `Authorization`，但不解析 JWT、不验证 RBAC、不生成 Token，也不将 Authorization 或 Token 写入访问日志。

## 5. 为什么 Vue API 基址必须是 `/api`

相对基址让页面与 API 共享浏览器 origin。例如页面来自 `http://localhost` 时，`/api/v1/users/me` 仍发往 `http://localhost`，再由 Nginx 转至 `127.0.0.1:8080`。这带来三点约束收益：

- Vue 不知道也不暴露 Spring Boot 的 `:8080` 地址，切换环境不需要重新写后端主机。
- Cookie、CSRF 和浏览器同源策略保持一致，不需要用宽松 CORS 补救架构问题。
- 部署环境可替换 Nginx upstream、域名和 TLS，而 Vue 的业务调用路径保持稳定。

前端代码或环境变量不得把 API 基址设为 `http://127.0.0.1:8080`。配置中也不添加 `Access-Control-Allow-Origin: *`。

## 6. 代理头与日志安全

Nginx 向后端传递：

- `Host`、`X-Real-IP`、`X-Forwarded-For`、`X-Forwarded-Proto`；
- 调用方已有的 `X-Request-ID`，缺失时由 Nginx 生成；
- API 所需的 `Authorization`、`Cookie`、`X-XSRF-TOKEN`；
- 后端响应中的 `Set-Cookie`。

访问日志只记录客户端地址、请求行、状态码、响应大小、Referer、User-Agent、请求 ID 和耗时。它不记录请求/响应体、Authorization、Cookie、Set-Cookie、X-XSRF-TOKEN 或密码。请求 ID 只应是非敏感关联标识，调用方不得把 Token 或客户数据放入其中。

## 7. 本地与部署环境替换

本地默认拓扑是 Nginx `localhost:80` 到 Spring Boot `127.0.0.1:8080`。端口 `80` 冲突时只把 Nginx `listen` 改为 `8081`，浏览器改访问 `http://localhost:8081`，Vue 仍调用 `/api`。

未来部署时替换 `server_name`、监听端口/TLS 证书、upstream 服务地址和 Vue 静态目录。若 Nginx 与后端位于不同容器或主机，`127.0.0.1:8080` 必须换成部署网络内的服务发现名称或内部地址。生产应使用 HTTPS，并让 `X-Forwarded-Proto` 反映浏览器实际协议；Spring Boot 是否信任转发头须作为部署安全配置单独确认。

## 8. 校验与非敏感验证

仅在系统已经安装 `nginx.exe` 且配置已按 `deploy/nginx/README.md` 放入 Nginx 安装目录时执行：

```powershell
.\nginx.exe -t -p (Get-Location).Path -c conf/nginx.conf
```

Nginx 与后端均已由操作者启动后，可执行：

```powershell
curl.exe http://localhost/actuator/health
curl.exe http://localhost/v3/api-docs
```

若监听 `8081`，将 URL 改为 `http://localhost:8081/...`。这两条命令不附带 Authorization、Cookie、Token 或密码；响应若包含异常环境信息，应先脱敏再共享。本阶段按边界不执行这些 HTTP 请求。

## 9. 业务规则与异常责任

- Nginx 不改变后端写接口的幂等键、事务边界、重复请求语义或租户识别方式。
- 租户身份和权限仍由受信 JWT 与 Spring Security/业务层判断，不能从普通代理头覆盖。
- CSRF 失败、认证失败、RBAC 拒绝、业务状态冲突和数据异常均由后端返回既有错误合同。
- Nginx 只处理连接、超时、请求大小、静态资源和路由级异常，不把失败缓存或转换为成功。

## 10. 本模块学习记录

本阶段确认同源 Nginx 是浏览器的唯一入口，而不是新的认证层。最关键的配置选择是保留 `/api` URI、完整透传认证信息但禁止敏感日志，并在 Vue 尚不存在时让 `/` 明确 404，避免页面请求意外进入业务后端。配置文件与 Nginx 安装目录解耦，迁移环境时只替换入口、upstream、TLS 和静态目录。

## 11. 提请项目负责人确认

1. 未来 Vue 构建产物的发布目录和 SPA history fallback 策略由哪个部署流程负责？
2. 生产环境是否允许公开 `/v3/api-docs`、`/swagger-ui/` 和 `/actuator/health`，还是应由内网、鉴权或 IP allowlist 限制？
3. 当前 `20m` 请求体上限是否覆盖账单 CSV 等最大导入场景，是否需要按路由设置不同上限？
4. 部署环境由 Nginx 直接终止 TLS，还是由更上层负载均衡终止，并如何建立可信转发头边界？
