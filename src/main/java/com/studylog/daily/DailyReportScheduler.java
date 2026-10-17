package com.studylog.daily;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** 每天 00:00（上海时间）自动生成前一天的全站日报。 */
@Component
public class DailyReportScheduler {

    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");

    private final DailyReportService service;

    public DailyReportScheduler(DailyReportService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Shanghai")
    public void generateYesterday() {
        LocalDate yesterday = LocalDate.now(SH).minusDays(1);
        try {
            service.generate(yesterday);
        } catch (Exception e) {
            // 定时任务失败不抛出，避免影响其他调度；日志由调用方观察
            System.err.println("[DailyReport] 生成 " + yesterday + " 日报失败: " + e.getMessage());
        }
    }
}
