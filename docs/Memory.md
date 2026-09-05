# 项目入口与当前状态

## 项目结构

- `frontend/`：Vue 3 用户端与管理端界面；用 VS Code 打开。
- `backend/`：Spring Boot 业务服务、JWT 鉴权、MySQL 事实源和 Agent 审计；用 IDEA 启动。
- `ai/`：FastAPI、LangChain/LangGraph 买家 Agent；根目录 `start-ai.bat` 一键启动。
- `backend/sql/`：数据库结构和演示数据。
- `docs/`：当前唯一文档目录。

## 一期 Agent 的固定边界

一期交付的是**只读、可审计的买家导购与确定性问答**。

它可以：

- 检索当前在售商品；
- 读取商品实时状态、价格、成色、校区和交易方式；
- 读取公开卖家信用与评价汇总；
- 查询当前登录用户自己的订单和偏好；
- 返回推荐、风险提示及服务端脱敏时间线。

它不可以：

- 创建订单或预约；
- 发送私聊；
- 发布商品、求购或换物；
- 处理纠纷；
- 调用支付；
- 直连 MySQL、支付系统或读取密钥。

用户要交易时，必须自行通过商品详情、聊天、订单或发布页面操作；Agent 不会代替用户调用这些写接口。

## 已完成的 P0：能力边界收敛

- 删除卖家/发布 Agent 的前端入口、Spring 接口、FastAPI 接口、Schema、实现与测试。
- 删除首页中 Agent 代发私聊、代建订单、代发商品、求购、换物等写操作。
- 删除 Agent 的私聊、求购、换物、发布草稿字段和页面预填路径。
- Agent 历史改为服务端 `agent_runs` 为准；前端不再使用 `localStorage` 保存 Agent 历史或草稿。
- FastAPI 仅保留 `/agents/buyer/runs` 这一审计链路。

通俗说明见 [04-一期P0能力边界说明.md](04-一期P0能力边界说明.md)。

## 配置与安全

- `ai/.env`：仅本地，包含真实 Agnes API Key，必须被 Git 忽略。
- `ai/.env.example`：只保留占位符，可提交 Git。
- 模型配置只使用：`EXTERNAL_LLM_BASE_URL`、`EXTERNAL_LLM_MODEL`、`EXTERNAL_LLM_API_KEY`。
- `AGENT_SERVICE_TOKEN` 需要在 `backend/.env` 和 `ai/.env` 中保持一致，不能交给浏览器。
- 不保留服务器部署说明；当前项目以本地开发运行方式为准。

## 本地启动

1. 用 IDEA 启动 `backend/`，默认 `http://127.0.0.1:8080`。
2. 在 VS Code 终端进入 `frontend/`，运行 `npm run dev`，默认 `http://127.0.0.1:5173`。
3. 双击根目录 `start-ai.bat`，默认 `http://127.0.0.1:8001`。

## 已完成的 P0：可信数据、权限与审计

- Spring 用 JWT `authId` 覆盖浏览器提交的 `userId`。
- FastAPI 执行入口和 Spring 内部工具入口都验证 `AGENT_SERVICE_TOKEN`。
- 订单查询按当前用户的买家/卖家身份隔离；Run 的读取和清除也按当前用户隔离。
- 推荐落库前重新核对在售状态和价格；下架、不可见或变价商品会被排除。
- Run、Step、Recommendation 会持久化；AI 服务不可用时也会保存失败 Step。
- AI 已删除 MySQL 直连实现和 `DB_*` 配置，只能通过 Spring 内部只读工具取数。

## 已完成的一期 P1/P2：可用性、可观测性与只读洞察

- 首页会用通俗标签区分“模型增强推荐”和“基础筛选”，并展示“已查询商品、卖家信用、本人订单、偏好、商品实时状态”等脱敏步骤；失败步骤会明确提示已经安全降级。
- Agent 历史页面只读取服务端最近 20 条 Run；审计记录默认保留 90 天。
- ai/.env 设置 OTEL_CONSOLE_EXPORTER=true 后，可以在本机 AI 服务控制台看到 Agent Run、工具名、耗时、状态和错误类型。配置 OTEL_EXPORTER_OTLP_ENDPOINT 时才会向企业 Collector 输出遥测。
- 买家洞察接口只汇总本人收藏偏好、已完成订单和近期 Run；这些信息仅用于只读排序。卖家洞察只汇总本人商品、订单和评价；它不是卖家 Agent。
- 管理后台的 Agent 运营页只展示近 30 天的聚合 Run、失败、工具耗时、推荐记录和同一用户推荐后的订单关联；它不会读取私聊正文，也不把关联统计表述为因果结论。
- 一期自动化验收入口是 scripts/verify-phase-one.ps1。上线前仍需两个真实普通账号完成一次页面级越权与降级联调，详见 [03-Agent-接口与本地运行清单.md](03-Agent-接口与本地运行清单.md)。
