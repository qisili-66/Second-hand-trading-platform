# CampusAgent 智能校园闲置平台

<p align="center">
  <img src="frontend/public/brand-mark.svg" width="88" alt="CampusAgent Logo">
</p>

<p align="center">
  <a href="#技术栈"><img src="https://img.shields.io/badge/Vue-3-42B883?logo=vuedotjs&logoColor=white" alt="Vue 3"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 4"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/FastAPI-0.115-009688?logo=fastapi&logoColor=white" alt="FastAPI"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white" alt="MySQL 8"></a>
</p>

CampusAgent 是一个面向校园场景的智能闲置交易平台，提供商品发布与浏览、订单交易、即时聊天、求购换物、后台治理，以及由 Agent 驱动的交易辅助能力。

## 项目演示

- 本地前台预览：`http://127.0.0.1:5173`
- 本地后台预览：`http://127.0.0.1:5173/admin`

项目 Logo：

<p align="center">
  <img src="frontend/public/brand-mark.svg" width="160" alt="CampusAgent 标识">
</p>

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts、SockJS、STOMP |
| 后端 | Java 17、Spring Boot 4、Spring MVC、Spring WebSocket、JdbcTemplate、MyBatis-Plus、Lombok |
| AI 服务 | Python 3.12、FastAPI、Pydantic 2、LangChain、LangGraph、Agnes OpenAI-compatible API |
| 数据库 | MySQL 8、utf8mb4 |
| 本地运行 | IDEA、VS Code、Windows 批处理脚本、环境变量 |

## 功能特性

- 商品交易：商品发布、草稿保存、图片上传、筛选搜索、详情展示、上下架和软删除。
- 交易闭环：订单创建、卖家接单、取消、完成、评价与信用分展示。
- 实时互动：收藏、留言、站内聊天、系统通知与 WebSocket 实时推送。
- 求购换物：求购发布、以物换物、匹配推荐、联系换物人、取消与完成。
- Agent 能力：只读买家导购、风险提示、实时商品/卖家/订单查询、可审计推荐与知识检索；不执行私聊、下单、发布、支付或纠纷处理。
- 管理后台：数据概览、用户管理、商品管理、分类管理、订单、举报、公告与系统配置。
- 权限控制：普通用户与管理员 JWT 分离，前端路由守卫和后端接口鉴权。

## 快速上手

### 环境依赖

| 工具 | 版本要求 |
| --- | --- |
| JDK | 17 |
| MySQL | 8.x |
| Node.js | `^20.19.0` 或 `>=22.12.0` |
| Python | 3.12 |

### 1. 克隆项目

```bash
git clone https://github.com/qisili-66/Second-hand-trading-platform.git
cd Second-hand-trading-platform
```

### 2. 初始化数据库

创建名为 `second_hand_trade` 的 MySQL 数据库，字符集设为 `utf8mb4`，再执行：

```powershell
cd backend
.\scripts\init-database.ps1
cd ..
```

也可以按顺序手动导入：

```text
backend/sql/schema.sql      # 完整表结构
backend/sql/seed_data.sql   # 基础配置、演示账号和 100 条商品数据
```

### 3. 配置环境变量

复制 `backend/.env.example` 为不提交的 `backend/.env`，并将其中变量配置到 IDEA 的本地运行环境。后端不会提供数据库密码或 JWT 密钥的弱默认值；至少需要设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和长度不少于 32 位的 `JWT_SECRET`。

AI 服务在 `ai/.env` 中配置模型能力。首次启动时可参考 `ai/.env.example` 创建该文件。

### 4. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

后端默认运行在 `http://127.0.0.1:8080`，健康检查：

```powershell
curl.exe http://127.0.0.1:8080/api/health
```

### 5. 启动 AI 服务

```bat
start-ai.bat
```

