package co.replyfit.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.replyfit.ai.LlmClient.ClassificationResult;
import co.replyfit.ai.LlmClient.DraftContext;
import co.replyfit.ai.LlmClient.DraftResult;
import co.replyfit.ai.LlmClient.PolicyRef;
import co.replyfit.inquiry.InquiryCategory;

@DisplayName("RuleBasedLlmClient — 폴백 엔진")
class RuleBasedLlmClientTest {

    private final RuleBasedLlmClient client = new RuleBasedLlmClient();

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "'165cm인데 사이즈 M 맞을까요?', SIZE",
            "'언제 배송 시작되나요? 송장 좀요', SHIPPING",
            "'색상이 화면과 달라 교환하고 싶어요', EXCHANGE_RETURN",
            "'재입고 언제 되나요? 품절이던데', RESTOCK",
            "'실물 색감이 화면과 같나요?', COLOR",
            "'스트랩 길이 조절 되나요?', OTHER",
    })
    void 키워드_기반_분류(String content, InquiryCategory expected) {
        ClassificationResult result = client.classify("상품", content);
        assertThat(result.category()).isEqualTo(expected);
    }

    @Test
    void 교환과_색상이_섞이면_교환반품이_우선한다() {
        // "교환" 키워드가 있으면 실제 처리(반품 안내)가 필요한 문의로 본다
        ClassificationResult result = client.classify("가디건", "색상이 달라서 교환 원해요");
        assertThat(result.category()).isEqualTo(InquiryCategory.EXCHANGE_RETURN);
    }

    @Test
    void 빈_문의는_기타_저신뢰도로_분류한다() {
        ClassificationResult result = client.classify("상품", "");
        assertThat(result.category()).isEqualTo(InquiryCategory.OTHER);
        assertThat(result.confidence()).isLessThan(0.5);
    }

    @Test
    void 초안은_등록된_정책_문구를_그대로_인용하고_ID를_기록한다() {
        PolicyRef policy = new PolicyRef(7L, "배송 정책", "기본 배송 안내",
                "주문 후 평균 2~3일 내 출고됩니다.");
        DraftContext ctx = new DraftContext("지은상점", "이*준", "셔츠", "쿠팡",
                "언제 배송되나요?", InquiryCategory.SHIPPING, List.of(policy), null);

        DraftResult draft = client.generateDraft(ctx);

        assertThat(draft.content())
                .contains("이*준")
                .contains("지은상점")
                .contains("주문 후 평균 2~3일 내 출고됩니다.");
        assertThat(draft.citedPolicyIds()).containsExactly(7L);
    }

    @Test
    void 정책이_없으면_셀러_확인_필요_문구를_넣는다() {
        DraftContext ctx = new DraftContext("지은상점", "김*연", null, "네이버",
                "반품 배송비 얼마예요?", InquiryCategory.EXCHANGE_RETURN, List.of(), null);

        DraftResult draft = client.generateDraft(ctx);

        assertThat(draft.content()).contains("[셀러 확인 필요]");
        assertThat(draft.citedPolicyIds()).isEmpty();
    }

    @Test
    void 법정_안내가_주어지면_초안에_포함한다() {
        DraftContext ctx = new DraftContext("지은상점", "김*연", "팬츠", "네이버",
                "반품하고 싶어요", InquiryCategory.EXCHANGE_RETURN, List.of(),
                LegalNotices.WITHDRAWAL_RIGHT);

        DraftResult draft = client.generateDraft(ctx);

        assertThat(draft.content()).contains("[법정 안내]");
    }

    @Test
    void 리포트_인사이트는_집계_데이터의_수치를_반영한다() {
        String aggregate = """
                {"returnReasonsTop5":[{"reason":"사이즈가 맞지 않음","count":5}],
                 "problemProducts":[{"productName":"린넨 팬츠","negativeCount":3,"topIssue":"사이즈"}]}
                """;
        String insights = client.reportInsights(aggregate);
        assertThat(insights).contains("사이즈가 맞지 않음").contains("린넨 팬츠");
    }
}
