package co.replyfit.policy;

public enum PolicyType {
    SHIPPING("배송 정책"),
    EXCHANGE_RETURN("교환/반품 정책"),
    SIZE_GUIDE("사이즈 가이드"),
    RESTOCK("재입고 안내"),
    GENERAL("일반 안내");

    private final String label;

    PolicyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
