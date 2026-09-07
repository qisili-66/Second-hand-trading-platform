# Agent 接口与本地运行清单

## 对外接口

所有对外接口以 `/api` 为前缀。买家 Agent 接口要求普通用户 JWT；请求体中的 `userId` 不作为授权依据，后端只使用 JWT 的 `authId`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/agent/buyer/runs` | 创建并同步执行一次买家 Agent Run |
| `POST` | `/api/agent/buyer` | 兼容别名，内部转向同一个 Run 流程 |
| `GET` | `/api/agent/runs` | 读取当前用户的 Agent 历史 |
| `GET` | `/api/agent/runs/{runId}` | 读取当前用户指定 Run 的结果与脱敏时间线 |
| `DELETE` | `/api/agent/runs` | 删除当前用户的 Agent 历史 |
| `GET` | `/api/agent/insights/buyer` | 读取当前用户自己的收藏偏好、近期 Agent Run 与已完成订单汇总 |
| `GET` | `/api/agent/insights/seller` | 读取当前用户自己商品、订单、评价的只读经营摘要 |
| `GET` | `/api/admin/agent-operations` | 管理员读取近 30 天 Agent 聚合运行指标与失败类型 |

典型请求：

```json
{
  "message": "想在东校区找一台 2000 元以内、成色较好的平板"
}
```

响应包含 `runId`、`traceId`、推荐、模型/降级标识和 `timeline`。前端只展示服务端返回的脱敏时间线，不展示原始模型提示词或敏感工具参数。

## 本地配置

保留两份不含真实密钥的模板与本地配置：

- `backend/.env.example`：复制为本地 `backend/.env`，并在 IDEA 的 Run Configuration 中加载。
- `ai/.env.example`：复制为本地 `ai/.env`。`ai/.env` 已被 Git 忽略，真实 Agnes API Key 只能写在该本地文件。

AI 本地配置使用 Agnes OpenAI-compatible 接口：

```env
EXTERNAL_LLM_BASE_URL=https://apihub.agnes-ai.com/v1
EXTERNAL_LLM_MODEL=agnes-2.0-flash
EXTERNAL_LLM_API_KEY=replace_with_your_agnes_api_key
```

`AGENT_SERVICE_TOKEN` 必须同时配置在 `backend/.env` 与 `ai/.env`，且两侧值完全相同。它仅供 Spring 与 FastAPI 的内部工具调用使用，不能交给前端。

Agent 的安全边界参数如下：

- `AGENT_TOOL_TIMEOUT_SECONDS=3`：一次读取商品、订单或信用工具最多等待 3 秒。
- `AGENT_RUN_TIMEOUT_SECONDS=25`：一次完整导购最多等待 25 秒。
- `AGENT_MAX_TOOL_CALLS=6`：模型整次导购最多调用 6 次只读工具。

模型 Key 未配置、内部服务令牌不一致、工具超时或 AI 服务不可用时，接口会返回标记为“基础筛选”的空推荐结果，不会猜测价格、订单或信用信息；同时该 Run 会记录为失败，并保存失败步骤，便于排查。

### 本地观测（可选）

在本机 ai/.env 设置 OTEL_CONSOLE_EXPORTER=true 后重启 AI 服务。每次 Agent 请求会在 AI 服务控制台输出 span，可查看 agent.buyer.run 与 agent.tool.* 的 Run ID、工具名、耗时、状态和错误类型。不要把这个本地 .env 文件提交到 Git。

企业环境如已有 OpenTelemetry Collector，可设置 OTEL_EXPORTER_OTLP_ENDPOINT；未设置时不会产生外部遥测请求。

## 本地启动

1. 在 IDEA 启动 Spring Boot，默认地址为 `http://127.0.0.1:8080`。
2. 在 VS Code 的 `frontend/` 目录执行 `npm run dev`，默认地址为 `http://127.0.0.1:5173`。
3. 双击项目根目录 `start-ai.bat` 启动 AI 服务，默认地址为 `http://127.0.0.1:8001`。

启动后可检查：

```powershell
curl.exe http://127.0.0.1:8080/api/health
curl.exe http://127.0.0.1:8001/health
```

若 AI 健康接口中 `llm_configured` 为 `false`，检查本机 `ai/.env` 的 `EXTERNAL_LLM_API_KEY`；未配置有效 Key 时系统会安全降级为规则筛选，不会伪造确定性信息。

## 本地验收清单

- [x] Agent 没有卖家入口，也不会发私聊、创建订单、发布内容、处理纠纷或调用支付。
- [x] 前端不保存 Agent 草稿或本地历史；历史只读取服务端 Run。
- [x] FastAPI 只保留 `/agents/buyer/runs`，并删除卖家及非审计买家入口。
- [x] JWT 身份覆盖浏览器传入的 `userId`；订单工具无法读取其他用户订单。
- [x] 下架、变价或不可见商品不会进入推荐快照。
- [x] 模型或工具失败时，返回明确的基础筛选/无证据结果，不编造价格、订单或信用信息；服务端同时保留失败 Run 与失败步骤。
- [x] Run 的 Step 顺序、状态和耗时会保存；工具/服务失败也会留下失败 Step。
- [x] 自动化测试模拟两个普通用户，验证订单越权和 Run 隔离；上线前仍建议做一次真实账号联调。
- [x] 首页会显示“模型增强推荐”或“基础筛选”，并展示服务端脱敏执行时间线；失败工具会标记为安全降级。
- [x] 服务端历史列表限制为最近 20 条，审计默认保留 90 天；可用 OTEL_CONSOLE_EXPORTER=true 在本地观察 Run 与工具 span。
- [x] 买家/卖家洞察仅返回当前 JWT 用户的只读汇总；管理员运营页仅返回聚合 Agent 审计指标，不读取私聊内容。

快速验证命令：

```powershell
cd ai
.\.venv\Scripts\python.exe -m pytest app\tests -q

cd ..\backend
.\mvnw.cmd -q -DskipTests compile

cd ..\frontend
npm run build
```

完整 Maven 测试需要为测试上下文注入 `JWT_SECRET`；缺失时 Spring 应用上下文无法启动。

也可以从项目根目录运行固定验收脚本：

```powershell
.\docs\verify-phase-one.ps1
```

该脚本依次执行 AI 全量测试、后端 Agent 聚焦测试、后端编译和前端构建，最后输出两账号手工联调清单。它要求 ai/.venv 使用 Python 3.12；若本机遗留 Python 3.14 的旧虚拟环境，删除 ai/.venv 后双击 start-ai.bat 重建。若只需要自动化检查，可使用 .\docs\verify-phase-one.ps1 -SkipManualChecklist。

## 明确不纳入当前建设

- 不建设钱包、充值、提现、余额支付或平台资金托管。
- 不让 Agent 发私聊、创建订单、发布商品/求购/换物，或处理纠纷；一期不提供“确认后由 Agent 执行”的例外。
- 不允许模型直连数据库、支付系统、密钥、完整聊天记录或未脱敏评价。
- 不为 RAG 引入外部资料抓取；知识范围只限平台商品、评价和管理员规则。
