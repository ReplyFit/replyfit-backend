package co.replyfit.inquiry;

/**
 * 의류·잡화 특화 문의 카테고리 (사업계획서 MVP 핵심 기능 ②).
 */
public enum InquiryCategory {
    SIZE("사이즈"),
    SHIPPING("배송"),
    EXCHANGE_RETURN("교환/반품"),
    COLOR("색상"),
    RESTOCK("재입고"),
    OTHER("기타");

    private final String label;

    InquiryCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static InquiryCategory fromString(String value) {
        if (value == null) {
            return OTHER;
        }
        try {
            return InquiryCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