AI 服务默认运行在 `http://127.0.0.1:8001`。未配置有效的 `EXTERNAL_LLM_API_KEY` 时，服务仍会返回规则兜底结果，便于本地联调。

### 6. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://127.0.0.1:5173`，开发环境已代理：

```text
/api -> http://127.0.0.1:8080
/ws  -> http://127.0.0.1:8080
```

### 演示账号

执行 `backend/sql/seed_data.sql` 后会重置本地演示数据，并创建基础平台配置、演示账号和 100 条商品。该脚本会清空现有业务数据，只应在本地开发或可丢弃的演示数据库中执行。出于安全考虑，README 不公开任何账号密码；请在本地种子脚本或本地环境中自行设置、查看并保管演示凭据。

## 目录结构

```text
.
├── ai/                         # FastAPI + LangChain Agent 服务
│   ├── app/                    # Agent、模型调用、工具与接口
│   └── tests/                  # AI 服务测试
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/          # Controller、Service、Entity、Mapper、Config、DTO
│   ├── src/main/resources/     # application.yml 等配置
│   ├── sql/                    # 建表、种子与演示数据 SQL
│   └── scripts/                # 数据库初始化脚本
├── frontend/                   # Vue 3 + Vite 前端
│   ├── src/views/front/        # 用户前台页面
│   ├── src/views/admin/        # 管理后台页面
│   ├── src/services/           # API、WebSocket 与 Agent 历史封装
│   ├── src/stores/             # Pinia 状态管理
│   └── src/router/             # 路由定义
└── README.md
```

## 配置说明

### 后端环境变量

| 变量 | 说明 |
| --- | --- |
| `DB_URL` | MySQL JDBC 连接地址 |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号与密码 |
| `JWT_SECRET` | JWT 签名密钥，本地也应使用长度不少于 32 位的随机值 |
| `APP_UPLOAD_DIR` | 上传文件存储路径 |
| `AI_SERVICE_BASE_URL` | AI 服务地址，默认 `http://127.0.0.1:8001` |
| `AI_SERVICE_TIMEOUT_SECONDS` | 后端调用 AI 服务的超时秒数 |
| `AI_SERVICE_MAX_CONCURRENT_REQUESTS` | 后端同时代理到 AI 服务的最大请求数，默认 `8` |
| `AI_SERVICE_QUEUE_TIMEOUT_MS` | 后端等待 AI 并发槽的最长毫秒数，默认 `150` |
| `AGENT_SERVICE_TOKEN` | Spring Boot 与 AI 服务的内部工具调用共享密钥，必须设置且不提交 |
| `AGENT_TRACE_RETENTION_DAYS` | Agent Run、步骤与推荐审计记录保留天数，默认 `90` |

### AI 服务环境变量

| 变量 | 说明 |
| --- | --- |
| `EXTERNAL_LLM_API_KEY` | Agnes API Key；未配置有效值时使用规则兜底 |
| `EXTERNAL_LLM_BASE_URL` | Agnes OpenAI-compatible 服务地址 |
| `EXTERNAL_LLM_MODEL` | 模型名称，默认 `agnes-2.0-flash` |
| `LLM_TIMEOUT_SECONDS` | 大模型调用超时秒数 |
| `LLM_MAX_RETRIES` | 单次 Agent 请求最多重试次数，默认 `1`，最大 `2` |
| `LLM_MAX_CONCURRENT_REQUESTS` | AI 服务同时执行的模型调用数，默认 `4` |
| `LLM_FAILURE_THRESHOLD` | 连续失败后打开熔断器的阈值，默认 `3` |
| `LLM_CIRCUIT_RECOVERY_SECONDS` | 熔断器再次尝试探测上游前的等待秒数，默认 `30` |
| `BACKEND_BASE_URL` / `AGENT_SERVICE_TOKEN` | AI 只读工具网关地址与共享服务密钥；AI 服务不再直连数据库 |
| `QDRANT_URL` / `QDRANT_COLLECTION` | 本地 Qdrant 配置 |
| `EMBEDDING_PROVIDER` / `LOCAL_EMBEDDING_MODEL` / `LOCAL_EMBEDDING_DEVICE` | 本地 Embeddings 配置，默认 `local` / `BAAI/bge-small-zh-v1.5` / `cpu` |
| `OTEL_SERVICE_NAME` / `OTEL_EXPORTER_OTLP_ENDPOINT` | 可选的企业 OpenTelemetry OTLP 链路导出配置 |

