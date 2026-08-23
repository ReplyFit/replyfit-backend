package co.replyfit.kafka.event;

/** 주간 VOC 리포트 생성 요청 */
public record ReportRequestedEvent(Long userId, Long reportId) {
}
