package co.replyfit.billing;

import java.util.List;

/**
 * 구독 요금제 (사업계획서 BUSINESS MODEL).
 * Starter 2.9만 / Growth 5.9만 / Pro 9.9만 (월)
 */
public enum PlanType {
    STARTER("Starter", "입문형", 29_000,
            List.of("문의·리뷰 CSV 업로드", "개인정보 자동 마스킹", "문의 유형 자동분류", "AI 답변 초안 생성")),
    GROWTH("Growth", "표준형", 59_000,
            List.of("Starter 전체 포함", "주간 VOC 리포트", "반품 사유 TOP5 분석", "상세페이지 문구 제안")),
    PRO("Pro", "상위형", 99_000,
            List.of("Growth 전체 포함", "고물량 대응(월 3,000건+)", "심화 VOC 분석", "우선 지원"));

    private final String displayName;
    private final String tagline;
    private final int monthlyPrice;
    private final List<String> features;

    PlanType(String displayName, String tagline, int monthlyPrice, List<String> features) {
        this.displayName = displayName;
        this.tagline = tagline;
        this.monthlyPrice = monthlyPrice;
        this.features = features;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTagline() {
        return tagline;
    }

    public int getMonthlyPrice() {
        return monthlyPrice;
    }

    public List<String> getFeatures() {
        return features;
    }
}
