package co.replyfit.kafka;

public final class KafkaTopics {

    /** CSV 업로드된 문의 배치 → 워커가 분류·초안 생성 */
    public static final String INQUIRIES_UPLOADED = "replyfit.inquiries.uploaded";

    /** 주간 VOC 리포트 생성 요청 */
    public static final String REPORTS_REQUESTED = "replyfit.reports.requested";

    private KafkaTopics() {
    }
}
