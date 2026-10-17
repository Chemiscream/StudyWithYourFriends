# StudyWithYourFriends · 学习记录

和朋友一起打卡学习的轻量社区网站：记录每天的学习、配图配视频、互相留言打气。

## 功能

- 用户注册 / 登录（token 长期有效，免频繁登录）
- 发布富文本学习记录：emoji 表情、图片（≤5MB）、视频（≤40MB，mp4/webm/mov）
- 记录搜索：一个关键词同时模糊匹配角色名和正文，支持日期区间筛选
- 留言与回复：任何记录下都可留言，支持回复他人留言
- 词云统计面板：按人统计学习频率、全员学习关键词词云（jieba 分词）
- 管理员：可删除任意记录与留言（凭据见下文「本地配置」，不入库）
- 深浅色主题切换、版本更新日志弹窗
- 安全：PBKDF2 密码哈希、参数化查询防 SQL 注入、jsoup 服务端富文本清洗防 XSS、请求体大小限制

## 技术栈

- **后端**：Java 21 · Spring Boot 3.2 · Spring Data JPA · jsoup · jieba-analysis
- **数据库**：MySQL 8（utf8mb4）
- **前端**：原生 HTML/CSS/JS（无框架，静态资源由 Spring Boot 托管）
- **构建**：Maven

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

## API 概览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/register` `/api/login` | 注册 / 登录 |
| GET | `/api/me` | 当前用户 |
| GET | `/api/records?username=&from=&to=` | 记录列表（角色/内容联合模糊搜索 + 日期） |
| POST | `/api/records` | 发布记录 |
| PUT / DELETE | `/api/records/{id}` | 编辑 / 删除（作者或管理员） |
| POST | `/api/records/{id}/comments` | 留言（支持 reply_to 回复） |
| DELETE | `/api/comments/{id}` | 删除留言（作者或管理员） |
| PUT | `/api/users/color` | 修改头像颜色 |
| GET | `/api/stats` | 词云与统计 |
| GET | `/api/version` | 版本号与更新日志 |

## License

MIT
