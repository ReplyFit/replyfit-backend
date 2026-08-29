package co.replyfit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProductionConfigValidator — 운영 기동 전 설정 검증")
class ProductionConfigValidatorTest {

    private static final String GOOD_SECRET = "9f3c1a77b25e4d08ae6152cb90d743fe2178bc4a05e93610d8f27ab4c6e15903";
    private static final String GOOD_DB_PASSWORD = "rds-injected-password";
    private static final String GOOD_ORIGINS = "https://app.replyfit.co";

    private static List<String> validate(String secret, String dbPassword, String origins, boolean seed) {
        return ProductionConfigValidator.validate(secret, dbPassword, origins, seed);
    }

    private static List<String> withSecret(String secret) {
        return validate(secret, GOOD_DB_PASSWORD, GOOD_ORIGINS, false);
    }

    private static List<String> withOrigins(String origins) {
        return validate(GOOD_SECRET, GOOD_DB_PASSWORD, origins, false);
    }

    @Test
    @DisplayName("제대로 주입된 운영 설정은 통과한다")
    void validConfigPasses() {
        assertThat(validate(GOOD_SECRET, GOOD_DB_PASSWORD, GOOD_ORIGINS, false)).isEmpty();
    }

    @Nested
    @DisplayName("JWT 시크릿")
    class JwtSecret {

        @Test
        @DisplayName("저장소에 공개된 개발용 기본값이면 거부한다")
        void devDefaultRejected() {
            assertThat(withSecret(ProductionConfigValidator.DEV_JWT_SECRET))
                    .anySatisfy(p -> assertThat(p).contains("JWT_SECRET").contains("개발용 기본값"));
        }

        @Test
        @DisplayName("64바이트 미만이면 거부한다 — HS512 서명 키 길이")
        void tooShortRejected() {
            assertThat(withSecret("a".repeat(ProductionConfigValidator.MIN_JWT_SECRET_BYTES - 1)))
                    .anySatisfy(p -> assertThat(p).contains("64바이트"));
        }

        @Test
        @DisplayName("정확히 64바이트면 통과한다 — 경계값")
        void exactlyMinimumPasses() {
            assertThat(withSecret("a".repeat(ProductionConfigValidator.MIN_JWT_SECRET_BYTES))).isEmpty();
        }
    }

    @Test
    @DisplayName("DB 비밀번호가 개발용 기본값이면 거부한다")
    void devDbPasswordRejected() {
        assertThat(validate(GOOD_SECRET, ProductionConfigValidator.DEV_DB_PASSWORD, GOOD_ORIGINS, false))
                .anySatisfy(p -> assertThat(p).contains("DB_PASSWORD"));
    }

    @Nested
    @DisplayName("CORS 허용 오리진")
    class CorsOrigins {

        @Test
        @DisplayName("비어 있으면 거부한다")
        void blankRejected() {
            assertThat(withOrigins("   ")).anySatisfy(p -> assertThat(p).contains("비어 있습니다"));
        }

        @Test
        @DisplayName("와일드카드가 섞여 있으면 거부한다")
        void wildcardRejected() {
            assertThat(withOrigins("https://app.replyfit.co, *"))
                    .anySatisfy(p -> assertThat(p).contains("와일드카드"));
        }

        @Test
        @DisplayName("여러 오리진을 콤마로 나열하면 통과한다")
        void multipleOriginsPass() {
            assertThat(withOrigins("https://app.replyfit.co, https://replyfit.co")).isEmpty();
        }
    }

    @Test
    @DisplayName("데모 시드가 켜져 있으면 거부한다 — 운영 DB 오염")
    void demoSeedRejected() {
        assertThat(validate(GOOD_SECRET, GOOD_DB_PASSWORD, GOOD_ORIGINS, true))
                .anySatisfy(p -> assertThat(p).contains("REPLYFIT_SEED_DEMO"));
    }

    @Test
    @DisplayName("문제가 여러 개면 한 번에 모두 보고한다 — 고치고 또 막히는 일이 없도록")
    void allProblemsReportedAtOnce() {
        assertThat(validate(ProductionConfigValidator.DEV_JWT_SECRET,
                ProductionConfigValidator.DEV_DB_PASSWORD, "*", true)).hasSize(4);
    }

    @Test
    @DisplayName("검증 메시지에 시크릿 실제 값이 담기지 않는다")
    void messagesNeverContainSecretValues() {
        String secret = "leak-me-jwt-secret-value";
        String dbPassword = "leak-me-db-password";

        String joined = String.join(" ", validate(secret, dbPassword, "*", true));

        assertThat(joined).doesNotContain(secret).doesNotContain(dbPassword);
    }
}
