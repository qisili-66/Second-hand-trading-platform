# learning.md

## bug 复盘

### 2026-07-19：默认 Python 3.14 导致 AI 服务依赖不兼容

- 现象：使用系统默认 `python` 导入 FastAPI/Pydantic/LangChain 相关依赖时失败，同时缺少 `pytest`、`langchain-openai` 等依赖。
- 原因判断：机器默认 Python 指向 3.14，而当前 FastAPI/Pydantic/LangChain 组合不适合作为本项目运行时。
- 修复：明确 AI 服务使用 Python 3.12；根目录 `start-ai.bat` 自动寻找 Python 3.12、创建 `ai/.venv`、安装依赖并启动 `127.0.0.1:8001`。
- 验证：Python 3.12 环境下 Agent 核心逻辑和测试通过。

### 2026-07-19：Spring Boot 4 Jackson 包名变化导致编译失败

- 现象：新增 `AgentService` 后编译失败，提示 `com.fasterxml.jackson.core.type`、`com.fasterxml.jackson.databind` 不存在。
- 原因判断：Spring Boot 4 默认 Jackson 3，包名迁移为 `tools.jackson.*`。
- 修复：使用 `tools.jackson.core.type.TypeReference` 和 `tools.jackson.databind.ObjectMapper`。
- 验证：后端 Maven 测试通过。

### 2026-07-19：公开接口被 JWT 拦截

- 现象：访问 `/api/items`、`/api/categories`、`/api/health` 曾返回 401，导致首页商品加载失败。
- 原因判断：仅在 `AuthInterceptor.isPublicApi` 内判断公开路径不够稳，实际运行中仍可能被拦。
- 修复：在 `CorsConfig.addInterceptors` 中使用 `excludePathPatterns` 显式排除公开接口。
- 验证：后端测试通过；实际体验需要重启后端进程。

### 2026-07-19：后台启动本地后端不稳定

- 现象：尝试用 PowerShell 后台启动 Maven/Spring Boot 时，日志为空或服务未稳定就绪。
- 原因判断：当前 Windows/PowerShell 环境对 `Start-Process`、重定向、PATH 大小写重复和后台 job 权限较敏感。
- 处理：清理临时进程，不把未知后台服务留给用户；最终提示用户用常规方式启动后端。

### 2026-07-20：旧 AI Agent 请求字段不兼容导致 422

- 现象：旧版本 Uvicorn 日志出现 `POST /agents/buyer HTTP/1.1 422 Unprocessable Entity`，Agent 业务代码未执行。
- 原因判断：FastAPI 在进入 endpoint 前使用 Pydantic 校验请求体；原 `AgentRequest` 必填字段只有 `message`，客户端若传 `prompt`、`content`、`userMessage` 等字段会直接 422。
- 修复：`AgentRequest` 通过 `AliasChoices` 兼容 `message/prompt/input/content/query/text/userMessage`，兼容 `userId/user_id`；同时修复 Pydantic 2.10 下 `dump_json` 的 `ensure_ascii` 参数不兼容。
- 现状：该旧入口已删除；当前 AI 服务只保留 `/agents/buyer/runs`，由 Spring 创建审计 Run 后调用。

### 2026-07-20：前端 Agent 调用超时

- 现象：前端提示 `timeout of 8000ms exceeded`，同时 AI 服务偶发 `Unsupported upgrade request`、`Invalid HTTP request received`、`422 Unprocessable Entity` 日志。
- 原因判断：`frontend/src/services/api.js` 的 Axios 实例全局超时为 8 秒；Agent 链路可能经过前端 -> Spring Boot -> Python AI -> 数据库/LLM，后端转发配置已是 25 秒，AI LLM 默认 18 秒，前端 8 秒会过早断开。
- 修复：普通 API 保持 8 秒超时，`agentApi.buyerRun` 单独设置 30 秒超时；Python LLM 调用增加硬超时保护，外部模型卡住时返回规则 fallback；Spring Boot 转发前归一 `message` 字段并使用 UTF-8 JSON 请求体。`Unsupported upgrade request` 多半是 WebSocket/HMR 或浏览器升级请求误打到 AI 服务 `8001`，不是 Agent JSON 主链路。
- 现状：一期已删除旧 `/agents/buyer` 与卖家 Agent 路径，排障应检查 `/agents/buyer/runs`。
- 追加：当 AI 返回 `loc:["body"] input:null` 时，说明请求根本没有 body；已在 Spring Controller 和 Python endpoint 两侧补清晰校验，统一提示“请先输入 Agent 需求内容”。
- 追加：Java 17 `HttpClient` 可能对明文 HTTP 尝试 HTTP/2 h2c upgrade，Uvicorn 会打印 `Unsupported upgrade request` 并返回 `Invalid HTTP request received`；调用 FastAPI/Uvicorn 本地服务时应强制 `HttpClient.Version.HTTP_1_1`。

