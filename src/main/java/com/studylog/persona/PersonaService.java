package com.studylog.persona;

import com.studylog.ai.AiAssistantService;
import com.studylog.model.Comment;
import com.studylog.model.Record;
import com.studylog.model.RecordFavorite;
import com.studylog.model.RecordLike;
import com.studylog.model.User;
import com.studylog.repo.CommentRepository;
import com.studylog.repo.RecordFavoriteRepository;
import com.studylog.repo.RecordLikeRepository;
import com.studylog.repo.RecordRepository;
import com.studylog.repo.UserRepository;
import com.studylog.web.BizException;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 五维人格指数：按站内行为累积 knowledge / courage / dexterity / kindness / charm 点数。
 * 规则要点：只增不减、来源去重、自己的行为不计分、AI 助手虚拟用户完全不参与。
 * 每日额度按上海时区自然日计算，写入前用当日已得点数限制本次加分。
 */
@Service
public class PersonaService {

    private static final Logger log = LoggerFactory.getLogger(PersonaService.class);

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    /** 五个维度的累计等级阈值（5 级）。拉长升级周期：活跃用户约两个月走满，休闲用户要更久。 */
    private static final int[] THRESHOLDS = {0, 40, 120, 300, 700};

    private static final int CAP_KNOWLEDGE = 12;
    private static final int CAP_COURAGE = 10;
    private static final int CAP_DEXTERITY = 12;
    private static final int CAP_GIVE_LIKE = 5;
    private static final int CAP_COMMENT_KINDNESS = 6;
    private static final int CAP_COMMENT_RECEIVED = 6;
    private static final int CAP_REPLY_RECEIVED = 6;

    private static final String[] COURAGE_SOURCES = {"first_record", "media", "return"};
    private static final String[] DEXTERITY_SOURCES = {"checkin", "streak", "mention", "quote"};
    private static final String[] COMMENT_KINDNESS_SOURCES = {"comment", "reply"};

