# 企业 Agent 文档

本目录描述当前已实现的企业级买家 Agent 与知识库基础设施。文档以代码当前行为为准，不将后续规划当作已交付功能。

| 文档 | 适用对象 | 内容 |
| --- | --- | --- |
| [01-企业级买家-Agent-架构.md](01-企业级买家-Agent-架构.md) | 开发与安全评审 | 服务边界、鉴权、LangGraph 有界编排、审计与降级 |
| [02-RAG-知识库与运维.md](02-RAG-知识库与运维.md) | 平台运营与运维 | Qdrant、Outbox、索引 Worker、知识库后台与故障处理 |
| [03-Agent-接口与本地运行清单.md](03-Agent-接口与本地运行清单.md) | 本地开发与测试 | 接口、本地配置、启动顺序、验收和明确不纳入范围的能力 |
| [04-一期P0能力边界说明.md](04-一期P0能力边界说明.md) | 产品、测试与使用者 | 用通俗语言说明一期 Agent 现在能做什么、不能做什么 |
| [work.md](work.md) | 开发排期 | 一期和二期的 P0/P1/P2 优先级工作清单 |

相关实现入口：

- Spring Boot Agent Run 服务：`backend/src/main/java/com/example/Second_hand/trading/platform/service/AgentRunService.java`
- FastAPI LangGraph Agent：`ai/app/agents/buyer_run_agent.py`
- Qdrant 索引 Worker：`ai/app/knowledge_worker.py`
- 数据表定义：`backend/sql/schema.sql`

本目录专门记录本次企业 Agent 与 RAG 建设。