### 2026-07-22：拦截器 excludePathPatterns 误伤需要鉴权的同路径 POST

- 现象：发布商品时 `POST /api/items` 返回 500，前端控制台只看到 Internal Server Error。
- 原因判断：`CorsConfig.addInterceptors` 曾用 `excludePathPatterns("/api/items")` 放行公开商品列表，但 Spring 路径排除不区分 HTTP 方法，导致 `POST /api/items` 也绕过 `AuthInterceptor`，控制器读取 `@RequestAttribute("authId")` 时缺失属性并抛 500。
- 修复：拦截器重新覆盖所有 `/api/**`，公开接口只在 `AuthInterceptor.isPublicApi` 内按 method + path 判断；同时发布页数字输入初始值改为 `null`，checkbox 改用 Element Plus `value`。
- 验证：前端 `npm run build` 通过；后端 Maven wrapper 在本机 PowerShell 环境仍可能启动失败，需用可用 Maven 环境复测。

### 2026-07-22：后台页看起来像占位符

- 现象：`/admin` 顶部后台通知固定显示 5 条举报、3 条纠纷和公告草稿；修改后台密码只在前端弹成功；商品管理“批量审核/审核”没有真实业务动作。
- 原因判断：后台页面已有接口基础，但部分操作仍停留在演示文案和前端提示，容易让整个管理后台显得像占位页。
- 修复：新增 `PATCH /api/admin/password`，校验旧密码并更新 `admin_users.password_hash`；后台通知从 `dashboard` 和 `notices` 实时汇总待办；商品管理审核按钮改为真实调用 `on-shelf`。
- 验证：前端构建和后端编译需要在本次改动后复跑；改动后端需要重启 Spring Boot 才能生效。


### 2026-09-03：一期 Agent 收敛为只读买家导购

- 决策：一期只交付可审计的买家导购与确定性问答，不再沿用旧的“生成草稿并辅助执行交易”的 Agent 口径。
- 原因：商品价格、在售状态、卖家信用和订单状态必须由业务系统提供真实数据；交易写操作会扩大越权、误操作和审计风险。
- 约束：Agent 只能检索在售商品、核验实时状态、读取公开卖家信用、查询本人订单和偏好并生成推荐；禁止创建订单、私聊、发布商品/求购/换物、纠纷处理和支付调用。
- 落地：Spring Boot 保存 `agent_runs`、`agent_steps`、`agent_recommendations`；FastAPI 只通过服务令牌保护的只读工具访问业务事实源。

### 2026-07-23：Windows Maven Wrapper 本机编译验证不稳定

- 现象：`backend\mvnw.cmd -DskipTests compile` 在 Windows 环境下曾报 `Cannot start maven from wrapper`，PowerShell 里提示不能对 null 值索引。
- 原因判断：Maven Wrapper 脚本直接读取 `(Get-Item $MAVEN_M2_PATH).Target[0]`，普通目录没有 `Target`，导致脚本进入空值异常。
- 修复：先保存 `Get-Item` 结果，只有 `Target` 存在且非空时才使用符号链接目标，否则使用普通 `$HOME/.m2/wrapper/dists`。首次本机缺 Maven 依赖时需要联网拉取 Maven Central。
- 验证：提升网络权限后 `backend\mvnw.cmd -DskipTests compile` 成功，输出 `BUILD SUCCESS`。

### 2026-07-23：评价显示、页面草稿与置换联系入口不完整

