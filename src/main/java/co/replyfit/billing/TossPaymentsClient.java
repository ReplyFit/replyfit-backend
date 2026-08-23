package co.replyfit.billing;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * 토스페이먼츠 연동 스텁.
 *
 * 사업계획서 로드맵에 따라 파일럿 기간('26.11)에는 계좌이체 수동 청구를 사용하고,
 * '26.12에 토스페이먼츠 정기결제(빌링) API로 교체한다.
 *
 * 실제 연동 시 교체 지점:
 *  - 빌링키 발급: POST https://api.tosspayments.com/v1/billing/authorizations/issue
 *  - 정기결제 승인: POST https://api.tosspayments.com/v1/billing/{billingKey}
 *  - 시크릿 키는 TOSS_SECRET_KEY 환경변수로 주입
 */
@Component
public class TossPaymentsClient {

    public record BillingKeyResult(String billingKey, boolean live) {
    }

    public BillingKeyResult issueBillingKey(Long userId, PlanType plan) {
        // TODO('26.12): 토스페이먼츠 빌링키 발급 API 호출로 교체
        return new BillingKeyResult("stub-billing-" + UUID.randomUUID(), false);
    }
}
