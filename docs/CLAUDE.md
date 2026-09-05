# CLAUDE.md

## 作用

本文件记录当前项目的全局规则、开发规范、行为准则和协作边界。适用于 `D:\project\myproject\Second-hand trading platform`。

## 行为准则

- 每一轮开始前先阅读本项目 `docs/Memory.md`、`docs/CLAUDE.md`、`docs/wiki.md` 与 `docs/learning.md`；完成代码或产品口径修改后同步更新相关文档。
- 当前项目以本地开发与验证为准；允许做必要的功能增强，但每次改动后必须同步验证前端构建、后端编译和 AI 测试。
- 改动前先确认目标模块，前端、后端、AI 服务、数据库、文档各自隔离处理。
- 遇到架构变动、生产风险、支付/资金相关风险、数据库迁移或清库风险时，先向用户暴露风险并等待明确授权。
- 不擅自创建新分支、新 worktree，不改动与任务无关的文件。
- 文档统一收口到 `docs/`；本目录同时保存项目设计、知识与开发记录。

## 代码风格

- 后端：Java 17、Spring Boot 4、Spring MVC、Spring WebSocket、JdbcTemplate、MyBatis-Plus、Lombok。
- 前端：Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts、SockJS/STOMP。
- AI 服务：Python 3.12、FastAPI、Pydantic 2、LangChain、Agnes OpenAI-compatible API；不要使用默认指向 Python 3.14 的 `python` 启动 AI 服务。
- 优先保持现有分层：`controller`、`service`、`entity`、`mapper`、`config`、`dto`。
- 优先复用本项目已有 API 封装、normalizer、service 方法和工具模块；不要为小需求新建大抽象。
- 避免堆砌嵌套 `if-else`；优先用清晰状态流、策略映射、数据库约束或服务方法封装业务规则。
- 用户明确偏好高质量代码而不是长代码；需要灵活使用数据结构和小而深的模块，把复杂度收在少数清晰接口后面。
- 生产代码禁止继续扩大 Mock 依赖；`frontend/src/data/mock.js`、`adminMock.js` 只作为历史/开发辅助。
- Spring Boot 4 使用 Jackson 3，包名为 `tools.jackson.*`，不要回退到 `com.fasterxml.jackson.*`。

## 测试要求

- 后端关键改动后优先执行：`cd backend && .\mvnw.cmd test`；若 PowerShell 下 wrapper 异常，可使用本机 Maven Wrapper 缓存中的 `mvn.cmd`。
- 前端关键改动后优先执行：`cd frontend && npm run build`；涉及格式或静态检查时执行 `npm run lint`。
- AI 服务关键改动后执行：`$env:PYTHONPATH='<项目根目录>\ai'; ai\.venv\Scripts\python.exe -m pytest ai\app\tests`。
- 已知前端构建可能出现 Vite/Rolldown pure annotation 和 chunk size warning；构建成功时通常不阻断交付。
- 涉及数据库结构变更时，同步 `backend/sql/` 脚本和 `docs/wiki.md` 的数据库/API 口径。
- 涉及服务体验时，修改后提醒用户重启对应的本地服务；后端、前端、AI 服务都有旧进程继续运行旧代码的可能。

## 禁止事项

- 禁止提交或写入真实 API key、密码、令牌、私钥、商户密钥。
- 禁止把任何登录密码、数据库密码或 API Key 写入项目文档或代码；真实凭据只保留在 Git 忽略的本地 `.env`。
- 不要在有保留价值的数据库上运行会清空业务数据的初始化或重置脚本，如 `backend/scripts/init-database.ps1`、`backend/sql/seed_data.sql`。
- 支付宝/微信真实支付默认关闭；未完成验签、退款、分账、提现账户、资金台账、对账前，不应贸然开启生产收款。
- 一期 Agent 仅限只读买家导购与确定性问答；禁止 Agent 创建订单、发送私聊、发布商品/求购/换物、处理纠纷或调用支付接口。
- 禁止安装来源不明的 Skill 或依赖；官方列表、仓库来源、用途不清楚时宁可不装。
- 禁止把不同项目经验混写到同一长期记忆文件夹。

## 输出标准

- 默认中文回复。
- 最终回复优先说明：做了什么、结果是什么、验证情况、是否还需要用户决定或重启什么。
- 简洁、直接，不输出工具原始回包。
- 引用项目文件时使用明确路径；需要用户操作时给出可直接运行的命令。