- 现象：个人中心“我的评价”在无评价或少量评价时显示不符合预期，信用分可能超过 100，星级排版溢出；页面输入关闭网站后丢失；`/swap` 非本人置换卡片只有“置换中”，没有联系换物人的真实入口。
- 原因判断：评价组件使用 `stats.creditScore || 100`，导致 0 被显示成 100，后端评价统计也直接返回内部累计信用分；前端未保存未发送输入；置换页已有按商品创建聊天的能力，但置换卡片没有把关联商品 `itemId` 接到联系人按钮上。
- 修复：评价列表无评价显示 0，信用分展示限制 0-100，并补 CSS 防止星级溢出；前端增加本地草稿读写清理；`/swap` 非本人置换卡片新增“联系换物人”，基于置换商品 `itemId` 创建聊天并发送置换咨询草稿。
- 验证：`frontend npm run build` 通过，存在既有 Rolldown `@vueuse/core` PURE annotation 和 chunk size warning；`backend mvnw.cmd -DskipTests compile` 通过；`git diff --check` 通过。

### 2026-07-23：商品详情卖家信息和头像能力不完整

- 现象：商品详情页卖家评价星星在宽屏下可能同时出现横向和竖向视觉，卖家头像不能进入个人主页，用户也不能在个人中心修改头像。
- 原因判断：评价组件顶部统计把星级塞进同一行末尾，宽屏容器不足时 Element Plus 星星被挤压；前端只有 `/profile` 私有个人中心，没有公开用户主页路由；后端已有 `avatar_url` 字段和图片上传接口，但个人中心没有把上传结果写回 `avatarUrl`。
- 修复：评价星级统一放入独立横向评分行；新增 `GET /api/users/{userId}` 和 `GET /api/users/{userId}/items` 公开接口，前端新增 `/users/:userId` 公开主页；商品详情卖家卡片可点击跳转；个人中心支持选择图片上传并更新 `avatarUrl`。
- 验证：`frontend npm run build` 通过，存在既有 Rolldown annotation/chunk warning；`backend mvnw.cmd -DskipTests clean compile` 通过；`git diff --check` 通过。

## 踩坑记录

- PowerShell 默认输出编码可能导致中文 Markdown 显示乱码；读取中文文档时使用 `Get-Content -Encoding UTF8`。
- 长期记忆库技能默认路径与用户实际路径不同；本项目以用户明确指定的 `D:\obsidian\codex长期记忆` 为准。
- 旧文档曾同时存在 `doc/` 与 `docs/`，容易造成维护分叉；现在统一维护在 `docs/`。
- 根 README 和历史文本曾提到 `fronted`，但当前真实目录是 `frontend/`。
- PowerShell 不支持 Bash 风格 `< file.sql` 输入重定向；执行 SQL 文件可用 `cmd.exe /c "mysql ... < file.sql"` 或 MySQL 自身参数方式。
- 执行清库脚本前必须确认目标数据库和备份策略；本地重置只针对 `127.0.0.1:3306/second_hand_trade`。
- 官方 curated 技能列表没有 AnySearch，experimental 路径不可用，GitHub 搜索也未找到明确 Skill 仓库；不要安装未知来源技能。
- 当前项目的 `mvnw.cmd` 已修复普通 `.m2` 目录 `.Target[0]` 为空导致的 Windows 启动失败；首次编译若 Maven Central 依赖缺失，需要联网下载依赖。
- Uvicorn 的 `Unsupported upgrade request` 通常说明 WebSocket/HMR/浏览器升级请求打到了纯 HTTP AI 服务端口；应确认页面访问前端 `5173`，后端 `8080`，AI `8001`。
- 也要警惕 Java `HttpClient` 的 h2c upgrade：即使请求路径和 body 都正确，只要 Java 客户端尝试升级 HTTP/2，Uvicorn 仍会报 unsupported upgrade。
- 前端全局超时不适合直接套给 Agent/LLM 链路；Agent 请求应有独立 timeout，避免普通接口保持灵敏和 AI 链路需要更长等待之间互相牵扯。
- LangChain/外部 LLM SDK 的 timeout 未必能覆盖 DNS、代理、底层连接或 SDK 内部阻塞；Agent 服务需要自己的硬超时保护，超时后返回 deterministic fallback。
- 本地调试便利不能靠在文档里保存密码或密钥解决；真实凭据只保留在被 Git 忽略的 `.env` 文件中。

