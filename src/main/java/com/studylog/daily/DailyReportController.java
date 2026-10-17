package com.studylog.daily;

import com.studylog.model.DailyReport;
import com.studylog.repo.DailyReportRepository;
import com.studylog.sec.Tokens;
import com.studylog.web.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * AI 日报接口：全站统一一份，每天 0 点自动生成前一天的。
 * 列表从昨天往前分页（不含今天，今天的日报要到明天 0 点才生成）。
 */
@RestController
@RequestMapping("/api/daily-reports")
public class DailyReportController {

    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");

    private final DailyReportRepository repo;
    private final DailyReportService service;
    private final Tokens tokens;

    public DailyReportController(DailyReportRepository repo, DailyReportService service, Tokens tokens) {
        this.repo = repo;
        this.service = service;
        this.tokens = tokens;
    }

    /** 分页列表：从昨天往前，每页 size 条。 */
    @GetMapping
    Map<String, Object> list(HttpServletRequest req,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "5") int size) {
        requireLogin(req);
        page = Math.max(0, page);
        size = Math.min(20, Math.max(1, size));
        LocalDate today = LocalDate.now(SH);
        Page<DailyReport> p = repo.findByReportDateBeforeOrderByReportDateDesc(today, PageRequest.of(page, size));

        List<Map<String, Object>> items = new ArrayList<>();
        for (DailyReport r : p.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("report_date", r.getReportDate().toString());
            m.put("summary_preview", summaryPreview(r.getSummary()));
            m.put("has_summary", r.getSummary() != null && !r.getSummary().isBlank());
            m.put("total", readTotal(r.getContent()));
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("items", items);
        out.put("page", page);
        out.put("size", size);
        out.put("total_pages", p.getTotalPages());
        out.put("has_more", page + 1 < p.getTotalPages());
        return out;
    }

    /** 指定日期详情。 */
    @GetMapping("/{date}")
    Map<String, Object> detail(HttpServletRequest req, @PathVariable String date) {
        requireLogin(req);
        DailyReport r = repo.findByReportDate(parseDate(date))
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "该日期暂无日报"));
        return detailOf(r);
    }

    /** 手动生成指定日期日报（补历史用）。已存在则覆盖。 */
    @PostMapping("/generate")
    Map<String, Object> generate(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        requireLogin(req);
        String d = body == null ? null : (String) body.get("date");
        LocalDate date = parseDate(d);
        Boolean ok = service.generate(date);
        if (ok == null) return Map.of("ok", false, "message", "AI 模型离线，系统每 5 分钟自动重试");
        if (!ok) return Map.of("ok", false, "message", "该日期没有学习记录，未生成日报");
        DailyReport r = repo.findByReportDate(date).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>(detailOf(r));
        out.put("message", "已生成 " + date + " 的日报");
        return out;
    }

    // ---------- 内部 ----------

    private Map<String, Object> detailOf(DailyReport r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("report_date", r.getReportDate().toString());
        out.put("summary", r.getSummary());
        out.put("stat", parseStat(r.getContent()));
        out.put("created_at", r.getCreatedAt());
        return out;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) throw new BizException(HttpStatus.BAD_REQUEST, "日期不能为空");
        try { return LocalDate.parse(s.trim()); }
        catch (Exception e) { throw new BizException(HttpStatus.BAD_REQUEST, "日期格式应为 yyyy-MM-dd"); }
    }

    private static String summaryPreview(String s) {
        if (s == null || s.isBlank()) return "";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 60 ? oneLine.substring(0, 60) + "…" : oneLine;
    }

    private static int readTotal(String content) {
        try {
            var t = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
            return t.path("total").asInt(0);
        } catch (Exception e) { return 0; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseStat(String content) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(content, Map.class);
        } catch (Exception e) { return Map.of(); }
    }

    private String requireLogin(HttpServletRequest req) {
        String u = tokens.currentUser(req);
        if (u == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        return u;
    }
}
