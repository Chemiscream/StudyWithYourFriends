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
    public static final String VERSION = "1.7.0";

    /** 每个版本的更新说明，前端按版本展示，最新写在最前。 */
    private static final List<ReleaseNote> NOTES = List.of(
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

    private record ReleaseNote(String version, List<String> notes) {}
}
