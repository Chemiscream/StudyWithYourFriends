# AGENT.MD · 开发流程说明（给后续开发者/AI 代理）

本项目是「学习记录」网站：Vue 3 前端 + Spring Boot 后端 + MySQL。每次开发请遵循以下流程与约束。

## 1. 项目结构速览

- `frontend/` — Vue 3 组合式 API + Vite（纯 JS，无 UI 库/Pinia/路由；全局状态在 `frontend/src/store.js` 的 reactive 单例）
- `src/main/java/com/studylog/` — 后端：`web/`（Controller）、`model/`（JPA 实体）、`repo/`（Repository）、`ai/`（AI 问答）、`daily/`（AI 日报）、`sec/`（token/密码/HTML 清洗）
- `src/main/resources/static/` — 前端构建产物（勿手改，由 `npm run build` 生成）
- `admin-credentials.properties` — 管理员凭据（本地文件，**永不提交 git**）

## 2. 环境路径（本机）

- JDK：`C:\Files\dev\jdk-21.0.12.1+1`（用前设 `$env:JAVA_HOME`，javaw 不在 PATH 时用全路径 `...\bin\javaw.exe`）
- Maven：`C:\Files\dev\apache-maven-3.9.16\bin\mvn.cmd`（不在 PATH）
- MySQL：root/263656 @ localhost:3306/studylog；客户端在 `C:\Files\dev\mysql-8.0.28-winx64\bin\mysql.exe`

## 3. 构建与部署（每次改代码后必做）

顺序不能乱，缺一不可：

```powershell
# 1. 前端构建（产物直出 src/main/resources/static；改了 frontend/ 才需要）
cd frontend; npm run build; cd ..

# 2. 停掉 8090 端口应用（不停会导致 jar 被锁、repackage 失败）
Get-NetTCPConnection -LocalPort 8090 -State Listen | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { Stop-Process -Id $_ -Force }

# 3. 打包
$env:JAVA_HOME="C:\Files\dev\jdk-21.0.12.1+1"; & "C:\Files\dev\apache-maven-3.9.16\bin\mvn.cmd" -q -DskipTests package

# 4. 启动
Start-Process "C:\Files\dev\jdk-21.0.12.1+1\bin\javaw.exe" -ArgumentList "-jar","target\studylog.jar" -WorkingDirectory "项目根目录"

# 5. 冒烟：确认版本号、无 token 请求 /api/ai/* 应 401
Invoke-WebRequest http://localhost:8090/api/version -UseBasicParsing
```

注意：JPA `ddl-auto:update` 会自动建新表/新列，但**不会放松已有列约束**（如 NOT NULL → NULL 需手动 `ALTER TABLE`）。

## 4. 浏览器验证

用 browser_use 子代理访问 `http://localhost:8090/?_=时间戳`（带参防缓存，必要时 Ctrl+F5）。**验证结束后必须清理测试数据**：测试评论/点赞/通知直接 SQL 删除（注意级联），测试注册用户一并删除。

## 5. 发版（版本更新通知）

只在用户明确要求发版时执行：

1. `VersionController.java`：更新 `VERSION` 常量，并在 `NOTES` 静态列表**最前面**加一条 `ReleaseNote`（所有历史版本保留，勿删）。
2. 重新构建部署（见第 3 节），`GET /api/version` 确认返回新版本。
3. 前端机制：接口只返回最新版本号+说明，前端与 localStorage 的上次已读版本对比，不同则弹更新日志弹窗——因此只要 NOTES 头部条目更新，用户刷新即可看到通知。

## 6. Git 提交规则

- 完成功能并验证通过后即提交；提交信息风格：`type(scope): 中文描述`（如 `feat: ...` / `fix(ui): ...`），正文用 `-` 列出要点。
- `git add` 一律列具体文件名（含 `src/main/resources/static/` 构建产物），**禁止** `git add -A`；`admin-credentials.properties` 已被 .gitignore 排除，历史从未提交，保持如此。
- 只提交，不 push（除非用户明确要求）。

## 7. 代码红线（改相关代码时必须遵守）

- **改名级联**：全库以 username 字符串快照关联（无外键）。`AuthController.rename` 在事务内级联 UPDATE users/records/comments/comments.reply_to/ai_messages/record_likes/comment_likes/notifications(recipient+actor)。**新增任何含 username 列的表，必须同步在该方法加 UPDATE。**
- **token 语义**：`Tokens.currentUser` 统一做签名+过期+用户存在性三重校验；改名后旧 token 全站立即失效（前端 `/api/me` 收到 username:null 自动清理）。
- **AI 安全**：用户提供 base_url 时只用用户自己的 api_key，绝不与服务端 key 混用；所有 `/api/ai/*` 需登录。
- **通知**：评论/回复/点赞产生通知；自己操作自己不通知；取消点赞撤回对应通知；删除记录/留言时级联删其通知与点赞。
- **前端渲染**：自写轻量 Markdown/富文本渲染，先 HTML 转义再转换防 XSS；emoji 面板弹出必须传触发按钮作锚点，插入后手动派发 input 事件同步 v-model。
- 记录正文渲染需隐藏媒体后多余的 `<br>`；新增记录提交成功后清空编辑器。
