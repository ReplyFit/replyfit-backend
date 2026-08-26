package co.replyfit.ai;

import java.util.ArrayList;
import java.util.List;

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
 * 파이프라인이 멈추지 않도록 설계했다.
 */
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final OpenAIClient client;
    private final String model;
    private final RuleBasedLlmClient fallback;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiLlmClient(String model, RuleBasedLlmClient fallback) {
        // OPENAI_API_KEY 환경변수에서 인증 정보를 읽는다.
        this.client = OpenAIOkHttpClient.fromEnv();
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public ClassificationResult classify(String productName, String content) {
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(model)
                    .maxCompletionTokens(2048L)
                    // 분류는 단순 작업 — 낮은 추론 강도로 비용·지연 절감
                    .reasoningEffort(ReasoningEffort.LOW)
                    .addSystemMessage(PromptTemplates.CLASSIFY_SYSTEM)
                    .addUserMessage(PromptTemplates.classifyUser(productName, content))
                    .build();
            ChatCompletion completion = client.chat().completions().create(params);
            logUsage("classify", completion);
            String text = firstText(completion);
            JsonNode json = extractJson(text);
            if (json == null) {
                return fallback.classify(productName, content);
            }
            InquiryCategory category = InquiryCategory.fromString(json.path("category").asText(null));
            double confidence = json.path("confidence").asDouble(0.8);
            return new ClassificationResult(category, confidence);
        } catch (Exception e) {
            log.warn("OpenAI classify failed ({}); falling back to rule-based", e.getMessage());
            return fallback.classify(productName, content);
        }
    }

    @Override
    public DraftResult generateDraft(DraftContext ctx) {
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(model)
                    .maxCompletionTokens(4096L)
                    .addSystemMessage(PromptTemplates.DRAFT_SYSTEM)
                    .addUserMessage(PromptTemplates.draftUser(ctx))
                    .build();
            ChatCompletion completion = client.chat().completions().create(params);
            logUsage("draft", completion);
            String text = firstText(completion);
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
            return fallback.generateDraft(ctx);
        } catch (Exception e) {
            log.warn("OpenAI draft generation failed ({}); falling back to rule-based", e.getMessage());
            return fallback.generateDraft(ctx);
        }
    }

    @Override
    public String reportInsights(String aggregateJson) {
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(model)
                    .maxCompletionTokens(4096L)
                    .addSystemMessage(PromptTemplates.REPORT_SYSTEM)
                    .addUserMessage(PromptTemplates.reportUser(aggregateJson))
                    .build();
            ChatCompletion completion = client.chat().completions().create(params);
            logUsage("report", completion);
            String text = firstText(completion);
            if (text == null || text.isBlank()) {
                return fallback.reportInsights(aggregateJson);
            }
            return text;
        } catch (Exception e) {
            log.warn("OpenAI report insights failed ({}); falling back to rule-based", e.getMessage());
            return fallback.reportInsights(aggregateJson);
        }
    }

    @Override
    public String name() {
        return "openai:" + model;
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
