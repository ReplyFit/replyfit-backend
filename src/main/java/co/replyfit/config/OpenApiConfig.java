package co.replyfit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI replyFitOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("리플핏 (ReplyFit) API")
                        .description("의류·잡화 온라인 셀러를 위한 문의 응대 자동화 B2B SaaS — "
                                + "AI 초안 생성 · 문의 자동분류 · 주간 VOC 리포트")
                        .version("v0.1.0"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
