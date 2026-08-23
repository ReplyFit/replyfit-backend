package co.replyfit.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import co.replyfit.kafka.KafkaTopics;
import co.replyfit.kafka.event.ReportRequestedEvent;
import co.replyfit.report.ReportService;

/**
 * Kafka 워커 — 주간 VOC 리포트 생성 (worker 프로필에서만 활성화).
 */
@Component
@Profile("worker")
public class ReportWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportWorker.class);

    private final ReportService reportService;

    public ReportWorker(ReportService reportService) {
        this.reportService = reportService;
    }

    @KafkaListener(topics = KafkaTopics.REPORTS_REQUESTED)
    public void onReportRequested(ReportRequestedEvent event) {
        log.info("Generating weekly VOC report reportId={} userId={}", event.reportId(), event.userId());
        reportService.generate(event.reportId(), null);
        log.info("Weekly VOC report done reportId={}", event.reportId());
    }
}
