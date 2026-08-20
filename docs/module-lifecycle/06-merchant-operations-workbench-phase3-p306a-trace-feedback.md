# 商家业务员工作台第三阶段 P3-06a 概览失败追踪反馈

更新时间：2026-08-18（Asia/Shanghai）  
阶段状态：已完成，等待确认进入 P3-06b 列表跳转与筛选闭环。

## 1. 范围与业务流程

本子阶段只完善运营概览的失败反馈。工作台仍只调用真实的 `GET /api/v1/operations/workbench`，后端错误响应中的 `traceId` 经既有 `ApiError` 保留后，在 401、403、服务端错误或网络错误的概览失败态中按实际存在性展示。用户可复制追踪编号给运维排查；复制失败显示中文人工记录提示。该流程不修改订单、店铺、仓库、认证或权限状态，也不创建业务数据。

参与方为商家业务员、租户管理员和运维支持人员。前端的错误展示不替代后端 `tenant_id`、`store_id`、角色和资源归属校验；刷新和重试仍使用既有请求去重逻辑，401 仍由既有会话刷新/清理机制处理。

## 2. 修改内容

- `frontend/src/views/operationsWorkbench.ts`：错误分类结果保留脱敏 `traceId`；新增可注入复制函数，空追踪编号不复制，浏览器剪贴板拒绝时返回失败状态。
- `frontend/src/views/DashboardView.vue`：概览的 401、403 和通用失败态仅在后端提供追踪编号时展示“追踪编号”和复制按钮；复制结果使用中文状态提示。
- `frontend/src/styles/main.css`：补充紧凑、可换行的追踪编号和文字按钮样式。
- `frontend/src/views/operationsWorkbench.spec.ts`：覆盖 `traceId` 保留、复制成功、空编号和复制失败。
- `docs/module-lifecycle/06-merchant-operations-workbench-phase3-task-list.md`：同步 P3-04、P3-05 结案状态，并把后续工作拆为 P3-06a/P3-06b/P3-06c，避免将尚未完成的筛选回显误记为已完成。

## 3. 验证

- `npm run test:unit`：通过，14 个测试文件、53 个测试通过；工作台测试为 7 项。
- `npm run build`：通过，`vue-tsc -b` 与 Vite 生产构建通过。
- OpenAPI 静态检查：通过，106 个 operation，`getOperationsWorkbench` 和 `/operations/workbench` 均存在。
- `git diff --check`：通过；仅有既有工作区 LF/CRLF 提示，没有空白错误。

## 4. 浏览器与边界

- 正常已登录工作台继续使用真实 API 和原有刷新行为；本子阶段不引入静态数据或前端本地统计。
- 401、网络失败、refresh 失败清理、Cookie 面板属性与跨租户 404 的真实浏览器验证继续沿用 P3-05 的 `BLOCKED` 环境限制，未改写为通过。
- 未执行数据库写入、Flyway、顺丰生产调用、Git commit 或 push。

## 5. 下一步

进入 P3-06b 前，需只读核对订单、仓库、轨迹、异常和账单页面对工作台后端 target query 的接收、回显与权限拒绝处理；只修复已确认的筛选闭环代码缺陷。
