# 第二模块冻结接口 QA 用例导入记录

- 执行日期：2026-08-10（Asia/Shanghai）
- 导入文件：`database/qa/007_seed_module2_frozen_20_api_test_cases.sql`
- 文件 SHA-256：`818BFAE18BF1CFDA365D998629A45BB07EFCAD4D908A3DE66AE8165669F97A92`
- 唯一数据目标：`shipflow_qa.api_test_case`

## 执行前核验

| 核验项 | 结果 |
|---|---|
| 服务器实际数据库 | `shipflow_qa` |
| 服务器当前账号 | `shipflow_qa_writer` |
| 最小权限 | 仅 `shipflow_qa.api_test_case` 的 `SELECT`、`INSERT`、`UPDATE`（另有全局 `USAGE`） |
| 文件范围 | 66 个唯一用例；排除 `USER-018`、`USER-019`；1 个事务；1 条全限定 `INSERT`/upsert；无 `USE`、DDL、删除、迁移或业务表语句 |

## 执行结果

导入脚本在单一事务中成功提交。脚本只写入 `shipflow_qa.api_test_case`，没有执行其他 SQL、迁移、业务库操作或删除操作。

| 项目 | 结果 |
|---|---:|
| 导入前第二模块用例数 | 0 |
| 导入后第二模块用例数 | 66 |
| 净新增行数 | 66 |
| 规范化后的方法加路径组合 | 20 |
| `BLOCKED` 用例数 | 66 |
| 非 `BLOCKED` 用例数 | 0 |
| 失败项 | 无 |

MySQL 批处理客户端没有输出单独的 affected-row 指标；本记录的 66 是导入前后只读计数的净变化。由于导入前为 0、脚本只有一条原子 upsert，实际新增记录为 66。

## 异常与恢复说明

执行过程中发现并修复了脚本末尾 values/upsert 分隔符问题。两次失败调用都在提交前由客户端中止：一次为不支持的客户端参数，另一次为 SQL 语法错误；两次均未产生提交。修复后重新完成全部前置核验并成功导入。

未运行 pytest，未提交或推送 Git。密码、连接串、Token、Cookie 和其他凭据未写入本记录。

## 中文最终化升级（2026-08-10）

最终设计升级脚本为 `database/qa/008_upgrade_module1_module2_api_test_cases_cn_final.sql`。它不覆盖、不删除或修改历史 `007`，只在单一事务内按既有 case_no 更新第二模块 66 条已导入用例的中文面向人元数据、场景分类、优先级和结构化断言；状态显式保持 `BLOCKED`。

脚本范围仅为 `shipflow_qa.api_test_case`，不含 DDL、DELETE、迁移、业务表或业务数据库引用。它保留 HTTP 方法、请求路径、JSON 字段名、占位符、权限码、错误码和既有请求模板，避免把尚未在真实环境验收的草稿模板误标为可运行。

导入后的核对口径如下：

| 项目 | 目标 |
|---|---:|
| 第一模块 AUTH 用例 | 52 条，编号唯一 |
| 第二模块用例 | 66 条，编号唯一，USER-018/019 继续排除 |
| 第二模块冻结业务接口 | 20 个规范化“方法 + 路径”组合 |
| 第二模块运行状态 | 66 条均为 BLOCKED |
| 中文面向人字段 | title、precondition、data_dependency 均非空且中文化 |

## 最终中文化导入审计（2026-08-10）

最终执行文件为 `database/qa/008_upgrade_module1_module2_api_test_cases_cn_final.sql`，最终静态审查 SHA-256 为 `D80E9671E3ABD2EE470747284204BCE2FA39929861E19B2BD7A124101BDDD3D7`。执行前再次确认数据库为 `shipflow_qa`、账号为 `shipflow_qa_writer`，且该账号仅拥有 `shipflow_qa.api_test_case` 的 SELECT、INSERT、UPDATE 权限。

首次文件执行因旧模块别名筛选而安全地更新 0 行；随后仅修正筛选为已导入的 `TENANT/STORE/USER/RBAC`，并重新静态审查后执行。最终的实际元数据更新为第二模块 66 行；第一模块另有 47 行仅为纯占位符的数据依赖添加“运行时变量：”中文前缀。第一模块的认证请求头、Cookie、请求体和执行逻辑均未修改。

| 最终只读核对项 | 结果 |
|---|---:|
| 第一模块 AUTH 用例 | 52 |
| 第二模块用例 | 66 |
| 总用例数 / 唯一 case_no 数 | 118 / 118 |
| 第一模块中文人类字段完整覆盖 | 52 / 52 |
| 第二模块中文人类字段完整覆盖 | 66 / 66 |
| 第二模块 BLOCKED / 非 BLOCKED | 66 / 0 |
| 第二模块规范化方法加路径组合 | 20 |
| 第二模块最终化实际更新行 | 66 |
| 第一模块必要中文补齐实际更新行 | 47 |
| 失败项 | 无 |

所有写入都在文件定义的单一事务内提交；未执行 DDL、DELETE、迁移、业务表操作或业务库连接。未运行 pytest、未启动后端、未提交或推送 Git。