### 企业 Agent 与知识库

买家 Agent 的价格、在售状态、信用与订单信息均通过 Spring Boot 内部只读工具查询；模型无法直接读取数据库或执行交易写操作。每次运行都会保存可脱敏回放的 Run、Step 和经后端校验的推荐快照。

二期 RAG 使用本地 Qdrant 和 `sentence-transformers` 的 `BAAI/bge-small-zh-v1.5` 向量模型。管理员在“Agent 知识库”中维护平台规则和 FAQ；商品、评价及规则变更进入 Outbox，由 AI 服务执行 `python -m app.knowledge_worker` 进行幂等索引。模型权重首次运行需要下载；不可用时仅使用开发回退向量，不会基于猜测回答。

请勿提交 `.env`、数据库密码、JWT 密钥、AI Key 或支付密钥。

## API 与使用文档

后端接口统一以 `/api` 为前缀，AI 服务接口以 `/agents` 为前缀。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 用户注册 |
| `POST` | `/api/auth/login` | 用户登录 |
| `POST` | `/api/auth/admin/login` | 管理员登录 |
| `GET` | `/api/items` | 商品分页查询 |
| `POST` | `/api/items` | 发布商品 |
| `POST` | `/api/orders` | 创建订单预约 |
| `POST` | `/api/chats` | 创建聊天会话 |
| `POST` | `/api/agent/buyer` | 平台买家 Agent |
| `POST` | `/agents/buyer/runs` | FastAPI 买家 Agent 内部接口 |

WebSocket 入口为 `/ws`。需要登录的接口应携带 JWT 认证信息；详细请求字段可查看对应 Controller、DTO 与前端 `src/services/api.js`。

## 常见问题

### 前端请求显示超时怎么办？

先确认后端健康检查：`curl http://127.0.0.1:8080/api/health`。普通 API 请求超时已配置为 30 秒；首页仅请求商品基础列表，不会自动调用 AI。若仍显示旧的 8 秒超时提示，请重新构建前端并清理浏览器缓存。

### 不配置 AI Key 能运行吗？

可以。AI 服务在未配置有效的 `EXTERNAL_LLM_API_KEY` 时会使用规则兜底结果；配置后可获得大模型增强的推荐与文案生成能力。

### 为什么不能直接开启真实支付？

支付相关接口默认不用于真实收款。启用支付宝或微信支付前，需要完成商户配置、签名验签、订单对账、退款和异常处理等生产级能力。

## 贡献指南

欢迎通过 Issue 提交问题或建议，也欢迎发起 Pull Request。

1. Fork 本仓库并创建功能分支：`git checkout -b feature/your-feature`。
2. 保持改动聚焦，补充必要测试，并执行前端构建或相关测试。
3. 提交信息使用清晰的动词开头，例如 `feat: add wanted post filter`。
4. 在 Pull Request 中说明改动目的、测试方式和可能的兼容性影响。

请不要提交环境文件、账号密码、密钥、构建产物或 IDE 本地配置。

## 开源协议

当前仓库尚未附带 `LICENSE` 文件。代码默认保留所有权利；如需开源分发，请在确定许可证后添加对应的 `LICENSE` 文件。

## 作者与联系

- 作者：张益达
- GitHub：[qisili-66](https://github.com/qisili-66)
- 仓库：[Second-hand-trading-platform](https://github.com/qisili-66/Second-hand-trading-platform)
