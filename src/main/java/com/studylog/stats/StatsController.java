package com.studylog.stats;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.huaban.analysis.jieba.WordDictionary;
import jakarta.persistence.EntityManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 统计：提交次数排行 + 关键词词云。分词/停用词规则与 Python 版一致。 */
@RestController
@RequestMapping("/api")
public class StatsController {

    private static final Pattern TAG_RE = Pattern.compile("<[^>]+>");
    private static final Pattern B64_RE = Pattern.compile("data:image/[a-zA-Z]+;base64,[A-Za-z0-9+/=]+");
    private static final Pattern EN_RE = Pattern.compile("[a-zA-Z]{3,}");
    private static final Pattern HAN_RE = Pattern.compile("[\\u4e00-\\u9fff]+");

    private static final Set<String> STOPWORDS = Set.of(
            ("的 了 和 是 我 你 他 她 它 在 就 都 而 及 与 着 或 一个 没有 我们 你们 他们 这 那 这个 那个 这些 那些 " +
            "今天 昨天 明天 什么 怎么 为什么 因为 所以 但是 如果 已经 还是 不是 就是 可以 自己 知道 觉得 现在 时候 " +
            "学习 记录 一下 真的 可能 应该 比较 这么 那么 这样 那样 还有 然后 之后 之前 一起 开始 继续 完成 " +
            "the a an and or but if of to in on at for with is are was were be been it this that i you he she we they my your " +
            "br div img src alt class style data image jpeg png gif webp base64 http https com www").split(" "));

    private final EntityManager em;
    private final JiebaSegmenter segmenter;

    public StatsController(EntityManager em) {
        this.em = em;
        // jieba-analysis 自带词典较老，加载与 Python jieba 同源的现代主词典，保证两边分词一致
        try (InputStream in = getClass().getResourceAsStream("/dict.txt")) {
            if (in != null) {
                Path tmp = Files.createTempFile("jieba-dict", ".txt");
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                WordDictionary.getInstance().loadUserDict(tmp, StandardCharsets.UTF_8);
                tmp.toFile().deleteOnExit();
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载 jieba 词典失败", e);
        }
        this.segmenter = new JiebaSegmenter();
    }

    @GetMapping("/stats")
    Map<String, Object> stats() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT r.username, COUNT(*) AS cnt, COALESCE(u.color, '4a90d9') AS color " +
                "FROM records r LEFT JOIN users u ON u.username = r.username " +
                "GROUP BY r.username ORDER BY cnt DESC").getResultList();

        List<Map<String, Object>> ranking = new ArrayList<>();
        long total = 0;
        for (Object[] r : rows) {
            long cnt = ((Number) r[1]).longValue();
            total += cnt;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", r[0]);
            m.put("count", cnt);
            m.put("color", r[2]);
            ranking.add(m);
        }

        Map<String, Long> counter = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<String> contents = em.createNativeQuery("SELECT content FROM records").getResultList();
        for (String content : contents) {
            for (String w : extractWords(content)) {
                counter.merge(w, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> words = counter.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(60)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("text", e.getKey());
                    m.put("weight", e.getValue());
                    return m;
                })
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("ranking", ranking);
        out.put("words", words);
        out.put("total", total);
        return out;
    }

    List<String> extractWords(String content) {
        if (content == null) return List.of();
        String text = B64_RE.matcher(content).replaceAll(" ");
        text = TAG_RE.matcher(text).replaceAll(" ");
        List<String> out = new ArrayList<>();
        for (SegToken t : segmenter.process(text, JiebaSegmenter.SegMode.SEARCH)) {
            String w = t.word.trim().toLowerCase(Locale.ROOT);
            if (w.isEmpty() || w.length() < 2 || STOPWORDS.contains(w)) continue;
            if (HAN_RE.matcher(w).matches()) out.add(w);
        }
        Matcher m = EN_RE.matcher(text);
        while (m.find()) {
            String w = m.group().toLowerCase(Locale.ROOT);
            if (!STOPWORDS.contains(w)) out.add(w);
        }
        return out;
    }
}
