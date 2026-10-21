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
    public static final String VERSION = "1.12.0";

    /** 每个版本的更新说明，前端按版本展示，最新写在最前。 */
    private static final List<ReleaseNote> NOTES = List.of(
            new ReleaseNote("1.12.0", List.of(
                    "界面皮肤全面重做：浅色「Persona 3」与深色「Persona 5」两套主题各自拥有独立的视觉语言——P3 的青蓝 HUD、刻度环与取景框，P5 的漫画分镜、斜切贴纸与半调网点。",
                    "手机端底部标签栏重新设计：「动态 / 日报 / 统计」改为带编号的斜切卡片，浅色与深色主题分别适配各自的风格。",
                    "深色主题的界面文案改为更贴合学习场景的「TAKE YOUR NOTES」。",
                    "库莉姆的聊天面板会按手机屏幕高度自适应，顶部不再被截断；聊天头像改为更清晰的脸部特写。",
                    "修复顶栏「消息」按钮在浅色主题下没有斜切、与相邻按钮形状不一致的问题。"
            )),
            new ReleaseNote("1.11.2", List.of(
                    "库莉姆现在知道正在和她聊天的人是谁：她会自然地称呼你的名字，也能把你的问题和你的学习记录对应起来。"
            )),
            new ReleaseNote("1.11.1", List.of(
                    "库莉姆的人设描述中不再提及黑猫伙伴，她会以更自然的普通女生口吻和你聊天。"
            )),
            new ReleaseNote("1.11.0", List.of(
                    "桌面端改为左右双栏：左侧是学习动态，右侧固定显示 AI 日报、学习打卡和学习统计，向下滚动时右侧一直跟随。",
                    "手机端改用底部标签分页：「动态 / 日报 / 统计」一次只看一个模块，日报可以展开阅读完整内容。",
                    "新增「学习打卡」热力图：用颜色深浅展示最近 32 周每天的学习记录数量，并显示连续打卡天数、本周条数。",
                    "提交排行默认只显示前三名，点击「查看更多」可展开全部；AI 助手不再出现在排行里。",
                    "关键词词云重新适配侧栏尺寸，字号随容器大小变化，不再挤压关键词。"
            )),
            new ReleaseNote("1.10.1", List.of(
                    "看板娘「库莉姆」不再以大头照出现：右下角的她默认以全身姿势登场，并在站立、看书、逗猫之间悠闲变换。",
                    "库莉姆的形象图背景已全部抠成透明，浮在页面角落不再带一圈白底方框。",
                    "库莉姆说话更自然了：改用活泼女生的口吻闲聊，不再反复强调自己「不是猫」。",
                    "修复 AI 设置中「服务器AI / 外部 API」切换按钮在手机上右侧留白的问题。"
            )),
            new ReleaseNote("1.10.0", List.of(
                    "新看板娘「库莉姆」入驻右下角：点击即可和她聊天，原 AI 问答面板由她接棒（聊天能力和历史对话完全保留）。",
                    "库莉姆会「自己有事做」：待机时在特写、站立、看书、逗猫等姿势间随机切换；你打字时她会侧耳倾听，回答时认真思考与书写。",
                    "库莉姆会主动打招呼：面板关闭时她偶尔冒出一句鼓励气泡，点气泡即可开聊。",
                    "AI 设置搬家：切换服务器AI / 外部 API、指定模型移至「⚙️ 设置 → AI 设置」。"
            )),
            new ReleaseNote("1.9.7", List.of(
                    "修复：在一个留言框输入 @ 时，其他留言框也会同时弹出用户列表的问题。"
            )),
            new ReleaseNote("1.9.6", List.of(
                    "AI 助手行为修正：不再把你的学习内容说成自己的经历，也不再承诺它做不到的操作。"
            )),
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
