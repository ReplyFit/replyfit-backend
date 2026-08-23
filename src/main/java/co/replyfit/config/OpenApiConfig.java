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
                        .description("""
                                의류·잡화 온라인 셀러를 위한 문의 응대 자동화 B2B SaaS — \
                                AI 초안 생성 · 문의 자동분류 · 주간 VOC 리포트

                                **사용 방법**
                                1. `01. 인증 > 로그인`을 데모 계정(demo@replyfit.co / demo1234!)으로 호출
                                2. 응답의 `accessToken`을 복사해 우측 상단 **Authorize** 버튼에 입력
                                3. 이후 모든 API를 브라우저에서 바로 실행할 수 있습니다

                                **전체 흐름**: CSV 업로드(03) → 작업 진행률 폴링(04) → 문의 확인(05) → \
                                초안 승인·발송(06) → 주간 VOC 리포트(09)""")
                        .version("v0.1.1"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
