package co.replyfit.inquiry;

public enum InquiryStatus {
    RECEIVED("접수"),
    PROCESSING("AI 처리중"),
    DRAFTED("초안 생성"),
    NEEDS_REVIEW("검토 필요"),
    APPROVED("승인 완료"),
    SENT("발송 완료"),
    FAILED("처리 실패");

    private final String label;

    InquiryStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
