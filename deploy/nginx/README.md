# ShipFlow 本地 Nginx 网关

本目录提供 Windows 本地开发用的 Nginx 配置样例，也保留迁移到部署环境时替换监听端口、后端地址和静态资源目录的空间。Nginx 仅承担同源入口、静态资源入口和反向代理，不负责 JWT 解析、RBAC 校验、Token 生成或业务幂等。

## 目录与安装方式

本阶段不安装或启动 Nginx。系统已经安装 `nginx.exe` 时，可将文件复制到独立的 Nginx 安装目录：

```text
<nginx-home>/
├── conf/
│   ├── nginx.conf              <- nginx.conf.example
│   └── conf.d/
│       └── shipflow.local.conf
├── html/
├── logs/
└── nginx.exe
```

使用复制而不是直接修改 Nginx 安装目录中的配置，可使仓库内样例保持可审查、可迁移。不要把本地日志、Cookie、Token 或密码写入仓库。

## 配置校验

仅当系统已经安装 Nginx 后，在 PowerShell 中执行：

```powershell
Set-Location C:\path\to\nginx
.\nginx.exe -t -p (Get-Location).Path -c conf/nginx.conf
```

校验成功后才考虑启动或重载；本阶段不执行启动、重载和 HTTP 验收。`-t` 会读取配置，并可能尝试打开配置中声明的日志与监听资源，所以应在实际 Nginx 安装目录内校验。

## 本地访问

默认监听 `80`，后端固定代理到 `http://127.0.0.1:8080`：

- Vue API 基址：`/api`
- 后端健康检查：`http://localhost/actuator/health`
- OpenAPI：`http://localhost/v3/api-docs`
- Swagger UI：`http://localhost/swagger-ui/`

若 Windows 上端口 `80` 已占用，将 `shipflow.local.conf` 中的 `listen 80 default_server;` 改为 `listen 8081 default_server;`，随后通过 `http://localhost:8081` 访问。Vue 仍使用相对基址 `/api`，不写死端口。

当前尚无 Vue 构建产物，因此 `/` 明确返回 `404 ShipFlow frontend build is not available.`，不会把页面请求误代理到 Spring Boot。前端产物准备好后，将 `location /` 替换为指向部署环境静态目录的 `root` 与 SPA `try_files` 配置。

## 无敏感信息的人工验证

在 Nginx 和后端均由操作者确认已启动后，可运行以下只读请求：

```powershell
curl.exe http://localhost/actuator/health
curl.exe http://localhost/v3/api-docs
```

端口改为 `8081` 时相应改用 `http://localhost:8081/...`。不要在命令行、终端录屏或日志中加入 `Authorization`、Cookie、Token 或密码。OpenAPI 当前预期有 82 个 operationId；计数验证属于独立验收，不由 Nginx 配置校验代替。

## 部署迁移

迁移时按环境替换以下位置：

1. `listen`：本地为 `80`（冲突时 `8081`），部署环境通常由平台约定 `80/443`。
2. `server_name`：替换为实际域名。
3. `upstream shipflow_backend`：将 `127.0.0.1:8080` 替换为部署环境内可达的 Spring Boot 服务地址；不要暴露为浏览器 API 基址。
4. `location /`：指向 Vue 构建产物，并使用 SPA 回退规则。
5. HTTPS：在部署环境配置证书与 `listen 443 ssl`，并确保后端收到正确的 `X-Forwarded-Proto`。

不应添加宽松的 `Access-Control-Allow-Origin: *`。浏览器、Vue 页面和 API 都从同一个 Nginx origin 访问，因此正常流程不依赖 CORS。
