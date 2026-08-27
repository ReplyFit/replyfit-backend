package co.replyfit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import co.replyfit.inquiry.InquiryCategory;

/**
 * OpenAI 클라이언트의 <b>폴백 계약</b> 테스트.
 *
 * 이 클래스는 OpenAI 장애 시 파이프라인 연속성을 책임지는 지점이다.
 * 정상 응답보다 "응답이 이상할 때 규칙 기반으로 넘어가는가"가 핵심이라
 * 그 경로를 집중적으로 검증한다.
 */
@DisplayName("OpenAiLlmClient — 폴백 계약")
class OpenAiLlmClientTest {

    private OpenAIClient openAi;
    private OpenAiLlmClient client;

    @BeforeEach
    void setUp() {
        openAi = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        client = new OpenAiLlmClient(openAi, "gpt-5-mini", new RuleBasedLlmClient());
    }

    /** OpenAI가 주어진 본문으로 응답하도록 스텁 */
    private void respondWith(String content) {
        ChatCompletion completion = mock(ChatCompletion.class, RETURNS_DEEP_STUBS);
        when(completion.choices().isEmpty()).thenReturn(false);
        when(completion.choices().get(0).message().content())
                .thenReturn(java.util.Optional.ofNullable(content));
        when(completion.usage()).thenReturn(java.util.Optional.empty());
        when(openAi.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenReturn(completion);
    }

    /** OpenAI 호출이 터지도록 스텁 (401, 타임아웃 등) */
    private void failWith(String message) {
        when(openAi.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenThrow(new IllegalStateException(message));
    }

    private static LlmClient.DraftContext draftContext() {
        return new LlmClient.DraftContext(
                "데모스토어", "홍*동", "린넨 와이드 팬츠", "네이버",
                "사이즈가 작아요",
                InquiryCategory.SIZE,
                List.of(new LlmClient.PolicyRef(1L, "사이즈 가이드", "사이즈표", "55 사이즈는 S~M")),
                "전자상거래법 안내");
    }

    @Nested
    @DisplayName("API가 예외를 던지면")
    class WhenApiFails {

        @Test
        @DisplayName("classify는 규칙 기반 결과를 돌려준다")
        void classifyFallsBack() {
            failWith("401 Incorrect API key provided");

            var result = client.classify("린넨 팬츠", "사이즈가 작아요");

            assertThat(result).isNotNull();
            assertThat(result.category()).isEqualTo(InquiryCategory.SIZE);
        }

        @Test
        @DisplayName("generateDraft는 규칙 기반 초안을 돌려준다")
        void draftFallsBack() {
            failWith("timeout");

            var result = client.generateDraft(draftContext());

            assertThat(result).isNotNull();
            assertThat(result.content()).isNotBlank();
        }

        @Test
        @DisplayName("reportInsights는 규칙 기반 인사이트를 돌려준다")
        void reportFallsBack() {
            failWith("503 Service Unavailable");

            assertThat(client.reportInsights("{\"summary\":{}}")).isNotBlank();
        }
    }

    @Nested
    @DisplayName("응답이 해석 불가능하면")
    class WhenResponseUnusable {

        @Test
        @DisplayName("classify — JSON이 없으면 폴백")
        void classifyWithoutJson() {
            respondWith("죄송하지만 답변드릴 수 없습니다");

            assertThat(client.classify("린넨 팬츠", "사이즈가 작아요").category())
                    .isEqualTo(InquiryCategory.SIZE);
        }

        @Test
        @DisplayName("reportInsights — 본문이 비면 폴백")
        void reportWithBlankBody() {
            respondWith("   ");

            assertThat(client.reportInsights("{}")).isNotBlank();
        }

        @Test
        @DisplayName("generateDraft — JSON이 아니어도 본문이 있으면 그 본문을 초안으로 쓴다")
        void draftFallsBackToRawText() {
            respondWith("안녕하세요, 문의 주셔서 감사합니다.");

            var result = client.generateDraft(draftContext());

            assertThat(result.content()).isEqualTo("안녕하세요, 문의 주셔서 감사합니다.");
            // 인용 정책은 컨텍스트에 준 정책 전체로 채워진다
            assertThat(result.citedPolicyIds()).containsExactly(1L);
        }
    }

    @Nested
    @DisplayName("정상 응답이면")
    class WhenResponseValid {

        @Test
        @DisplayName("classify — JSON의 카테고리·신뢰도를 그대로 쓴다")
        void classifyParsesJson() {
            respondWith("{\"category\":\"EXCHANGE_RETURN\",\"confidence\":0.93}");

            var result = client.classify("크롭 니트", "색상이 달라 교환하고 싶어요");

            assertThat(result.category()).isEqualTo(InquiryCategory.EXCHANGE_RETURN);
            assertThat(result.confidence()).isEqualTo(0.93);
        }

        @Test
        @DisplayName("generateDraft — draft와 citedPolicyIds를 파싱한다")
        void draftParsesJson() {
            respondWith("{\"draft\":\"55 사이즈는 S를 권장드립니다.\",\"citedPolicyIds\":[1,2]}");

            var result = client.generateDraft(draftContext());

            assertThat(result.content()).isEqualTo("55 사이즈는 S를 권장드립니다.");
            assertThat(result.citedPolicyIds()).containsExactly(1L, 2L);
        }
    }

    @Test
    @DisplayName("name()은 모델명을 포함해 초안 감사 추적에 쓰인다")
    void nameIncludesModel() {
        assertThat(client.name()).isEqualTo("openai:gpt-5-mini");
    }
}
