# StudyWithYourFriends · 学习记录

> 当前版本 **v1.9.7**

和朋友一起打卡学习的轻量社区网站：记录每天的学习、配图配视频、互相留言打气。

## 功能

- 用户注册 / 登录（token 长期有效，免频繁登录）
- 发布富文本学习记录：emoji 表情、图片（≤5MB）、视频（≤40MB，mp4/webm/mov）
- 记录搜索：一个关键词同时模糊匹配用户名和正文，支持日期区间筛选
- 留言与回复：任何记录下都可留言，支持回复他人留言；留言超过 3 条自动折叠可展开
- 点赞：记录和留言均可点赞/取消（👍），显示点赞数
- 收藏：每条记录可 ☆ 收藏/取消，顶栏 ⭐ 入口查看收藏列表
- 转发 / 引用：🔁 转发任意记录，正文独立编辑，原文以引用卡片展示（原记录被删后仍保留，显示「原记录已删除」）
- 图片放大预览：点击记录富文本中的图片全屏放大（遮罩点击 / Esc / ✕ 关闭）
- @提及与自动补全：留言和记录正文支持 `@用户名` 提及，输入 `@` 弹出用户列表（实时过滤、键盘导航）；管理员账号对普通用户隐藏，无法被提及
- 消息中心（顶栏 🔔）：评论、回复、点赞、@提及、转发都会产生通知，红点显示未读数；点击通知直达记录详情页；支持一键清除；取消点赞/删除源头自动撤回对应通知
- 记录详情页：点击记录进入占满页面的详情（`#record/id`，可刷新直达），从动态进入返回时恢复滚动位置，从消息进入返回时回到顶部
- 词云统计面板：按人统计学习频率、全员学习关键词词云（jieba 分词），面板可折叠
- AI 日报：每天 0 点自动生成前一天的全站学习日报（含当天留言互动），模型离线时每 5 分钟自动排队补生成；首页展示最新日报卡片，支持按天翻页查看历史；只读接口未登录可访问
- AI 问答（登录可用）：默认接本机 LM Studio（OpenAI 兼容，免 Key），可切换任意 OpenAI 兼容 API（DeepSeek、GPT 等）；AI 参考全站学习记录作答，回答逐字流式输出，支持多轮对话（记忆存服务端、每账号最多 10 轮、可清空），多人并发自动排队
- AI 助手（虚拟用户「AI助手」）：在留言中 `@AI助手` 或直接回复它、在记录正文里 `@AI助手` 都会触发异步回复；每次日报生成成功后由 AI 助手发布播报记录；行为受身份与能力边界约束（不冒用用户学习成果、不承诺执行操作）
- 管理员：可删除任意记录与留言（凭据见下文「本地配置」，不入库）
- 设置面板（顶栏 ⚙️）：修改个人信息（用户名 / 密码 / 头像色）、查看全部版本更新日志
- 深浅色主题切换、顶栏 sticky 固定
- 确认弹窗按钮顺序统一：「确定」在左、「取消」在右（Enter=确定、Esc=取消）
- 安全：PBKDF2 密码哈希、参数化查询防 SQL 注入、jsoup 服务端富文本清洗防 XSS、请求体大小限制

## 技术栈

- **后端**：Java 21 · Spring Boot 3.2 · Spring Data JPA · jsoup · jieba-analysis
- **数据库**：MySQL 8（utf8mb4）
- **前端**：Vue 3（组合式 API）+ Vite，无 UI 库 / Pinia / 路由依赖，全局状态用 reactive 单例管理
- **静态资源外置热更**：`spring.web.resources.static-locations=file:./static-frontend/, classpath:/static/`，`npm run build` 同时输出到 `static-frontend/`（外置，优先）和 `src/main/resources/static`（jar 内兜底）；纯前端改动构建后即时生效，无需重启后端
- **AI 助手**：单线程异步队列串行处理触发请求，模型离线静默跳过
- **构建**：Maven（后端）· npm（前端）

## 本地运行

前置：JDK 21+、Maven 3.9+、MySQL 8（创建库 `studylog`，字符集 utf8mb4）。

