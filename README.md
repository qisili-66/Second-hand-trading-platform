# CampusAgent 智能校园闲置平台

![Vue 3](https://img.shields.io/badge/Vue-3-42b883?logo=vue.js&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688?logo=fastapi&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)

CampusAgent 是一个面向校园场景的 Agent 辅助闲置交易平台。项目围绕大学生二手流转、求购、置换和交易治理展开，提供普通用户前台、管理后台、Spring Boot 后端、MySQL 数据库脚本和 FastAPI AI Agent 服务。

平台内置淘货 Agent 和发布 Agent：买家可以用自然语言描述需求，获得商品推荐、风险提示、私聊草稿、求购草稿或换物草稿；卖家可以用一句话生成发布标题、描述、分类、成色、建议价格和置换意向。Agent 只生成建议和草稿，所有发布、私聊、下单等有副作用动作都由用户确认。

## 功能亮点

- 商品交易：发布商品、保存草稿、图片上传、筛选搜索、详情展示、上下架和软删除。
- 交易闭环：订单创建、卖家接单、取消、完成、评价和信用分展示。
- 即时互动：收藏、留言、站内聊天、系统通知和 WebSocket 实时推送。
- 求购置换：求购发布、以物换物、匹配推荐、联系换物人、取消或标记完成。
- AI Agent：淘货推荐、求购草稿、换物草稿、发布草稿、风险提示和本地历史记录。
- 管理后台：数据大盘、用户管理、商品管理、分类管理、订单/纠纷/举报、系统配置和公告管理。
- 权限控制：普通用户和管理员 JWT 分离，前端路由守卫和后端接口鉴权双重兜底。
- 部署友好：支持 Nginx 反代、systemd 管理后端与 AI 服务、环境变量覆盖生产配置。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts、SockJS、STOMP |
| 后端 | Java 17、Spring Boot 4、Spring MVC、Spring WebSocket、JdbcTemplate、MyBatis-Plus、Lombok |
| AI 服务 | Python 3.12、FastAPI、Pydantic 2、LangChain、千问 OpenAI-compatible API |
| 数据库 | MySQL 8、utf8mb4 |
| 部署 | Nginx、systemd、环境变量、MySQL 备份与回滚 |

## 项目结构

```text
.
├── ai/                          # FastAPI + LangChain Agent 服务
├── backend/                     # Spring Boot 后端
│   ├── src/main/java/...         # controller / service / entity / mapper / config / dto
│   ├── src/main/resources        # application.yml 等配置
│   ├── sql                       # 建表、基础数据、演示重置 SQL
│   └── scripts                   # 数据库初始化脚本
├── frontend/                    # Vue 3 + Vite 前端
│   ├── src/views/front           # 用户前台页面
│   ├── src/views/admin           # 管理后台页面
│   ├── src/services              # API、Agent 历史和 WebSocket 封装
│   ├── src/stores                # Pinia 状态
│   └── src/router                # 前端路由
├── doc/                         # 项目规则、长期记忆、复盘和接口口径
├── DEPLOYMENT.md                # 轻量服务器上线部署指南
└── README.md
```

## 快速开始

### 环境要求

| 工具 | 建议版本 |
| --- | --- |
| JDK | 17 |
| MySQL | 8.x |
| Node.js | `^20.19.0` 或 `>=22.12.0` |
| Python | 3.12 |

### 初始化数据库

```powershell
cd backend
.\scripts\init-database.ps1
cd ..
```

脚本会执行基础建表和种子数据。生产环境不要直接运行重置脚本，尤其不要执行 `backend/sql/04_reset_seed_zhangyida_100_items.sql`。

### 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

默认地址：`http://127.0.0.1:8080`

健康检查：

```powershell
curl.exe http://127.0.0.1:8080/api/health
```

### 启动 AI 服务

```powershell
ai\start.bat
```

默认地址：`http://127.0.0.1:8001`

AI 服务使用 Python 3.12。可通过环境变量配置 `QWEN_API_KEY`、`QWEN_BASE_URL`、`QWEN_MODEL` 和 `LLM_TIMEOUT_SECONDS`。

### 启动前端

```powershell
cd frontend
npm install
npm run dev
```

默认地址：`http://127.0.0.1:5173`

Vite 已配置本地代理：

```text
/api -> http://127.0.0.1:8080
/ws  -> http://127.0.0.1:8080
```

## 默认账号

管理员账号只用于后台，普通用户只用于前台交易功能。

```text
后台入口：http://127.0.0.1:5173/admin
账号：admin
密码：admin123456
```

```text
前台入口：http://127.0.0.1:5173/login
账号：张益达 或 ZYD2026001
密码：123456
```

## 常用命令

前端构建：

```powershell
cd frontend
npm run build
```

前端检查：

```powershell
cd frontend
npm run lint
```

后端测试：

```powershell
cd backend
.\mvnw.cmd test
```

AI 测试：

```powershell
$env:PYTHONPATH="${PWD}\ai"
ai\.venv\Scripts\python.exe -m pytest ai\app\tests
```

## 接口与文档

详细接口、数据库口径、业务规则和运维清单见 [doc/wiki.md](./doc/wiki.md)。

轻量服务器上线步骤见 [DEPLOYMENT.md](./DEPLOYMENT.md)。

核心接口入口：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/admin/login`
- `GET /api/items`
- `POST /api/items`
- `POST /api/orders`
- `POST /api/chats`
- `POST /api/agent/buyer`
- `POST /api/agent/seller`
- `POST /agents/buyer`
- `POST /agents/seller`

## 部署概览

推荐生产架构：

```text
Browser
  |
  v
Nginx :80/:443
  |-- static frontend dist
  |-- /api -> Spring Boot :8080
  |-- /ws  -> Spring Boot :8080
                    |
                    |-- MySQL :3306
                    |-- FastAPI AI :8001
```

后端和 AI 服务建议用 systemd 托管，生产配置通过环境变量注入，不要把数据库密码、JWT secret、AI key、支付密钥写入仓库。

## 项目状态

当前项目适合作为简历项目展示：功能闭环完整，包含前台交易、后台治理、实时通信、求购置换和 Agent 辅助流程。真实支付默认关闭，支付回调验签、退款、分账/提现、资金台账和对账仍属于生产级增强项。

## 注意事项

- `frontend/` 是真实前端目录，历史文本里可能出现过 `fronted`。
- AI 服务不要使用默认指向 Python 3.14 的 `python`，请使用 Python 3.12。
- 生产环境不要执行清库或演示重置脚本。
- Agent 当前定位为建议型能力，不自动发布商品、发送私聊、创建订单或创建求购。
- 支付宝/微信支付配置默认关闭，未补齐验签和对账前不建议开启真实收款。