    /** @提及解析：与通知保持一致（@用户名，截到空白或常见中英文标点，最长 32 字符）。 */
    private static final Pattern MENTION_RE =
            Pattern.compile("@([^\\s@，。！？、,.!?~；;：:\"'“”()（）\\[\\]【】]{1,32})");
    /** 先整段去掉图片/视频标签，避免内嵌 base64 被当作文本。 */
    private static final Pattern MEDIA_TAG_RE = Pattern.compile("<(?:img|video)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_RE = Pattern.compile("<[^>]*>");

    private static final List<StatDef> STATS = List.of(
            new StatDef("knowledge", "知识",
                    new String[]{"才疏学浅", "勤奋好学", "知识分子", "博学多闻", "百科字典"}),
            new StatDef("courage", "勇气",
                    new String[]{"胆小多疑", "独当一面", "不屈不挠", "大胆无畏", "狮王之心"}),
            new StatDef("dexterity", "灵巧",
                    new String[]{"笨手笨脚", "普普通通", "专业职人", "大师手艺", "点石成金"}),
            new StatDef("kindness", "体贴",
                    new String[]{"不甚关心", "倾听高手", "善解人意", "乐于助人", "圣母慈爱"}),
            new StatDef("charm", "魅力",
                    new String[]{"一般程度", "令人在意", "潜力十足", "魅力洋溢", "超凡脱俗"}));

    private final StatEventRepository events;
    private final RecordRepository records;
    private final RecordLikeRepository likes;
    private final RecordFavoriteRepository favorites;
    private final CommentRepository comments;
    private final UserRepository users;
    private final EntityManager em;

    public PersonaService(StatEventRepository events, RecordRepository records,
                          RecordLikeRepository likes, RecordFavoriteRepository favorites,
                          CommentRepository comments, UserRepository users, EntityManager em) {
        this.events = events;
        this.records = records;
        this.likes = likes;
        this.favorites = favorites;
        this.comments = comments;
        this.users = users;
        this.em = em;
    }

    // ---------- 对外计分入口（实时行为） ----------

    /** 记录创建成功后计分（编辑、删除不计分也不扣分），返回本人本次加分与升级（即时 POINT UP / RANK UP）。 */
    public Map<String, Object> onRecordCreated(Record r) {
        Map<String, Integer> gains = new LinkedHashMap<>();
        if (r == null) return result(gains, List.of());
        Map<String, Integer> before = totalsOf(r.getUsername());
        awardRecord(r, r.getCreatedAt(), gains);
        return result(gains, rankUps(before, totalsOf(r.getUsername())));
    }

    /** 新点赞：作者魅力 +2，点赞者体贴 +1（取消点赞不扣分）。返回点赞者本次的分与升级（作者的分留作未读）。 */
    public Map<String, Object> onLike(String owner, String actor, Long recordId) {
        Map<String, Integer> gains = new LinkedHashMap<>();
        Map<String, Integer> before = totalsOf(actor);
        awardLike(owner, actor, recordId, now(), gains);
        return result(gains, rankUps(before, totalsOf(actor)));
    }

    /** 新收藏：作者魅力 +3（取消收藏不扣分）。收藏者本人不加分，原作者的加分留作未读。 */
    public void onFavorite(String owner, String actor, Long recordId) {
        awardFavorite(owner, actor, recordId, now());
    }

    /** 新留言：留言者体贴、记录作者与被回复者魅力。返回留言者本次的分与升级（作者的加分留作未读）。 */
    public Map<String, Object> onComment(Record record, Comment c) {
        Map<String, Integer> gains = new LinkedHashMap<>();
        if (record == null || c == null) return result(gains, List.of());
        String commenter = c.getUsername();
        Map<String, Integer> before = commenter == null ? Map.of() : totalsOf(commenter);
        awardComment(record.getUsername(), c, c.getCreatedAt(), gains);
        return result(gains, commenter == null ? List.of() : rankUps(before, totalsOf(commenter)));
    }

    /** 登录后待播放的未读加分：把别人点赞/收藏/留言带来的提升按维度合并成一份，并附带跨过的等级。 */
    public Map<String, Object> pending(String username) {
        Map<String, Integer> byStat = new HashMap<>();
        for (Object[] row : events.unseenGains(username)) {
            byStat.put((String) row[0], ((Number) row[1]).intValue());
        }
        List<Map<String, Object>> gains = new ArrayList<>();
        int total = 0;
        for (StatDef def : STATS) {
            int v = byStat.getOrDefault(def.key(), 0);
            if (v <= 0) continue;
            total += v;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", def.key());
            m.put("name", def.name());
            m.put("delta", v);
            gains.add(m);
        }
        // 升级判定：当前累计 - 未读加分 = 打开站点前的旧等级
        Map<String, Integer> nowTotals = totalsOf(username);
        Map<String, Integer> beforeTotals = new HashMap<>();
        for (StatDef def : STATS) {
            beforeTotals.put(def.key(), nowTotals.getOrDefault(def.key(), 0) - byStat.getOrDefault(def.key(), 0));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("total", total);
        out.put("gains", gains);
        out.put("rank_ups", rankUps(beforeTotals, nowTotals));
        return out;
    }

    /** 播放完未读加分动画后标记已读，避免下次登录重复播放。 */
    @org.springframework.transaction.annotation.Transactional
    public void markPendingSeen(String username) {
        events.markSeen(username);
    }

    // ---------- 快照 ----------

    /** 本人快照：管理员登录后仍可查看自己的档案（is_admin=true）。AI 助手档案忽略。 */
    public Map<String, Object> snapshot(String username) {
        User u = users.findByUsername(username)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (isIgnored(username)) {
            throw new BizException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return snapshotOf(u);
    }

    /** 公开快照：管理员与 AI 助手对普通用户隐藏，统一按用户不存在处理。 */
    public Map<String, Object> publicSnapshot(String username) {
        User u = users.findByUsername(username)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (u.isAdmin() || isIgnored(username)) {
            throw new BizException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return snapshotOf(u);
    }

    private Map<String, Object> snapshotOf(User u) {
        String username = u.getUsername();

        Map<String, Integer> totals = new HashMap<>();
        for (Object[] row : events.totals(username)) {
            totals.put((String) row[0], ((Number) row[1]).intValue());
        }

        long totalPoints = 0;
        List<Map<String, Object>> stats = new ArrayList<>();
        for (StatDef def : STATS) {
            int value = Math.max(0, totals.getOrDefault(def.key(), 0));
            totalPoints += value;
            stats.add(statMap(def, value));
        }

        // 上海日期 -> 当天记录数（32 周热力图只返回有记录的日期）
        Set<LocalDate> daySet = new HashSet<>();
        Map<String, Object> days = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> dayRows = em.createNativeQuery(
                        "SELECT DATE(created_at + INTERVAL 8 HOUR) AS d, COUNT(*) FROM records " +
                                "WHERE username = :u GROUP BY d ORDER BY d")
                .setParameter("u", username)
                .getResultList();
        for (Object[] row : dayRows) {
            LocalDate d = toLocalDate(row[0]);
            daySet.add(d);
            days.put(d.toString(), ((Number) row[1]).longValue());
        }

        // 连续打卡：今天有记录从今天往前数，否则从昨天往前数
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate cursor = daySet.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (daySet.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("records", countNative("SELECT COUNT(*) FROM records WHERE username = :u", username));
        summary.put("likes_received", countNative(
                "SELECT COUNT(*) FROM record_likes l JOIN records r ON r.id = l.record_id WHERE r.username = :u", username));
        summary.put("favorites_received", countNative(
                "SELECT COUNT(*) FROM record_favorites f JOIN records r ON r.id = f.record_id WHERE r.username = :u", username));
        summary.put("comments_received", countNative(
                "SELECT COUNT(*) FROM comments c JOIN records r ON r.id = c.record_id WHERE r.username = :u", username));
        summary.put("quotes_received", countNative(
                "SELECT COUNT(*) FROM records r2 JOIN records r1 ON r2.quoted_record_id = r1.id WHERE r1.username = :u", username));
        summary.put("likes_given", countNative(
                "SELECT COUNT(*) FROM record_likes WHERE username = :u", username));
        summary.put("comments_given", countNative(
                "SELECT COUNT(*) FROM comments WHERE username = :u", username));
        summary.put("active_days", (long) daySet.size());
        summary.put("streak", (long) streak);
        summary.put("total_points", totalPoints);

        List<Map<String, Object>> recent = new ArrayList<>();
        for (Record r : records.findTop5ByUsernameOrderByCreatedAtDesc(username)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("preview", preview(r.getContent()));
            m.put("created_at", r.getCreatedAt());
            recent.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("username", username);
        out.put("color", u.getColor());
        out.put("is_admin", u.isAdmin());
        out.put("joined_at", u.getCreatedAt());
        out.put("stats", stats);
        out.put("summary", summary);
        out.put("days", days);
        out.put("recent", recent);
        return out;
    }

    // ---------- 记录加分 ----------

    /** 记录创建计分；backfill 时传入记录原始 createdAt，因此每日额度也按原始日期计算。 */
    private void awardRecord(Record r, OffsetDateTime at, Map<String, Integer> gains) {
        String u = r.getUsername();
        if (isIgnored(u) || at == null || r.getId() == null) return;

        LocalDate day = shanghaiDay(at);
        long epochDay = day.toEpochDay();
        String content = r.getContent() == null ? "" : r.getContent();
        String plain = plainText(content);

        // 勇气：史上第一条记录（终身一次；用固定 sourceId + 本人做去重，删光记录重发也不会重复拿）
        if (!events.existsByUsernameAndSourceType(u, "first_record")
                && records.countByUsernameAndIdLessThan(u, r.getId()) == 0) {
            addGain(gains, "courage",
                    award(u, "courage", 8, "first_record", 0L, u, at, CAP_COURAGE, true, COURAGE_SOURCES));
        }

        // 知识：正文纯文本 >= 6 字（门槛放低，短记录也能拿到正反馈），当天第一条 +5，之后每条 +2
        if (plain.length() >= 6) {
            int used = dailyUsed(u, "knowledge", at, new String[]{"record"});
            addGain(gains, "knowledge",
                    award(u, "knowledge", used == 0 ? 5 : 2, "record", r.getId(), "", at,
                            CAP_KNOWLEDGE, true, new String[]{"record"}));
        }

        // 勇气：媒体记录每天一次（按日来源按行为人区分用户，避免不同用户同一 epochDay 冲突）
        if (MEDIA_TAG_RE.matcher(content).find()) {
            addGain(gains, "courage",
                    award(u, "courage", 3, "media", epochDay, u, at, CAP_COURAGE, true, COURAGE_SOURCES));
        }

        // 勇气：距上一条记录 >= 3 天，每天一次（按自增 id 找上一条）
        List<Record> prev = records.findPrevious(u, r.getId(), PageRequest.of(0, 1));
        if (!prev.isEmpty()) {
            LocalDate prevDay = shanghaiDay(prev.get(0).getCreatedAt());
            if (day.toEpochDay() - prevDay.toEpochDay() >= 3) {
                addGain(gains, "courage",
                        award(u, "courage", 5, "return", epochDay, u, at, CAP_COURAGE, true, COURAGE_SOURCES));
            }
        }

        // 灵巧：每日首次打卡
        addGain(gains, "dexterity",
                award(u, "dexterity", 2, "checkin", epochDay, u, at, CAP_DEXTERITY, true, DEXTERITY_SOURCES));

        // 灵巧：连续打卡正好到 3/7/14/30 天
        int streak = streakOn(u, day);
        int streakBonus = switch (streak) {
            case 3 -> 5;
            case 7 -> 10;
            case 14 -> 15;
            case 30 -> 25;
            default -> 0;
        };
        if (streakBonus > 0) {
            addGain(gains, "dexterity",
                    award(u, "dexterity", streakBonus, "streak", epochDay, u, at, CAP_DEXTERITY, true, DEXTERITY_SOURCES));
        }

        // 灵巧：@ 真实存在的非自己用户
        if (hasMention(plain, u)) {
            addGain(gains, "dexterity",
                    award(u, "dexterity", 2, "mention", epochDay, u, at, CAP_DEXTERITY, true, DEXTERITY_SOURCES));
        }

        // 灵巧：转发记录
        if (r.getQuotedRecordId() != null) {
            addGain(gains, "dexterity",
                    award(u, "dexterity", 2, "quote", epochDay, u, at, CAP_DEXTERITY, true, DEXTERITY_SOURCES));
        }
    }

    private void awardLike(String owner, String actor, Long recordId, OffsetDateTime at, Map<String, Integer> gains) {
        if (recordId == null || isIgnored(actor) || owner == null || owner.equals(actor)) return;
        // 作者魅力：同一人同一记录只计一次（唯一约束兜底）；作者是 AI 时不计；本人不在线看不到，保持未读
        if (!isIgnored(owner)) {
            award(owner, "charm", 2, "like", recordId, actor, at, 0, false);
        }
        // 点赞者体贴：每日上限 5，点赞者本人当场看到动画
        addGain(gains, "kindness",
                award(actor, "kindness", 1, "give_like", recordId, actor, at, CAP_GIVE_LIKE, true, new String[]{"give_like"}));
    }

    private void awardFavorite(String owner, String actor, Long recordId, OffsetDateTime at) {
        if (owner == null || recordId == null || isIgnored(owner) || isIgnored(actor) || owner.equals(actor)) return;
        // 收藏者本人不加分；作者的魅力提升留作未读，等本人下次打开站点合并播放
        award(owner, "charm", 3, "favorite", recordId, actor, at, 0, false);
    }

    private void awardComment(String owner, Comment c, OffsetDateTime at, Map<String, Integer> gains) {
        if (c == null || at == null) return;
        String commenter = c.getUsername();
        if (isIgnored(commenter)) return;

        // 留言者体贴 +3，回复再 +2，共用每日 6 点额度（留言者本人即时看到）
        addGain(gains, "kindness",
                award(commenter, "kindness", 3, "comment", c.getId(), "", at,
                        CAP_COMMENT_KINDNESS, true, COMMENT_KINDNESS_SOURCES));
        String replyTo = c.getReplyTo();
        if (replyTo != null && !replyTo.isBlank() && !replyTo.equals(commenter)) {
            addGain(gains, "kindness",
                    award(commenter, "kindness", 2, "reply", c.getId(), "", at,
                            CAP_COMMENT_KINDNESS, true, COMMENT_KINDNESS_SOURCES));
        }

        if (owner == null || isIgnored(owner)) return;
        // 记录作者魅力 +2（作者自己留言跳过），作者的提升留作未读
        if (!owner.equals(commenter)) {
            award(owner, "charm", 2, "comment_received", c.getId(), "", at,
                    CAP_COMMENT_RECEIVED, false, new String[]{"comment_received"});
        }
        // 被回复者魅力 +2（是留言者自己或就是记录作者时跳过，避免同一留言重复计分），留作未读
        if (replyTo != null && !replyTo.isBlank() && !replyTo.equals(commenter) && !replyTo.equals(owner)) {
            award(replyTo, "charm", 2, "reply_received", c.getId(), "", at,
                    CAP_REPLY_RECEIVED, false, new String[]{"reply_received"});
        }
    }

    private static void addGain(Map<String, Integer> gains, String stat, int delta) {
        if (delta <= 0) return;
        gains.merge(stat, delta, Integer::sum);
    }

    /**
     * 写入一条计分事件。
     * 先做来源去重，再按每日剩余额度裁剪；额度为 0 则不写。唯一约束冲突时静默忽略（并发重复触发）。
     */
    private int award(String username, String stat, int delta, String sourceType, long sourceId,
                      String actor, OffsetDateTime at, int dailyCap, boolean seen, String... capSources) {
        if (isIgnored(username) || delta <= 0 || at == null) return 0;
        String who = actor == null ? "" : actor;
        if (events.existsBySourceTypeAndSourceIdAndStatAndActor(sourceType, sourceId, stat, who)) return 0;

        int give = delta;
        if (dailyCap > 0) {
            int used = dailyUsed(username, stat, at, capSources);
            int remain = dailyCap - used;
            if (remain <= 0) return 0;
            give = Math.min(give, remain);
        }

        StatEvent e = new StatEvent();
        e.setUsername(username);
        e.setStat(stat);
        e.setDelta(give);
        e.setSourceType(sourceType);
        e.setSourceId(sourceId);
        e.setActor(who);
        e.setSeen(seen);
        e.setCreatedAt(at);
        try {
            events.saveAndFlush(e);
            return give;
        } catch (DataIntegrityViolationException ex) {
            // 唯一约束防重复：并发下两条相同来源只会成功一条
            return 0;
        }
    }

    /** 某维度在事件当天、指定来源分组内已获得的点数。 */
    private int dailyUsed(String username, String stat, OffsetDateTime at, String... capSources) {
        LocalDate day = shanghaiDay(at);
        OffsetDateTime from = startOfDay(day);
        OffsetDateTime to = startOfDay(day.plusDays(1));
        Set<String> filter = new HashSet<>(Arrays.asList(capSources));
        int sum = 0;
        for (Object[] row : events.dailyBySource(username, stat, from, to)) {
            if (filter.contains((String) row[0])) sum += ((Number) row[1]).intValue();
        }
        return sum;
    }

    /** 截至指定上海日期（含）的连续打卡天数；该日期没有记录时从昨天往前数。 */
    private int streakOn(String username, LocalDate day) {
        Set<LocalDate> days = new HashSet<>();
        for (Object o : records.findDaysBefore(username, startOfDay(day.plusDays(1)))) {
            days.add(toLocalDate(o));
        }
        LocalDate cursor = days.contains(day) ? day : day.minusDays(1);
        int n = 0;
        while (days.contains(cursor)) {
            n++;
            cursor = cursor.minusDays(1);
        }
        return n;
    }

    // ---------- 历史回填 ----------

    /**
     * 应用启动后，若 stat_events 为空则按时间升序回填现有记录、点赞、收藏、留言。
     * 使用与实时行为相同的 award 方法，事件时间取原始创建时间；失败只记日志，不影响服务启动。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnce() {
        try {
            if (events.count() > 0) return;
            log.info("五维人格：stat_events 为空，开始历史数据回填");
            Map<Long, String> owners = new HashMap<>();

            int page = 0;
            while (true) {
                Page<Record> p = records.findAll(PageRequest.of(page, 200,
                        Sort.by(Sort.Direction.ASC, "createdAt", "id")));
                for (Record r : p.getContent()) {
                    owners.put(r.getId(), r.getUsername());
                    awardRecord(r, r.getCreatedAt(), new LinkedHashMap<>());
                }
                if (!p.hasNext()) break;
                page++;
            }

            for (RecordLike l : likes.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "id"))) {
                String owner = owners.get(l.getRecordId());
                if (owner == null) continue; // 记录已删除
                awardLike(owner, l.getUsername(), l.getRecordId(), l.getCreatedAt(), new LinkedHashMap<>());
            }

            for (RecordFavorite f : favorites.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "id"))) {
                String owner = owners.get(f.getRecordId());
                if (owner == null) continue; // 记录已删除
                awardFavorite(owner, f.getUsername(), f.getRecordId(), f.getCreatedAt());
            }

            for (Comment c : comments.findAll(Sort.by(Sort.Direction.ASC, "createdAt", "id"))) {
                awardComment(owners.get(c.getRecordId()), c, c.getCreatedAt(), new LinkedHashMap<>());
            }

            // 历史加分属于陈年数据，全部标记已见，避免老用户下次打开站点时一次性弹出巨大的 POINT UP
            events.markAllSeen();
            log.info("五维人格：历史数据回填完成，共写入 {} 条计分事件", events.count());
        } catch (Exception e) {
            log.warn("五维人格：历史数据回填失败（不影响服务启动）", e);
        }
    }

    // ---------- 工具 ----------

    /** 本人当前各维度累计点数。 */
    private Map<String, Integer> totalsOf(String username) {
        Map<String, Integer> m = new HashMap<>();
        if (username == null || username.isBlank()) return m;
        for (Object[] row : events.totals(username)) {
            m.put((String) row[0], ((Number) row[1]).intValue());
        }
        return m;
    }

    /** 累计点数对应的等级（1~5）。 */
    private static int rankOf(int value) {
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (value >= THRESHOLDS[i]) return i + 1;
        }
        return 1;
    }

    /** 对比加分前后的等级，返回本次升级的维度（按固定顺序），供前端播放 RANK UP。 */
    private static List<Map<String, Object>> rankUps(Map<String, Integer> before, Map<String, Integer> after) {
        List<Map<String, Object>> ups = new ArrayList<>();
        for (StatDef def : STATS) {
            int b = rankOf(before.getOrDefault(def.key(), 0));
            int a = rankOf(after.getOrDefault(def.key(), 0));
            if (a <= b) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", def.key());
            m.put("name", def.name());
            m.put("from", b);
            m.put("rank", a);
            m.put("title", def.titles()[a - 1]);
            ups.add(m);
        }
        return ups;
    }

    /** 计分入口的统一返回结构。 */
    private static Map<String, Object> result(Map<String, Integer> gains, List<Map<String, Object>> ups) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gains", gains);
        out.put("rank_ups", ups);
        return out;
    }

    private Map<String, Object> statMap(StatDef def, int value) {
        int rank = rankOf(value);
        String[] titles = def.titles();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", def.key());
        m.put("name", def.name());
        m.put("value", value);
        m.put("rank", rank);
        m.put("title", titles[rank - 1]);
        if (rank >= 5) {
            m.put("next", null);
            m.put("next_title", null);
            m.put("progress", 100);
        } else {
            int hi = THRESHOLDS[rank];
            m.put("next", hi);
            m.put("next_title", titles[rank]);
            // 与契约示例一致：42 分、下一等级 70 分时 progress=60，即累计值占下一等级阈值的比例
            m.put("progress", (int) Math.round(value * 100.0 / hi));
        }
        return m;
    }

    private long countNative(String sql, String username) {
        Object v = em.createNativeQuery(sql).setParameter("u", username).getSingleResult();
        return v == null ? 0L : ((Number) v).longValue();
    }

    /** @提及：去掉标签后匹配，命中至少一个真实存在、非自己、非 AI 的用户。 */
    private boolean hasMention(String plain, String me) {
        Matcher m = MENTION_RE.matcher(plain);
        while (m.find()) {
            String u = m.group(1);
            if (u.equals(me) || isIgnored(u)) continue;
            if (users.existsByUsername(u)) return true;
        }
        return false;
    }

    /** 记录正文转纯文本：先整段去掉图片/视频标签（含 base64），再去标签、解码常见实体、压缩空白。 */
    private static String plainText(String html) {
        if (html == null) return "";
        String t = MEDIA_TAG_RE.matcher(html).replaceAll(" ");
        t = TAG_RE.matcher(t).replaceAll(" ");
        return t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    /** 最近记录摘要：纯文本最多 60 字，超出加省略号。 */
    private static String preview(String html) {
        String t = plainText(html);
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }

    private static boolean isIgnored(String username) {
        return username == null || username.isBlank() || AiAssistantService.AI_NAME.equals(username);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static LocalDate shanghaiDay(OffsetDateTime t) {
        return t.atZoneSameInstant(SHANGHAI).toLocalDate();
    }

    private static OffsetDateTime startOfDay(LocalDate d) {
        return d.atStartOfDay(SHANGHAI).toOffsetDateTime();
    }

    private static LocalDate toLocalDate(Object v) {
        if (v instanceof LocalDate l) return l;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof java.util.Date d) return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        if (v instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate();
        throw new IllegalArgumentException("未知日期类型: " + v.getClass());
    }

    private record StatDef(String key, String name, String[] titles) {}
}