```bash
# 1. 在项目根目录创建管理员凭据文件（已被 .gitignore 排除）
# admin-credentials.properties 内容：
#   admin.username=你的管理员账号
#   admin.password=你的管理员密码

# 2. 构建
mvn -DskipTests package

# 3. 运行（默认端口 8090，数据库连接见 src/main/resources/application.yml）
java -jar target/studylog.jar
```

访问 `http://localhost:8090`。首次启动会自动建表并初始化管理员账号。

### 便捷脚本

- `start.bat`：先确保 MySQL 在运行，再启动应用
- `stop.bat`：按端口停止应用

## 前端开发与构建

前端源码在 `frontend/`（Vue 3 + Vite）。

```bash
cd frontend
npm install     # 首次或依赖变更时执行
npm run dev     # 本地开发调试：热更新，/api 自动代理到 http://localhost:8090
npm run build   # 产物同时输出到 ../static-frontend/（外置热更）和 ../src/main/resources/static（jar 内兜底）
```

- **纯前端改动**（样式 / 组件 / 文案）：只需 `npm run build`，`static-frontend/` 目录被 Spring Boot 优先加载，刷新页面即生效，**无需重启后端、无需重新打包**。
- **改 Java 代码或配置**：需停掉 8090 端口应用后 `mvn -DskipTests package` 重新打包。
- 打包前必须先停掉 8090 端口的应用，否则 `target/studylog.jar` 被锁定导致 repackage 失败。

## API 概览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/register` `/api/login` | 注册 / 登录 |
| GET | `/api/me` | 当前用户 |
| GET | `/api/users` | 用户列表（登录；用于 @ 补全，不含管理员） |
| GET | `/api/records?username=&from=&to=` | 记录列表（用户/内容联合模糊搜索 + 日期） |
| POST | `/api/records` | 发布记录（body 可选 `quote_id` 转发引用已存在记录） |
| GET | `/api/records/{id}` | 单条记录详情（含全部留言与点赞信息） |
| PUT / DELETE | `/api/records/{id}` | 编辑 / 删除（作者或管理员） |
| POST | `/api/records/{id}/like` | 记录点赞切换 |
| POST | `/api/records/{id}/favorite` | 记录收藏切换（登录） |
| POST | `/api/records/{id}/comments` | 留言（支持 reply_to 回复） |
| DELETE | `/api/comments/{id}` | 删除留言（作者或管理员） |
| POST | `/api/comments/{id}/like` | 留言点赞切换 |
| GET | `/api/favorites` | 我的收藏列表（登录） |
| GET | `/api/notifications` | 消息列表与未读数（登录） |
| POST | `/api/notifications/{id}/read` `/api/notifications/clear` | 单条已读 / 一键清除（登录） |
| PUT | `/api/users/color` | 修改头像颜色 |
| PUT | `/api/users/rename` | 修改用户名（事务内级联更新全部含 username 的表并重签 token） |
| PUT | `/api/users/password` | 修改密码（需验证原密码） |
| GET | `/api/stats` | 词云与统计 |
| POST | `/api/ai/chat` | AI 问答（登录；body 可带 `settings` 覆盖模型提供方） |
| POST | `/api/ai/models` | 拉取模型列表（登录） |
| GET / DELETE | `/api/ai/history` | 查看 / 清空自己的 AI 对话记忆（登录） |
| GET | `/api/ai/status` | AI 队列状态（登录） |
| GET | `/api/version` | 最新版本号与更新日志 |
| GET | `/api/versions` | 全部历史版本更新日志 |
| GET | `/api/daily-reports?page=&size=` | AI 日报列表（分页，从昨天往前；未登录可读） |
| GET | `/api/daily-reports/latest` | 最新一条日报（首页卡片用；未登录可读） |
| GET | `/api/daily-reports/{date}` | 指定日期日报详情（未登录可读） |
| POST | `/api/daily-reports/generate` | 生成指定日期日报（登录；body: `{date}`） |

> AI 模型服务配置见 `application.yml` 的 `app.ai.*`（默认 `http://localhost:1234/v1`，并发/队列/超时/上下文预算均可用环境变量覆盖）。

## License

MIT
