# 校园二手交易平台

![Vue 3](https://img.shields.io/badge/Vue-3-42b883?logo=vue.js&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)

一个面向校园场景的二手交易平台，包含 Vue 3 前端、Spring Boot 后端、MySQL 数据库脚本和管理后台。项目支持商品发布、筛选搜索、收藏留言、订单交易、实时聊天、求购置换、评价信用分、系统通知和后台管理等完整业务流程。

> 说明：仓库中的前端目录名为 `fronted`，以下命令均按当前项目实际目录编写。

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [默认账号](#默认账号)
- [常用命令](#常用命令)
- [接口与权限](#接口与权限)
- [数据库说明](#数据库说明)
- [实时推送](#实时推送)
- [支付说明](#支付说明)
- [发布前注意](#发布前注意)

## 功能概览

- 用户认证：普通用户注册登录、管理员登录、JWT 鉴权、接口权限拦截。
- 商品交易：商品发布、草稿保存、图片上传、筛选分页、详情展示、上下架、软删除。
- 互动能力：收藏、留言、立即咨询、订单创建、接单、取消、完成、评价。
- 实时通信：基于 SockJS + STOMP 的聊天消息、订单通知、系统通知和公告广播。
- 求购置换：求购发布、置换发布、列表查询和按分类/校区/预算/关键词的匹配推荐。
- 信用体系：订单完成后买家可评价卖家，评价结果联动用户信用分。
- 后台管理：数据大盘、用户管理、商品管理、分类管理、订单/纠纷/举报、系统配置、公告管理。
- 支付预留：已提供支付宝、微信支付配置入口和下单流程，默认关闭真实支付。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts、SockJS、STOMP |
| 后端 | Java 17、Spring Boot 4、Spring MVC、Spring WebSocket、JdbcTemplate、MyBatis-Plus、Lombok |
| 数据库 | MySQL 8、utf8mb4 |
| 文档 | `API.md`、`DATABASE.md`、`WORK.md` |

## 项目结构

```text
.
├── backend/                         # Spring Boot 后端
│   ├── src/main/java/.../controller # REST 控制器
│   ├── src/main/java/.../service    # 业务服务
│   ├── src/main/java/.../entity     # 数据实体
│   ├── src/main/resources           # 后端配置
│   ├── sql                          # 建表和初始化脚本
│   └── scripts                      # 数据库初始化脚本
├── fronted/                         # Vue 前端
│   ├── src/views/front              # 用户端页面
│   ├── src/views/admin              # 管理后台页面
│   ├── src/services                 # API 与 WebSocket 封装
│   ├── src/stores                   # Pinia 状态
│   └── src/router                   # 前端路由
├── API.md                           # 接口说明
├── DATABASE.md                      # 数据库设计说明
└── WORK.md                          # 工作记录与后续建议
```

## 快速开始

### 1. 环境要求

| 工具 | 建议版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | 运行后端服务 |
| MySQL | 8.x | 存储业务数据 |
| Node.js | `^20.19.0` 或 `>=22.12.0` | 运行前端项目 |
| npm | 随 Node 安装 | 安装前端依赖 |
| Maven | 可选 | Maven Wrapper 不可用时使用 |

默认数据库配置位于 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/second_hand_trade
    username: root
    password: root
```

如果本机 MySQL 账号不是 `root/root`，需要同步修改：

- `backend/src/main/resources/application.yml`
- `backend/scripts/init-database.ps1` 中的 `-uroot -proot`

### 2. 初始化数据库

启动 MySQL 后，在项目根目录执行：

```powershell
cd backend
.\scripts\init-database.ps1
cd ..
```

脚本会执行：

```text
backend/sql/01_create_tables.sql
backend/sql/02_seed_data.sql
```

初始化后默认保留管理员账号、商品分类、敏感词和系统配置；普通用户、商品、订单、聊天、通知等业务数据会被清空，便于重新录入测试数据。

### 3. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

后端默认地址：

```text
http://127.0.0.1:8080
```

健康检查：

```powershell
curl.exe http://127.0.0.1:8080/api/health
```

### 4. 启动前端

```powershell
cd fronted
npm install
npm run dev
```

前端默认地址：

```text
http://127.0.0.1:5173
```

Vite 已配置本地代理：

```text
/api -> http://127.0.0.1:8080
/ws  -> http://127.0.0.1:8080
```

## 默认账号

管理员账号仅用于本地开发和演示：

```text
入口：http://127.0.0.1:5173/admin
账号：admin
密码：admin123456
```

普通用户默认没有预置数据，请通过 `/register` 注册后登录。

## 常用命令

后端测试：

```powershell
cd backend
.\mvnw.cmd test
```

前端构建：

```powershell
cd fronted
npm run build
```

前端代码检查：

```powershell
cd fronted
npm run lint
```

数据库数据量快速检查：

```powershell
mysql -uroot -proot --default-character-set=utf8mb4 -D second_hand_trade -e "SELECT COUNT(*) AS users FROM users; SELECT COUNT(*) AS items FROM items; SELECT COUNT(*) AS orders FROM orders; SELECT COUNT(*) AS chats FROM chats;"
```

## 接口与权限

详细接口见 [API.md](./API.md)。

公开接口示例：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/admin/login`
- `GET /api/health`
- `GET /api/categories`
- `GET /api/items`
- `GET /api/items/{itemId}`
- `GET /api/files/images/{storageKey}`
- `GET /api/purchases`
- `GET /api/exchanges`

登录成功后后端返回 `accessToken`，前端保存到 `localStorage`，后续请求通过请求头携带：

```http
Authorization: Bearer <jwt>
```

普通用户接口需要 `USER` JWT，后台接口需要 `ADMIN` JWT。未登录返回 401，权限不匹配返回 403。

## 数据库说明

详细表结构见 [DATABASE.md](./DATABASE.md)。

核心数据表包括：

- 用户与权限：`users`、`user_privacy`、`admin_users`
- 商品与互动：`categories`、`category_tags`、`items`、`item_images`、`favorites`、`item_comments`
- 交易流程：`orders`、`order_status_logs`、`payments`、`reviews`
- 即时通信：`chats`、`chat_messages`、`notifications`
- 求购置换：`wanted_posts`、`purchases`、`exchanges`、`swap_requests`
- 后台治理：`reports`、`disputes`、`sensitive_words`、`announcements`、`system_settings`、`audit_logs`
- 文件存储：`files`

商品图片默认上传到后端本地目录：

```yaml
app:
  upload:
    dir: uploads
```

该目录已在 `.gitignore` 中排除。

## 实时推送

前端 `fronted/src/services/websocket.js` 使用 SockJS + STOMP 连接后端 `/ws`。普通用户登录后订阅：

- `/user/queue/notifications`
- `/user/queue/messages`
- `/topic/broadcast`

后端会在订单状态变化、聊天消息、系统通知、公告发布等场景写入数据库并推送消息。客户端 WebSocket `SEND` 已禁用，订单、聊天和通知写入仍通过 REST 接口完成。

## 支付说明

支付配置位于 `backend/src/main/resources/application.yml`：

```yaml
app:
  payment:
    alipay:
      enabled: false
    wechat:
      enabled: false
```

默认关闭真实支付，不会向支付宝或微信发起真实下单，也不会产生真实收款二维码。配置正式商户参数前，请不要在生产环境打开支付开关。

## 发布前注意

- 修改数据库账号、JWT 密钥、支付商户参数等敏感配置，不要直接使用本地默认值。
- 按生产环境重新配置 CORS、文件上传目录、日志、安全策略和 HTTPS。
- `backend/scripts/init-database.ps1` 会清空业务数据，生产环境不要直接执行。
- 推送公开仓库前建议再次检查 `application.yml`、SQL 脚本和提交记录，避免包含真实密钥或个人隐私数据。1
