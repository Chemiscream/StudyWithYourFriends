package com.studylog.daily;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** 每天 00:00（上海时间）自动生成前一天的全站日报；模型离线时由补生成任务兜底重试。 */
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
            System.err.println("[DailyReport] 生成 " + yesterday + " 日报失败: " + e.getMessage());
        }
    }

    /**
     * 每 5 分钟检查并补生成缺失日报中最早的一天。
     * 模型离线导致某天未生成时，这里会持续重试直到成功；
     * 连续多天未生成时，按日期从早到晚一天一天补，不会跳过或覆盖。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void backfillMissing() {
        try {
            service.backfillOne();
        } catch (Exception e) {
            System.err.println("[DailyReport] 补生成任务异常: " + e.getMessage());
        }
    }
}
