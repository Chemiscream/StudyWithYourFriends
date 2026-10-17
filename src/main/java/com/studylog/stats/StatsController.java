package com.studylog.stats;

import jakarta.persistence.EntityManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
