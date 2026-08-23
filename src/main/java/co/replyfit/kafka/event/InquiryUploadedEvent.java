package co.replyfit.kafka.event;

import java.util.List;

/** 문의 업로드 완료 → AI 파이프라인 처리 요청 */
public record InquiryUploadedEvent(String jobId, Long userId, List<Long> inquiryIds) {
}
