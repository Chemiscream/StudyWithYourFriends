package com.studylog.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 版本与更新日志：前端据此判断是否弹出「v x.x.x 更新日志」。 */
@RestController
@RequestMapping("/api")
public class VersionController {

    /** 当前版本号（发版时手工维护，遵循语义化版本）。 */
    public static final String VERSION = "1.9.5";

    /** 每个版本的更新说明，前端按版本展示，最新写在最前。 */
    private static final List<ReleaseNote> NOTES = List.of(
            new ReleaseNote("1.9.5", List.of(
                    "AI 助手知识增强：它现在了解本站全部功能和更新历史，回答不再瞎编。"
            )),
            new ReleaseNote("1.9.4", List.of(
                    "AI 助手现在支持「直接回复」：在留言下点回复 AI 助手（无需 @），它也会回复你。"
            )),
            new ReleaseNote("1.9.3", List.of(
                    "点击记录中的图片可全屏放大查看，点击空白处或按 Esc 关闭。"
            )),
            new ReleaseNote("1.9.2", List.of(
                    "顶栏布局微调：⚙️ 设置按钮移到「退出」左边。"
            )),
            new ReleaseNote("1.9.1", List.of(
                    "✏️ 按钮升级为 ⚙️ 设置面板：集中入口包含「修改个人信息」和「查看更新日志」。",
                    "新增历史更新日志浏览：设置 → 查看更新日志，可查看从 1.0.0 至今每个版本的更新内容。",
                    "AI 助手语境理解增强：回复留言时 AI 能理解「他」「这句话」指的是谁/哪条内容。"
            )),
            new ReleaseNote("1.9.0", List.of(
                    "新增转发功能：点击记录下方 🔁 可引用该记录发布新记录，转发卡片点击直达原记录详情；原作者会收到转发通知。"
            )),
            new ReleaseNote("1.8.0", List.of(
                    "新增 AI 助手虚拟用户：在留言或记录中 @「AI助手」，它会阅读记录和留言后回复你；每次日报发布后它也会发一条播报记录。"
            )),
            new ReleaseNote("1.7.5", List.of(
                    "前端资源支持热更新：调整界面样式等纯前端改动后立即生效，无需重启服务器（后端逻辑改动仍需重启）。"
            )),
            new ReleaseNote("1.7.4", List.of(
                    "确认弹窗按钮顺序调整：「确定」放左边、「取消」放右边，删除/清空等操作确认更顺手。"
            )),
            new ReleaseNote("1.7.3", List.of(
                    "AI 对话具备时间感知：AI 现在知道当前日期和星期，问「今天/昨天学了什么」等时间相关问题时回答更准确。"
            )),
            new ReleaseNote("1.7.2", List.of(
                    "消息和收藏弹窗更紧凑美观：弹窗整体收窄，标题栏与列表间距收紧。"
            )),
            new ReleaseNote("1.7.1", List.of(
                    "新增收藏功能：每条记录右侧 ☆ 一键收藏，顶栏 ⭐ 查看我的收藏列表，点击直达详情页。",
                    "留言和记录正文都支持 @提及：输入 @ 弹出用户列表自动补全（支持输入过滤、↑↓ 键盘选择），被提及的人会在消息中心收到提醒。",
                    "管理员账号对普通用户隐藏，无法被 @ 或收到提及通知。",
                    "所有提示与确认改为站内风格弹窗，不再弹出浏览器原生对话框。",
                    "未登录也可以浏览 AI 日报；点赞和收藏仍需登录。",
                    "顶栏吸顶固定：页面下滑时导航始终可见；点赞图标更换为 👍；优化移动端顶栏布局与回复排版，修复色板在窄屏超出屏幕的问题。"
            )),
            new ReleaseNote("1.7.0", List.of(
                    "记录和留言都支持点赞：点 🤍 变 ❤️ 并显示点赞数，再点一次取消。",
                    "新增消息中心（顶栏 🔔）：有人评论你的记录、回复你的留言、点赞你的记录或留言时，铃铛出现红点；点击消息直达对应记录详情页，支持一键清除全部消息；对方取消点赞时对应消息会自动撤回。",
                    "留言超过 3 条自动折叠，点「查看全部 n 条回复」展开。",
                    "点击记录进入详情页（占满页面，链接可收藏、刷新不丢）：从学习动态进入返回时回到原滚动位置，从消息进入返回时回到页面顶部。",
                    "AI 日报总结当天内容时会参考记录下的留言互动。"
            )),
            new ReleaseNote("1.6.0", List.of(
                    "AI 日报更智能：模型离线时不再生成无摘要的空日报，系统每 5 分钟自动重试；连续多天未生成时按日期从早到晚排队补生成，不会跳过或覆盖。",
                    "AI 日报生成超时延长至 10 分钟，并加互斥锁，避免慢模型生成时被重复请求打断重跑。",
                    "日报列表分页按钮改为「上一天 / 下一天」，并对调翻页方向（上一天看更早，下一天看更新）。",
                    "移除日报列表底部的「补生成」按钮。"
            )),
            new ReleaseNote("1.5.0", List.of(
                    "新增「AI 日报」：每天 0 点自动生成前一天的全站学习日报，AI 用活泼有趣的风格总结大家学了什么、谁最拼、今日热词。",
                    "日报支持分页浏览历史、点击查看当日详情、一键下载为文本文件；还可手动补生成昨天/前天的日报。"
            )),
            new ReleaseNote("1.4.0", List.of(
                    "前端整体重构为 Vue 3：界面与操作习惯保持不变，页面响应更快，后续新功能迭代更轻松。",
                    "登录页并入主应用，旧地址 /login.html 会自动跳转（无需更改书签）。"
            )),
            new ReleaseNote("1.3.4", List.of(
                    "右上角 ✏️ 按钮升级为「修改个人信息」弹窗：支持修改用户名和修改密码。",
                    "新增修改密码功能，需验证原密码，改密后下次登录使用新密码。"
            )),
            new ReleaseNote("1.3.3", List.of(
                    "新增修改用户名功能：登录后点击页面右上角 ✏️ 按钮即可改名，你的所有学习记录、评论和 AI 对话记忆都会同步更新，无需重新登录。"
            )),
            new ReleaseNote("1.3.2", List.of(
                    "AI 回答支持 Markdown 渲染：加粗、斜体、行内代码、代码块、标题、列表、链接都会正常显示。",
                    "设置区「本地 LM Studio」更名为「服务器AI」。"
            )),
            new ReleaseNote("1.3.1", List.of(
                    "AI 回答改为逐字流式输出，边生成边显示，不用再干等完整回答。",
                    "排队进度更准确：流建立即代表轮到你，生成期间显示实时状态。"
            )),
            new ReleaseNote("1.3.0", List.of(
                    "「学习统计」面板移到页面最上方，点击标题即可折叠/展开，折叠状态刷新后保持。",
                    "新增「AI 问答」（登录后可用，在统计面板下方，同样可折叠）：AI 会参考全站学习记录作答，支持多轮追问。",
                    "AI 默认使用本机 LM Studio 正在运行的模型（免 Key），也可切换为任意 OpenAI 兼容 API（如 DeepSeek、GPT），设置保存在你自己的浏览器里。",
                    "多人同时提问自动排队，排队进度实时可见；对话记忆按账号保存在服务器（最多保留最近 10 轮），可随时一键清空。"
            )),
            new ReleaseNote("1.2.1", List.of(
                    "安全改进：管理员凭据改为服务器本地配置文件管理，不再随代码分发。"
            )),
            new ReleaseNote("1.2.0", List.of(
                    "网站有了专属 logo：描边书本 + 上升曲线，寓意「坚持记录，持续成长」。",
                    "logo 已出现在首页标题旁、登录页和浏览器标签页图标上。"
            )),
            new ReleaseNote("1.1.1", List.of(
                    "修复：不先点击编辑器就直接插入图片/视频时，光标消失、内容插错位置的问题。"
            )),
            new ReleaseNote("1.1.0", List.of(
                    "记录支持插入视频（mp4/webm/mov，单个不超过 40MB），与图片一样直接嵌在正文里播放。",
                    "「插入图片」按钮更名为「图片/视频」。",
                    "编辑器中删除视频：按住 Alt 点击视频即可删除（单击仍是播放控制）。"
            )),
            new ReleaseNote("1.0.0", List.of(
                    "后端升级为 Java（Spring Boot）+ MySQL，更稳定易维护；原有账号、密码、记录和留言全部保留，无需重新登录。",
                    "新增管理员账号，管理员可以删除任意记录与留言。",
                    "搜索框升级：一个关键词可同时按角色名和记录内容搜索。",
                    "留言新增「回复」功能，可以回复其他用户的留言。",
                    "新增记录处的 emoji 入口改为表情按钮，与留言处保持一致。",
                    "安全加固：服务端过滤恶意脚本、防止 SQL 注入、限制超大提交。"
            ))
    );

    @GetMapping("/version")
    Map<String, Object> version() {
        ReleaseNote latest = NOTES.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("version", latest.version());
        m.put("notes", latest.notes());
        return m;
    }

    /** 全部历史版本（最新在前），供「查看更新日志」弹窗展示。 */
    @GetMapping("/versions")
    Map<String, Object> versions() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("versions", NOTES.stream()
                .map(n -> Map.of("version", n.version(), "notes", n.notes()))
                .toList());
        return m;
    }

    private record ReleaseNote(String version, List<String> notes) {}

    /** 全部版本更新日志的纯文本（供 AI 助手 system prompt 引用），格式：v1.9.5：条目；条目。 */
    public static String notesText() {
        StringBuilder sb = new StringBuilder();
        for (ReleaseNote n : NOTES) {
            sb.append("v").append(n.version()).append("：").append(String.join("；", n.notes())).append("\n");
        }
        return sb.toString();
    }
}
