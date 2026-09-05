# RAG 知识库与运维

## 已实现范围

二期基础设施采用自托管 Qdrant，独立于 MySQL。知识来源限定为平台内商品、评价和管理员维护的交易规则/FAQ；不抓取外部网页或资料。这样可控制版权、时效性、审计和下架删除边界。

知识检索只用于需要解释的商品和规则问题。价格、在售状态、卖家信用和订单状态仍由 Spring 只读工具直答，不能由 RAG 或模型猜测。

## 本地 Qdrant 配置

仓库不再提供 Docker Compose 或服务器部署文件。需要启用 RAG 时，请先自行运行一个本地 Qdrant 实例，再在本机 `ai/.env` 中设置：

```env
QDRANT_URL=http://127.0.0.1:6333
QDRANT_COLLECTION=campus_trade_knowledge
EMBEDDING_PROVIDER=local
LOCAL_EMBEDDING_MODEL=BAAI/bge-m3
LOCAL_EMBEDDING_DEVICE=cpu
```

Qdrant 检索使用本地 `sentence-transformers` 模型，不依赖 Agnes 的 `/embeddings` 接口；聊天模型仍使用 `EXTERNAL_LLM_API_KEY`。首次运行会下载 `BAAI/bge-m3` 权重，下载完成后可离线生成向量。

## 数据流与一致性

```text
商品 / 评价 / 已发布规则发生变更
                 │
                 ▼
MySQL knowledge_outbox  ── claim ──► AI knowledge_worker
                 ▲                     │
                 │ complete             ├─ 读取权威内容
                 └──────────────────────┤
                                       ├─ 切片/嵌入
                                       ▼
                                     Qdrant upsert 或 delete
```

Spring 在商品发布、修改、上下架、删除，订单评价完成，以及规则文章发布/重建时向 `knowledge_outbox` 写入事件。Worker 通过受服务令牌保护的内部端点领取任务，读取权威源内容后完成索引。

当前实现按 `sourceType + sourceId` 计算稳定 UUID，因此重复处理同一事件会覆盖同一 Qdrant 点，具备幂等性。下架或删除事件使用相同 ID 执行删除；同时检索阶段会过滤非发布状态商品，避免旧向量在删除延迟期间被作为证据。

失败事件最多重试 5 次；超过次数会标记为 `FAILED` 并保留错误摘要，供后台排查与人工重建。

## 索引 Worker

一次消费命令：

```powershell
cd ai
.\.venv\Scripts\python.exe -m app.knowledge_worker
```

Worker 当前执行一次领取与处理。本地调试时按需执行该命令；不要绕过 Outbox 直接写 Qdrant。

排障顺序：

1. 确认 Qdrant 健康且 `QDRANT_URL` 从 AI 服务可达。
2. 确认 AI 与 Spring 的 `AGENT_SERVICE_TOKEN` 一致且非空。
3. 确认嵌入模型配置可用，且模型密钥未过期。
4. 查看 `knowledge_outbox` 的 `status`、`retry_count` 与 `last_error`，不要手工伪造已完成状态。
5. 在后台触发已发布规则的“重建索引”，或重新保存/发布相应权威内容以生成新事件。

## 检索与回答约束

`ai/app/rag.py` 每次检索最多返回 Top 5 证据，默认相似度阈值为 `0.35`。结果附带来源 ID、类型、标题与分数，前端/上层回答可显示引用。低于阈值、向量库不可用或无可用证据时，必须明确拒绝基于猜测作答。

推荐的问答分流规则如下：

| 问题类型 | 数据源 | 是否调用模型 | 回答要求 |
| --- | --- | --- | --- |
| 价格、在售状态、卖家信用 | Spring 只读工具 | 否 | 直接组织真实查询结果 |
| 本人订单状态 | Spring 鉴权工具 | 否 | 仅返回当前用户数据 |
| 商品参数、描述、评价摘要 | Qdrant RAG | 可以 | 返回证据引用；无证据即拒答 |
| 平台规则、验货、纠纷和售后 FAQ | 结构化规则优先，必要时规则 RAG | 仅解释时 | 返回规则版本与来源 |

## 管理后台知识库

管理员可通过“Agent 知识库”页面或以下接口维护规则与 FAQ：

- `GET /api/admin/knowledge-documents`：查看文章、版本和索引状态。
- `POST /api/admin/knowledge-documents`：创建草稿。
- `PUT /api/admin/knowledge-documents/{documentId}`：修改草稿内容。
- `PATCH /api/admin/knowledge-documents/{documentId}/publish`：发布并入队索引。
- `POST /api/admin/knowledge-documents/reindex`：为所有已发布文章重新入队。

每次发布会保留并递增版本信息。运营人员应先在草稿中完成审核，再发布；涉及交易、纠纷和安全的规则变更应同时走平台既有公告/治理流程，避免知识库说明与真实业务规则不一致。
