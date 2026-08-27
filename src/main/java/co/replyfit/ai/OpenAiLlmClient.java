package co.replyfit.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import co.replyfit.inquiry.InquiryCategory;

/**
 * OpenAI API 기반 LLM 클라이언트 (공식 openai-java SDK 사용).
 *
 * API 오류 시 {@link RuleBasedLlmClient}로 자동 폴백해
 * 파이프라인이 멈추지 않도록 설계했다. 폴백 판단은 {@link #call}
 * 한 곳에서만 이뤄진다 — 경로마다 흩어져 있으면 한쪽만 고치는 사고가 난다.
 */
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    /** 분류는 판정 한 줄이면 되고, 초안·리포트는 서술이 길다 */
    private static final long CLASSIFY_MAX_TOKENS = 2048L;
    private static final long COMPOSE_MAX_TOKENS = 4096L;

    private final OpenAIClient client;
    private final String model;
    private final RuleBasedLlmClient fallback;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiLlmClient(String model, RuleBasedLlmClient fallback) {
        // OPENAI_API_KEY 환경변수에서 인증 정보를 읽는다.
        this(OpenAIOkHttpClient.fromEnv(), model, fallback);
    }

    /** 테스트에서 OpenAIClient를 주입하기 위한 생성자 */
    OpenAiLlmClient(OpenAIClient client, String model, RuleBasedLlmClient fallback) {
        this.client = client;
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public ClassificationResult classify(String productName, String content) {
        return call("classify",
                () -> ChatCompletionCreateParams.builder()
                        .model(model)
                        .maxCompletionTokens(CLASSIFY_MAX_TOKENS)
                        // 분류는 단순 작업 — 낮은 추론 강도로 비용·지연 절감
                        .reasoningEffort(ReasoningEffort.LOW)
                        .addSystemMessage(PromptTemplates.CLASSIFY_SYSTEM)
                        .addUserMessage(PromptTemplates.classifyUser(productName, content))
                        .build(),
                this::parseClassification,
                () -> fallback.classify(productName, content));
    }

    @Override
    public DraftResult generateDraft(DraftContext ctx) {
        return call("draft",
                () -> ChatCompletionCreateParams.builder()
                        .model(model)
                        .maxCompletionTokens(COMPOSE_MAX_TOKENS)
                        .addSystemMessage(PromptTemplates.DRAFT_SYSTEM)
                        .addUserMessage(PromptTemplates.draftUser(ctx))
                        .build(),
                text -> parseDraft(text, ctx),
                () -> fallback.generateDraft(ctx));
    }

    @Override
    public String reportInsights(String aggregateJson) {
        return call("report",
                () -> ChatCompletionCreateParams.builder()
                        .model(model)
                        .maxCompletionTokens(COMPOSE_MAX_TOKENS)
                        .addSystemMessage(PromptTemplates.REPORT_SYSTEM)
                        .addUserMessage(PromptTemplates.reportUser(aggregateJson))
                        .build(),
                text -> text == null || text.isBlank() ? null : text,
                () -> fallback.reportInsights(aggregateJson));
    }

    @Override
    public String name() {
        return "openai:" + model;
    }

    /**
     * OpenAI 호출 공통 뼈대 — 호출 · usage 로깅 · 텍스트 추출 · 폴백을 한 곳에 모은다.
     * 경로마다 다른 건 파라미터 구성({@code params})과 응답 해석({@code parse})뿐이다.
     *
     * @param parse 응답 본문 해석. 해석 불가 시 null을 반환하면 폴백으로 넘어간다
     */
    private <T> T call(String op,
                       Supplier<ChatCompletionCreateParams> params,
                       Function<String, T> parse,
                       Supplier<T> fallbackCall) {
        try {
            ChatCompletion completion = client.chat().completions().create(params.get());
            logUsage(op, completion);
            T parsed = parse.apply(firstText(completion));
            return parsed != null ? parsed : fallbackCall.get();
        } catch (Exception e) {
            log.warn("OpenAI {} failed ({}); falling back to rule-based", op, e.getMessage());
            return fallbackCall.get();
        }
    }

    private ClassificationResult parseClassification(String text) {
        JsonNode json = extractJson(text);
        if (json == null) {
            return null;
        }
        return new ClassificationResult(
                InquiryCategory.fromString(json.path("category").asText(null)),
                json.path("confidence").asDouble(0.8));
    }

    private DraftResult parseDraft(String text, DraftContext ctx) {
        JsonNode json = extractJson(text);
        if (json != null && json.hasNonNull("draft")) {
            List<Long> cited = new ArrayList<>();
            json.path("citedPolicyIds").forEach(node -> cited.add(node.asLong()));
            return new DraftResult(json.path("draft").asText(), cited);
        }
        // JSON 파싱 실패 시 전체 텍스트를 초안으로 사용
        if (text != null && !text.isBlank()) {
            return new DraftResult(text, ctx.policies().stream().map(PolicyRef::id).toList());
        }
        return null;
    }

    /** 호출당 토큰 사용량 — 운영 비용 모니터링용. 문의 1건당 2줄(분류·초안) 수준이라 볼륨 부담 없음. */
    private static void logUsage(String op, ChatCompletion completion) {
        completion.usage().ifPresent(usage -> log.info(
                "OpenAI {} usage: prompt={} completion={} total={}",
                op, usage.promptTokens(), usage.completionTokens(), usage.totalTokens()));
    }

    private static String firstText(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            return null;
        }
        return completion.choices().get(0).message().content().orElse(null);
    }

    /** 응답 텍스트에서 첫 번째 JSON 오브젝트를 추출한다. */
    private JsonNode extractJson(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }
}
