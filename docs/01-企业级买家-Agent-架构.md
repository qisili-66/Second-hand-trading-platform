# 企业级买家 Agent 架构

## 目标与边界

一期交付的是只读、可审计的买家导购与确定性问答。它可以帮助用户检索在售商品、核验商品实时状态、读取公开卖家信用、查询本人订单和偏好，并给出推荐。它不能创建订单、发私聊、发布商品或求购、处理纠纷，也不能调用任何支付能力。

系统的事实源始终是 Spring Boot 和 MySQL。FastAPI 负责 Agent 编排与模型调用，不再承担直接查询 MySQL 的职责。

```text
浏览器 (JWT)
    │ POST /api/agent/buyer/runs
    ▼
Spring Boot ── 写入 Run / 调用 FastAPI ──► FastAPI + LangChain + LangGraph
    ▲                                           │
    │  X-Agent-Service-Token                     │ 只读工具调用，单次 3 秒
    └──── /api/internal/agent-tools/* ◄──────────┘
    │
    ├── MySQL：商品、订单、信用、审计记录
    └── 返回已校验的推荐快照与脱敏时间线
```

## 职责划分

| 组件 | 责任 | 不承担的责任 |
| --- | --- | --- |
| Vue 前端 | 提交自然语言需求、展示结果和服务端时间线 | 传递可被信任的 `userId`、让 Agent 触发任何写操作 |
| Spring Boot | JWT 鉴权、业务事实查询、权限校验、Run/Step/Recommendation 落库、推荐二次校验 | 把数据库连接或支付权限交给模型 |
| FastAPI | LangGraph 编排、调用 Agnes OpenAI-compatible 模型、将工具过程转为步骤 | 直连 MySQL、直接对外暴露内部工具 |
| LangGraph | 根据已获得的信息选择工具、决定是否继续、生成收尾文本 | 无限循环或绕过工具生成确定性事实 |
| MySQL | 业务主数据和审计数据 | 向量检索 |

## 身份、授权与网络边界

1. 浏览器访问 `/api/agent/**` 必须携带用户 JWT。Spring 从 JWT 的 `authId` 取得用户身份，前端请求体中的 `userId` 被覆盖。
2. Spring 调用 FastAPI `/agents/buyer/runs` 时也必须携带 `X-Agent-Service-Token`；AI 服务拒绝浏览器或其他调用方伪造的执行请求。
3. AI 服务仅通过 `/api/internal/agent-tools/**` 访问业务数据。该路径必须携带同一个 `X-Agent-Service-Token`。
4. 本地开发中内部工具仅由 AI 服务调用，不应向前端暴露服务令牌。
5. 订单查询工具以由 Spring 注入的认证用户为约束，只能返回该用户的订单状态。
6. 工具仅提供读取能力；Agent 不会调用任何业务写接口。用户如需交易，只能离开 Agent，通过既有业务页面自行完成操作。

本地 `ai/.env` 与 `backend/.env` 必须使用相同的 `AGENT_SERVICE_TOKEN`；不得提交 `.env`、JWT 密钥或模型密钥。

## LangGraph 有界执行

实现位于 `ai/app/agents/buyer_run_agent.py`。图的基本循环是：

```text
理解用户需求 → 模型选择只读工具 → ToolNode 执行 → 模型判断是否信息充分 → 输出
```

- 默认最大工具调用数为 `6`，通过 `AGENT_MAX_TOOL_CALLS` 配置；代码会强制封顶为 6。
- 每个内部工具调用默认超时 `3` 秒，由 `AGENT_TOOL_TIMEOUT_SECONDS` 配置。
- Spring 调用 FastAPI 的超时默认 `25` 秒，由 `AI_SERVICE_TIMEOUT_SECONDS` 配置。
- LangGraph 的递归上限由工具调用上限推导，防止模型反复调用工具。
- Agnes 配置为 OpenAI-compatible Tool Calling 模式；模型不可用、依赖未安装或工具失败时回退到规则筛选结果。

当前工具集如下：

| Tool | Spring 内部端点 | 用途 |
| --- | --- | --- |
| `search_items` | `search-items` | 仅检索当前在售商品，可按关键词、校区和最高价格筛选 |
| `get_item_realtime` | `item-realtime` | 获取商品实时价格、状态、成色、校区和交易方式 |
| `get_seller_summary` | `seller-summary` | 获取公开卖家信用与评价汇总 |
| `get_order_status` | `order-status` | 查询当前用户自己的订单状态 |
| `get_user_preferences` | `user-preferences` | 读取当前用户的收藏/历史偏好摘要 |
| `get_trade_rules` | `trade-rules` | 读取结构化平台交易规则 |
| `search_product_knowledge` | 本地 Qdrant 检索 | 检索商品和规则知识证据；二期配置完成后可用 |

工具结果是价格、状态和订单信息的唯一来源。模型的自然语言收尾不能替代工具结果；降级回答会明确标记为基础筛选，不能编造库存、价格、订单或信用。

## 审计、可解释性与推荐校验

每次买家请求都会生成一个 `runId` 和链路 `traceId`：

- `agent_runs`：用户、输入、运行状态、模型、开始/结束时间、输出和链路 ID。
- `agent_steps`：每个工具调用的顺序、工具名、脱敏后输入与输出摘要、状态、耗时、错误码。
- `agent_recommendations`：模型建议的商品 ID 与理由，以及后端重新读取后保存的真实快照。

模型提交的推荐不会直接可信。Spring 会逐个重新执行实时商品查询；只有商品仍可见、仍为 `ON_SALE` 且满足当前可访问条件时，才会保存并返回该商品的价格和状态快照。变价、下架或不可访问的商品会被排除。

用户读取 `GET /api/agent/runs/{runId}` 时，服务端按用户边界返回脱敏时间线，不返回原始提示词或敏感工具参数。默认定时清理 90 天前的 Run、Step 与 Recommendation，可用 `AGENT_TRACE_RETENTION_DAYS` 调整为 30 至 365 天。

## 可观测性与降级

FastAPI 在配置 `OTEL_EXPORTER_OTLP_ENDPOINT` 时导出 OpenTelemetry trace/span；未配置时保持本地运行，不依赖 LangSmith 云服务。应至少采集以下指标：

- 工具失败率、工具超时率和平均工具调用次数；
- Agent 总耗时、25 秒请求超时率、模型熔断次数；
- 无证据拒答率与 RAG 检索命中率；
- 推荐后的点击和订单转化。

本地联调时发生模型、Qdrant 或内部工具故障，不允许用模型猜测确定性数据；应返回可识别的基础筛选或“当前无法取得证据”结果，并记录失败步骤。