## 错误用法

- 不要恢复 `doc/task_plan.md`、`doc/progress.md`、`doc/findings.md`、`doc/task_issue.md` 或 `docs/API.md`、`docs/DATABASE.md`、`docs/WORK.md` 的分散维护方式。
- 不要在未确认生产数据库和备份策略前运行初始化或重置数据库脚本。
- 不要把默认关闭的支付配置误解为已完成真实收款闭环。
- 不要把 ReactBits React 组件直接塞入 Vue 3 项目；应复刻动效思路或用 Vue/CSS 实现。
- 不要把一期 Agent 扩展成交易执行器；即使用户点击确认，也不允许 Agent 发私聊、创建订单、发布商品/求购/换物、处理纠纷或调用支付。
- 不要把请求 422 当作 Agent 内部逻辑错误；FastAPI 422 多数发生在 endpoint 执行前。
- 不要把浏览器主页面或 `/ws` 指向 AI 服务 `8001`；AI 服务只提供 HTTP JSON 接口。

## 反直觉结论

- 数据库只记录支付单和流水状态，真实资金流向取决于支付宝/微信商户号，不会“进入数据库”。
- WebSocket 客户端 `SEND` 已禁用，订单、聊天和通知写入仍通过 REST 接口完成，再由服务端推送。
- 手机端体验不是桌面端缩放，而是独立的信息架构：顶部状态、底部主导航、信息流卡片和粘性操作。
- 本地数据库 seed 正确不等于前端能显示；还需要确认后端已重启到最新代码、公开接口已放行、前端代理指向正确端口。
- FastAPI 422 错误体不会进入业务函数；日志只有 `POST /agents/buyer 422` 时优先检查请求体字段名、Content-Type 与 Pydantic schema。
- Pydantic 2.10 的 `model_dump_json()` 不支持 `ensure_ascii` 参数；如需中文 JSON，用 `json.dumps(model.model_dump(mode="json"), ensure_ascii=False)`。
- 一期 Agent 的价值来自可信推荐与可解释过程：模型通过只读工具获得真实事实，后端二次校验推荐快照并保存 Run/Step 审计，而不是代替用户执行交易。
- Agent 历史需要服务端审计，而非以浏览器 `localStorage` 作为事实来源；用户只能读取自己的 Run 与脱敏步骤。
- 公开个人主页要只暴露非隐私字段；头像、昵称、校区、院系、信用分和在售商品可以公开，手机号、邮箱、QQ、微信等隐私字段不能跟随 `/users/{userId}` 暴露。
- 评价星级组件不要和统计数字强行挤在同一网格末尾；需要横向展示时给星级独立容器，移动端可以上下排，但星星本身不能拆成竖列。
- 高质量代码不是代码更长，而是用合适的数据结构和小接口压住复杂度；如果一个功能要靠散落在模板里的重复分支维持，后续会很难改。

## 优化建议

- 优先补真实生产风险最高的支付闭环：回调验签、退款、分账/提现、资金台账、对账。
- 可继续清理前端历史 Mock 依赖，减少上线项目误用风险。
- 可补齐后台分类管理、商品编辑、我的求购/置换详情等剩余边界。
- 手机端建议做一次浏览器截图验收，覆盖 390px、414px、768px、1280px 四类视口。
- Agent 后续增强可以加入更明确的结构化错误返回、请求体日志脱敏、推荐结果 action schema 和商品详情/求购/发布页的端到端测试；有副作用动作继续保持用户确认、平台鉴权和后端业务规则三层约束。
- 若 Agent 历史记录需要跨设备同步，再新增后端表保存 `mode/message/result/createdAt/userId`，并保持前端 `agentHistory` 模块接口不变，替换存储适配即可。
- 本地构建前端、编译后端和运行 AI 服务时，应分别确认 Node、JDK 17 与 Python 3.12 环境可用。
