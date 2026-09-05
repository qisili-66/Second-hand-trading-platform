# wiki.md

## 项目背景

本项目是面向校园场景的二手交易平台，包含普通用户前台、管理后台、Spring Boot 后端、MySQL 数据库脚本、Python AI Agent 服务。当前以本地开发和验证为主，可按需做功能增强。

当前产品方向：大学生校园二手市集，清爽、可信、有轻动效；Web/平板保持市集型宽屏体验，手机端使用独立 App 化导航和信息流体验。

## API 说明

基础路径：Spring Boot 后端为 `/api`；Python AI 服务为 `http://127.0.0.1:8001`。

通用成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [],
    "page": 1,
    "pageSize": 10,
    "total": 20
  }
}
```

错误码：`40001` 参数错误，`40100` 未登录或 JWT 无效，`40300` 权限不足，`40400` 资源不存在，`40900` 数据冲突，`50000` 服务器内部错误。

### 认证与用户

- `POST /api/auth/register`：普通用户注册，真实写入 `users` 和 `user_privacy`。
- `POST /api/auth/login`：普通用户登录，账号支持学号、邮箱、昵称或实名，返回 USER JWT；本地演示用户可用 `张益达/123456` 或 `ZYD2026001/123456`。
- `POST /api/auth/admin/login`：管理员登录，默认演示账号 `admin/admin123456`，返回 ADMIN JWT。
- `GET /api/users/me`：当前用户，需要 USER JWT。
- `PUT /api/users/me`：修改当前用户，目前部分字段仍可继续完善。
- `GET /api/users/{userId}`：公开用户主页资料，不需要 JWT；只返回昵称、头像、校区、院系、信用分展示值等非隐私字段。
- `GET /api/users/{userId}/items`：公开用户在售商品，不需要 JWT；只返回该用户 `ON_SALE` 且未删除的商品。
- `GET /api/users/me/items`：我的发布，按 JWT 用户查询 `items.seller_id`。
- `GET /api/users/me/favorites`：我的收藏。
- `GET /api/users/me/notifications`：我的系统通知。
- `GET /api/users/{userId}/reviews`：用户作为卖家收到的评价。

JWT 规则：除公开接口外，`/api/**` 需要 JWT；`/api/admin/**` 需要 ADMIN JWT；普通受保护接口需要 USER JWT。

### 商品、图片、收藏、留言

- `GET /api/categories`：公开分类列表。
- `GET /api/items`：公开商品列表，支持 `page`、`pageSize`、`keyword`、`categoryId`、`categories`、`conditions`、`campus`、`minPrice`、`maxPrice`、`sort`。
- `GET /api/items/{itemId}`：公开商品详情；草稿、下架、已删除、已售商品不进入公开详情。
- `POST /api/items`：发布商品，需要 USER JWT；真实写入 `items` 和 `item_images`。`status` 可为上架或草稿。
- `PUT /api/items/{itemId}`：商品修改，仍是后续可重点补齐的边界。
- `PATCH /api/items/{itemId}/off-shelf`：卖家下架自己的商品。
- `PATCH /api/items/{itemId}/on-shelf`：卖家重新上架自己的商品。
- `DELETE /api/items/{itemId}`：卖家软删除自己的商品；存在进行中订单时不能删除。
- `POST /api/items/{itemId}/favorite`、`DELETE /api/items/{itemId}/favorite`：收藏/取消收藏。
- `GET /api/items/{itemId}/comments`、`POST /api/items/{itemId}/comments`：留言列表公开，发表留言需要 USER JWT。
- `POST /api/files/images`：上传图片，需要 USER JWT；保存到 `app.upload.dir/images` 并写入 `files`。
- `GET /api/files/images/{storageKey}`：公开读取图片。

### 订单、支付、评价

- `POST /api/orders`：创建订单，需要 USER JWT；买家不能购买自己商品，商品必须 `ON_SALE`。
- `GET /api/orders`、`GET /api/orders/{orderId}`：查询当前用户参与的订单。
- `PATCH /api/orders/{orderId}/accept`：卖家接单。
- `PATCH /api/orders/{orderId}/cancel`：买家或卖家取消。
- `PATCH /api/orders/{orderId}/complete`：完成订单，商品改为 `SOLD`。
- `POST /api/orders/{orderId}/pay`：创建支付单，默认支付配置关闭时会明确返回未配置。
- `POST /api/payments/alipay/notify`：支付宝回调入口；生产必须补验签。
- `POST /api/payments/wechat/notify`：微信回调入口；生产必须补 API v3 验签和资源解密。
- `POST /api/reviews`、`POST /api/orders/{orderId}/reviews`：订单评价；后端根据订单推导被评价卖家，前端不能指定任意目标用户。
- `GET /api/reviews/user/{userId}`、`GET /api/reviews/user/{userId}/stats`：用户评价列表和评分统计。

订单典型流转：`PENDING -> ACCEPTED -> PAYING -> PAID -> COMPLETED`，中途可取消到 `CANCELLED`。

### 聊天与 WebSocket

REST 聊天接口都需要 USER JWT：

- `GET /api/chats`
- `POST /api/chats`
- `GET /api/chats/{chatId}/messages`
- `POST /api/chats/{chatId}/messages`

WebSocket：

- 连接地址：`/ws`
- 协议：SockJS + STOMP
- CONNECT headers 必须携带 `Authorization: Bearer <jwt>`
- 允许订阅：`/user/queue/notifications`、`/user/queue/messages`、`/topic/broadcast`
- 客户端 `SEND` 已禁用，写操作必须走 REST 接口。

### 求购与置换

- `GET /api/purchases`：公开求购列表。
- `POST /api/purchases`：发布求购，需要 USER JWT。
- `PATCH /api/purchases/{purchaseId}/close`：关闭自己的求购。
- `GET /api/purchases/{purchaseId}/matches`：求购匹配推荐。
- `GET /api/exchanges`：公开置换列表。
- `POST /api/exchanges`：发布置换，需要 USER JWT。
- `PATCH /api/exchanges/{exchangeId}/matched`、`PATCH /api/exchanges/{exchangeId}/cancel`：置换状态操作。
- `GET /api/exchanges/{exchangeId}/matches`：置换匹配推荐。
- `/swap` 前端流程：登录用户点击“发布换物”，先从自己的 `ON_SALE` 商品中选择拿来交换的物品，再填写想换物品或目标分类、校区和补充说明；提交后调用 `POST /api/exchanges`，页面展示 `recommendedItems` 或通过 `GET /api/exchanges/{exchangeId}/matches` 继续拉取推荐。用户可查看匹配商品详情，也可对自己的置换单执行取消或标记完成。
- `/swap` 联系口径：非本人发布的置换卡片显示“联系换物人”，前端使用该置换单关联的 `itemId` 调用 `POST /api/chats` 创建真实会话，并自动发送一条置换咨询草稿，覆盖成色、配件、补差价和面交时间。
- 兼容旧路径：`/api/wanted-posts`、`/api/swap-requests`。

匹配推荐：求购按分类、校区、预算、关键词相似度打分；置换按指定目标商品、目标分类、校区、是否支持置换、关键词相似度打分。

### 管理后台

后台接口需要 ADMIN JWT：

- `GET /api/admin/dashboard`：真实数据库聚合，含用户、商品、交易额、活跃用户、待处理项、近 7 日交易额、分类商品占比、校区分布。
- `PATCH /api/admin/password`：管理员修改后台密码，需要校验当前密码，新密码至少 8 位，并更新 `admin_users.password_hash`。
- `GET /api/admin/users`
- `GET /api/admin/items`
- `POST /api/admin/items`：管理员可为指定普通用户新增商品。
- `GET /api/admin/categories`
- `GET /api/admin/orders`
- `GET /api/admin/disputes`
- `GET /api/admin/reports`
- `GET /api/admin/settings`
- `GET /api/admin/notices`
- 商品后台下架、上架、删除会真实更新商品并通知卖家。
- `/admin` 顶部后台通知不再使用静态数字，角标由待处理举报、待处理纠纷、待实名用户和公告草稿实时汇总，点击可跳转对应管理页。
- 公告新增、编辑、发布、删除真实写入 `announcements`；发布公告会通知目标普通用户并广播。
- 用户禁用/启用/实名审核、分类管理、举报处理、纠纷仲裁、系统设置已接入真实接口；后续仍可继续补分页、审计日志和更细的权限配置。

### Agent 接口

Python AI 服务：

- `GET /health`：AI 服务状态、模型名、LLM 是否配置。
- `POST /agents/buyer/runs`：一期买家 Agent 的内部执行入口，由 Spring Boot 调用。
- 一期只处理买家导购和确定性问答：检索在售商品、核验实时状态、读取公开卖家信用、查询本人订单和偏好，并给出推荐。
- Agent 不生成私聊、求购、换物或商品发布草稿；不创建订单，不发送消息，不执行任何交易写操作。

Spring Boot 转发接口：

- `POST /api/agent/buyer/runs`：创建并同步执行一次可审计买家 Agent Run。
- `POST /api/agent/buyer`：兼容别名，内部转向同一个 Run 流程。
- `GET /api/agent/runs`、`GET /api/agent/runs/{runId}`：读取当前用户的历史和脱敏时间线。

Agent 请求体建议：

```json
{
  "message": "我想买一个考研用的 iPad，预算 1500 左右，最好校本部面交"
}
```

前端只展示推荐结果及服务端返回的脱敏时间线。用户如需发布商品、创建订单、发私聊、发布求购或换物，必须在对应业务页面直接操作；这些既有业务接口不属于一期 Agent。

Agent Run、Step 和 Recommendation 由 Spring Boot 持久化，可按登录用户读取；不使用前端 `localStorage` 作为一期 Agent 历史事实源，也不保存或生成 Agent 交易草稿。

Agent 超时口径：前端 `agentApi.buyerRun` 单独使用 30 秒超时，Spring Boot 转发 AI 服务默认 25 秒，Python LLM 调用默认 18 秒；普通 API 仍保持 8 秒超时。
Python LLM 超时后会返回规则 fallback；Spring Boot 转发层会归一常见输入字段为 `message` 并使用 UTF-8 JSON 转发。

### 公开接口

以下接口应允许未登录访问：

- `GET /api/health`
- `GET /api/categories`
- `GET /api/items`
- `GET /api/items/{itemId}`
- `GET /api/items/{itemId}/comments`
- `GET /api/files/images/{storageKey}`
- `GET /api/purchases`
- `GET /api/purchases/{purchaseId}/matches`
- `GET /api/exchanges`
- `GET /api/exchanges/{exchangeId}/matches`
- `GET /api/wanted-posts`
- `GET /api/swap-requests`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/admin/login`
- `POST /api/payments/alipay/notify`
- `POST /api/payments/wechat/notify`

## 业务口径

- 普通用户通过 USER JWT 访问用户接口，管理员通过 ADMIN JWT 访问 `/api/admin/**`。
- 本地演示账号固定为两类：管理员 `admin/admin123456` 只进入数据后台；普通用户 `张益达` 或 `ZYD2026001` / `123456` 只进入用户端交易功能。前端路由阻止普通用户进入 `/admin`，后端后台接口仍以 ADMIN JWT 二次兜底。
- 商品公开列表主要读取 `status = 'ON_SALE' AND deleted = 0` 的商品。
- 商品详情卖家卡片可点击进入 `/users/{sellerId}` 公开个人主页；公开主页展示卖家头像、信用分、在售商品和评价，不暴露手机号、邮箱、QQ、微信等隐私字段。
- 商品状态包含 `ON_SALE`、`RESERVED`、`SOLD`、`REMOVED`、`DRAFT`、`VIOLATION`。
- 删除商品采用软删除；存在进行中订单时不能删除。
- 订单完成后买家可评价卖家；评分 4-5 星信用分 `+5`，3 星 `0`，1-2 星 `-10`，范围限制 `0-200`。
- 评价展示口径：`我的评价` 无评价时平均评分、评价数、信用分均显示 `0`；有评价后信用分展示值限制在 `0-100`，避免把内部累计分直接暴露为超过 100 的数值。
- 评价组件展示口径：商品详情和个人主页中的评分星级统一保留横向排布；移动端允许标题和星级上下排列，但星星本身不拆成竖列。
- 支付宝/微信支付默认关闭；开启真实支付后，资金进入所配置商户号绑定的结算账户，而非数据库。
- 平台代收模式要补资金台账、分账、提现、退款、回调验签和对账。
- 求购/置换已真实写库并支持匹配推荐；详情页和“我的求购/置换”仍可继续完善。
- `/swap` 已完成基础闭环：发布置换、选择自己的在售商品、查看匹配推荐、联系对方、取消和标记完成；后续可继续补置换详情页、双方确认流和评价联动。
- `/swap` 非本人置换卡片已支持直接联系换物人，创建聊天基于置换商品 `itemId`；若缺少关联商品 ID，前端会提示暂时不能联系。
- WebSocket 只做服务端推送，客户端写入动作不能绕过 REST 鉴权。
- 一期 Agent 是只读、可审计的买家导购与确定性问答能力。模型和工具均不能创建订单、发私聊、发布商品/求购/换物、处理纠纷或调用支付。
- Agent 历史记录由 `agent_runs`、`agent_steps` 与 `agent_recommendations` 保存；用户只能读取或清除自己的记录，不影响商品、求购或订单等业务数据。

## 数据库口径

数据库：MySQL 8.x，字符集 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`。

核心脚本：

- `backend/sql/schema.sql`：完整建表脚本，包含求购和换物等全部业务表。
- `backend/sql/seed_data.sql`：完整演示数据脚本，写入基础配置、演示账号和 100 条商品；会清空现有业务与演示数据。
- `backend/scripts/init-database.ps1`：一键初始化脚本，生产禁用或需明确备份授权。

连接配置：

- 默认 host：`127.0.0.1`
- 默认 port：`3306`
- 默认 database：`second_hand_trade`
- 通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 注入连接信息。
- 参考 `backend/.env.example` 配置本地环境，不在仓库中保存真实凭据。

主要表：

- 用户认证：`users`、`user_privacy`、`admin_users`
- 商品：`categories`、`category_tags`、`items`、`item_images`、`favorites`、`item_comments`
- 订单评价：`orders`、`order_status_logs`、`payments`、`reviews`
- 聊天通知：`chats`、`chat_messages`、`notifications`
- 求购置换：`wanted_posts`、`purchases`、`swap_requests`、`exchanges`
- 风控后台：`reports`、`disputes`、`sensitive_words`、`audit_logs`、`announcements`、`system_settings`、`files`

`seed_data.sql` 口径：

- 清空普通用户、商品、图片、订单、聊天、收藏、评价、通知、公告、举报、纠纷等业务数据。
- 重置管理员、6 个基础分类、18 个分类标签、敏感词与系统设置。
- 新增普通用户 `张益达`，账号可用实名/昵称 `张益达` 或学号 `ZYD2026001`，密码 `123456`。
- 插入 100 条商品，均为 `ON_SALE`，5 个校区均匀覆盖，6 个分类全部覆盖。
- 每条商品插入 1 张公开真实图片 URL。

## 前端设计口径

- Vite dev server 默认 `host: 0.0.0.0`，`npm run dev` 终端会显示 Local 和 Network 地址；Network 地址用于同一局域网设备访问，实际连通性取决于网络与 Windows 防火墙。
- 普通用户端和管理员后台分离：普通用户登录后不展示后台功能，访问 `/admin` 会回到前台；管理员登录后默认进入 `/admin`，不作为普通交易用户使用。
- 不直接引入 ReactBits，因为项目是 Vue 3；只用 Vue/CSS 复刻 SplitText、Spotlight、Magnet、Shiny、Aurora 等轻动效思路。
- 首页悬浮 Agent 入口：默认显示 AI 按钮，展开后选择“我要淘货”或“我要发布”，在小聊天面板展示结构化结果。
- Agent 面板手机端贴近底部导航上方，不使用桌面端压缩版小窗口。
- 登录/注册页保持简单表单，不使用上方大横幅或营销式内容。
- 个人中心支持头像修改：用户选择图片后走 `POST /api/files/images` 上传，再用 `PUT /api/users/me` 写入 `avatarUrl`；商品详情和公开个人主页读取该头像。
- 手机端不压缩桌面端设计，使用独立 App 顶栏、底部 Tab、信息流卡片和粘性操作区。
- Web/平板端保持宽屏校园市集布局，平板使用两列/收敛布局。

## 目录结构

```text
.
├── ai/                          # Python 3.12 + FastAPI + LangChain Agent 服务
├── backend/                     # Spring Boot 后端
│   ├── src/main/java/...         # controller/service/entity/mapper/config/dto
│   ├── src/main/resources        # application.yml 等配置
│   ├── sql                       # 建表、基础数据、演示重置 SQL
│   └── scripts                   # 数据库初始化脚本
├── frontend/                    # Vue 3 + Vite 前端
│   ├── src/views/front           # 用户前台页面
│   ├── src/views/admin           # 管理后台页面
│   ├── src/services              # API 与 WebSocket 封装
│   ├── src/stores                # Pinia 状态
│   └── src/router                # 路由
└── docs/                        # 项目设计、知识与开发记录
```

## 常见术语

- 普通用户：校园二手交易买家/卖家。
- 管理员：后台管理账号，默认开发演示账号为 `admin/admin123456`，只用于数据后台；生产不可复用默认密码。
- 求购：用户发布想买的需求，由系统按分类、校区、预算和关键词推荐商品。
- 置换：用户发布交换需求，由系统按目标商品、分类、校区、关键词和是否支持置换推荐商品。
- 支付闭环：支付下单、回调验签、状态同步、退款、分账/提现、资金台账、对账的完整链路。
- 买家 Agent：面向买家的只读、可审计导购与确定性问答能力；通过工具查询真实商品、卖家信用、本人订单和偏好后给出推荐。
- 确定性问答：价格、在售状态、卖家信用和本人订单状态必须由 Spring 只读工具取数，不能由模型猜测。
- 公开接口：不需要 JWT 的接口，常用于首页、列表、详情、图片、登录注册和支付回调；一期买家 Agent 必须使用普通用户 JWT。
