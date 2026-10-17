package com.studylog.stats;

import jakarta.persistence.EntityManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/** 统计：提交次数排行 + 关键词词云。分词/停用词规则与 Python 版一致。 */
@RestController
@RequestMapping("/api")
public class StatsController {

    private final EntityManager em;
    private final WordExtractor wordExtractor;

    public StatsController(EntityManager em) {
        this.em = em;
        this.wordExtractor = new WordExtractor();
    }

    /**
     * 统计接口。
     * @param range 时间范围：all（全部，默认）/ month（本月）/ week（本周）/ today（本日），均按上海时区计算。
     */
    @GetMapping("/stats")
    Map<String, Object> stats(@RequestParam(defaultValue = "all") String range) {
        range = range == null ? "all" : range.trim().toLowerCase();
        String cond = switch (range) {
            case "month" -> "WHERE DATE(r.created_at + INTERVAL 8 HOUR) >= DATE_FORMAT(NOW() + INTERVAL 8 HOUR, '%Y-%m-01')";
            case "week"  -> "WHERE YEARWEEK(r.created_at + INTERVAL 8 HOUR, 1) = YEARWEEK(NOW() + INTERVAL 8 HOUR, 1)";
            case "today" -> "WHERE DATE(r.created_at + INTERVAL 8 HOUR) = DATE(NOW() + INTERVAL 8 HOUR)";
            default      -> "";
        };

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT r.username, COUNT(*) AS cnt, COALESCE(u.color, '4a90d9') AS color " +
                "FROM records r LEFT JOIN users u ON u.username = r.username " +
                cond + " GROUP BY r.username ORDER BY cnt DESC").getResultList();

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
        List<String> contents = em.createNativeQuery("SELECT content FROM records r " + cond)
                .getResultList();
        for (String content : contents) {
            for (String w : wordExtractor.extract(content)) {
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
}
