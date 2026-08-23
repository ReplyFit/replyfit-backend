package co.replyfit.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public LlmClient llmClient(@Value("${replyfit.ai.provider}") String provider,
                               @Value("${replyfit.ai.model}") String model) {
        RuleBasedLlmClient ruleBased = new RuleBasedLlmClient();
        String apiKey = System.getenv("OPENAI_API_KEY");
        if ("openai".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank()) {
            log.info("AI provider: OpenAI API (model={})", model);
            return new OpenAiLlmClient(model, ruleBased);
        }
        log.warn("AI provider: rule-based fallback (OPENAI_API_KEY 미설정 또는 provider={})", provider);
        return ruleBased;
    }
}
