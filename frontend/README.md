# ShipFlow Frontend

ShipFlow 前端采用 Vue 3、Vite、TypeScript、Vue Router、Pinia 与 Axios，包含中文物流官网、在线报价入口以及已接入后端业务能力的权限控制台。

## 本地开发

环境要求：Node.js 18+、npm 9+。安装依赖并启动：

```powershell
Set-Location D:\Projects\shipflow\frontend
npm install
npm run dev
```

Vite 开发服务器默认监听 `http://localhost:5173`。浏览器仍只发起相对 `/api/v1` 请求；开发服务器将 `/api` 转发到本机 Nginx `http://127.0.0.1:80`，再由 Nginx 转发至 Spring Boot。前端代码不直接访问后端 `:8080`。

调用链如下：

```text
浏览器 :5173 → Vite /api 代理 → Nginx :80 → Spring Boot :8080 → MySQL
```

若本地 Nginx 改为 `8081`，仅调整 `vite.config.ts` 中开发代理的 `target` 为 `http://127.0.0.1:8081`；业务代码中的 `/api/v1` 保持不变。

## 构建

```powershell
Set-Location D:\Projects\shipflow\frontend
npm run build
```

生产构建输出到 `frontend/dist/`。部署时将该目录内容复制到 Nginx 的独立静态资源目录，并把网关 `location /` 从当前明确 404 改为实际 `root` 与 SPA fallback：

```nginx
location / {
    root  D:/path/to/shipflow-frontend;
    try_files $uri $uri/ /index.html;
}
```

`root` 必须指向包含 `index.html` 的目录。API location `/api/` 保持现有反向代理规则，页面与 API 由同一 Nginx origin 提供，因此不依赖宽松 CORS。

## API 与安全约束

- Axios 基址固定为 `/api/v1`，并启用 `withCredentials: true`。
- 登录先调用 `GET /api/v1/auth/csrf`，读取 `XSRF-TOKEN` Cookie，再以匹配的 `X-XSRF-TOKEN` Header 调用 `POST /api/v1/auth/login`。
- Access Token 仅保存在 Pinia 内存并由 Axios 请求拦截器添加，不写入 LocalStorage 或 SessionStorage。
- Refresh Token 由后端以 HttpOnly Cookie 管理，前端 JavaScript 不读取。
- 各领域采用独立 typed service；写请求统一处理 CSRF、请求 ID 和契约要求的幂等键。
- 401 最多进行一次共享刷新并重试，登录、刷新、退出和 CSRF 请求不会进入刷新循环。

## 已完成范围

- `/`：物流官网首页与报价转化入口。
- `/quote`：五步报价表单、前端校验、契约字段预览，不持久化联系人数据。
- `/login`：真实 CSRF → 登录接口流程。
- `/app`：真实运营概览；按 scope 与 permission 限制菜单和路由。
- `/app/tenants`、`/app/users`、`/app/stores`、`/app/rbac`、`/app/logistics`：基础资料列表与部分创建动作。
- `/app/quotes`、`/app/orders`、`/app/warehouse`、`/app/tracking`：报价到履约主流程的查询和关键操作。
- `/app/exceptions`、`/app/billing`、`/app/audit`：异常索赔、账单对账、审计查询入口。
- 未知路由：独立 404 页面。

完整的 82 个 operation 覆盖状态和契约缺口见 [`docs/openapi-coverage.md`](docs/openapi-coverage.md)。

## 待补充的交互

1. 将已封装的编辑、角色绑定、价格规则、索赔处理、账单导入和对账确认补成领域化表单。
2. OpenAPI 补齐订单列表/草稿更新，以及两个轨迹查询的响应 schema 后，替换当前按 ID 查询或只读 JSON 展示。
3. 官网询价与登录后正式报价仍是两个入口；联系人等官网字段不擅自写入正式报价契约。

## 接入笔记

模块级业务规则、权限边界、幂等与时区处理见 [`docs/business-integration-notes.md`](docs/business-integration-notes.md)。

## 提请项目负责人确认

1. 请确认 `ShipmentOrderResponse` 是否应返回订单 `version`；现有更新、提交和取消请求需要该值，但查询响应无法提供。
2. 请确认平台物流资料的分页参数是否应加入 OpenAPI；当前契约未声明分页 query。
