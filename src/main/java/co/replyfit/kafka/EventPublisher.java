package co.replyfit.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import co.replyfit.kafka.event.InquiryUploadedEvent;
import co.replyfit.kafka.event.ReportRequestedEvent;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishInquiriesUploaded(InquiryUploadedEvent event) {
        // 사용자 ID를 파티션 키로 사용 — 동일 셀러의 문의는 순서 보장
        kafkaTemplate.send(KafkaTopics.INQUIRIES_UPLOADED, String.valueOf(event.userId()), event);
        log.info("Published InquiryUploadedEvent jobId={} userId={} count={}",
                event.jobId(), event.userId(), event.inquiryIds().size());
    }

    public void publishReportRequested(ReportRequestedEvent event) {
        kafkaTemplate.send(KafkaTopics.REPORTS_REQUESTED, String.valueOf(event.userId()), event);
        log.info("Published ReportRequestedEvent userId={} reportId={}", event.userId(), event.reportId());
    }
}
