package co.replyfit.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.replyfit.ai.LlmClient.DraftContext;
import co.replyfit.ai.LlmClient.PolicyRef;
import co.replyfit.ai.PolicyGuard.GuardResult;
import co.replyfit.inquiry.InquiryCategory;

@DisplayName("PolicyGuard — 정책 검증 로직 (출처 없는 수치 차단)")
class PolicyGuardTest {

    private static final PolicyRef EXCHANGE_POLICY = new PolicyRef(
            1L, "교환/반품 정책", "교환/반품 기준",
            "상품 수령일로부터 7일 이내 교환/반품 신청이 가능합니다. 단순 변심은 왕복 배송비 6,000원이 부과됩니다.");

    private DraftContext context(String legalNotice, PolicyRef... policies) {
        return new DraftContext("지은상점", "김*연", "린넨 팬츠", "네이버",
                "반품하고 싶어요.", InquiryCategory.EXCHANGE_RETURN, List.of(policies), legalNotice);
    }

    @Test
    void 정책에_있는_수치만_인용한_초안은_통과한다() {
        String draft = "수령일로부터 7일 이내 반품 가능하며, 단순 변심은 왕복 배송비 6,000원이 부과됩니다.";
        GuardResult result = PolicyGuard.verify(draft, context(null, EXCHANGE_POLICY));
        assertThat(result.passed()).isTrue();
        assertThat(result.note()).isNull();
    }

    @Test
    void 정책에_없는_수치가_있으면_경고와_함께_실패한다() {
        String draft = "14일 이내 반품 가능하며 배송비는 3,000원입니다.";
        GuardResult result = PolicyGuard.verify(draft, context(null, EXCHANGE_POLICY));
        assertThat(result.passed()).isFalse();
        assertThat(result.note()).contains("14일").contains("셀러 확인이 필요");
    }

    @Test
    void 법정_안내에_있는_수치는_통과한다() {
        String draft = "법에 따라 공급받은 날부터 7일 이내 청약철회가 가능합니다.";
        GuardResult result = PolicyGuard.verify(draft, context(LegalNotices.WITHDRAWAL_RIGHT));
        assertThat(result.passed()).isTrue();
    }

    @Test
    void 문의_원문에_있는_수치는_통과한다() {
        DraftContext ctx = new DraftContext("지은상점", "김*연", "팬츠", "네이버",
                "3일 전에 주문했어요.", InquiryCategory.SHIPPING, List.of(), null);
        GuardResult result = PolicyGuard.verify("3일 전 주문 건 확인해 드리겠습니다.", ctx);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void 수치가_전혀_없는_초안은_통과한다() {
        GuardResult result = PolicyGuard.verify(
                "안녕하세요 고객님, 확인 후 안내드리겠습니다.", context(null, EXCHANGE_POLICY));
        assertThat(result.passed()).isTrue();
    }

    @Test
    void 빈_초안은_실패한다() {
        assertThat(PolicyGuard.verify("", context(null)).passed()).isFalse();
        assertThat(PolicyGuard.verify(null, context(null)).passed()).isFalse();
    }

    @Test
    void 공백_차이는_무시하고_대조한다() {
        // 정책: "7일 이내" / 초안: "7일  이내" (공백 2개)
        String draft = "수령일로부터 7일  이내 신청 가능합니다.";
        GuardResult result = PolicyGuard.verify(draft, context(null, EXCHANGE_POLICY));
        assertThat(result.passed()).isTrue();
    }
}
