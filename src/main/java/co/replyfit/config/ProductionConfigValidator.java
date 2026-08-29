package co.replyfit.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 기본값 그대로 운영이 기동되는 사고를 막는다(#56).
 *
 * <p>기본 프로필은 아무 설정 없이도 데모가 돌아가도록 안전하지 않은 기본값을 갖고 있고,
 * 그 값들은 저장소에 그대로 공개돼 있다. 운영에 실수로 올라가면 즉시 침해로 이어지는데
 * 경고 로그는 묻히기 때문에, prod 프로필에서는 <b>기동 자체를 거부</b>한다.
 *
 * <p>배포 파이프라인이 시크릿 주입에 실패해도 서비스가 "일단 뜨는" 상황을 만들지 않는 것이
 * 목적이다. 뜨지 않는 편이 낫다.
 *
 * <p>검증 메시지에는 실제 값을 절대 싣지 않는다. 로그로 새는 순간 검증의 의미가 없어진다.
 */
@Component
@Profile("prod")
public class ProductionConfigValidator {

    /** application.yml에 적힌 개발용 기본값 — 저장소에 공개돼 있다. */
    static final String DEV_JWT_SECRET =
            "replyfit-dev-secret-key-please-change-in-production-0123456789abcdef";
    static final String DEV_DB_PASSWORD = "replyfit";

    /** HS512 서명 키 최소 길이(512비트). JwtService가 키 길이로 알고리즘을 고른다. */
    static final int MIN_JWT_SECRET_BYTES = 64;

    public ProductionConfigValidator(
            @Value("${replyfit.jwt.secret}") String jwtSecret,
            @Value("${spring.datasource.password}") String dbPassword,
            @Value("${replyfit.cors.allowed-origins}") String corsOrigins,
            @Value("${replyfit.seed.demo}") boolean seedDemo) {

        List<String> problems = validate(jwtSecret, dbPassword, corsOrigins, seedDemo);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "운영(prod) 설정이 안전하지 않아 기동을 중단합니다. 아래를 해결한 뒤 다시 배포하세요:"
                            + System.lineSeparator() + "  - "
                            + String.join(System.lineSeparator() + "  - ", problems));
        }
    }

    /** 문제 목록을 반환한다. 비어 있으면 통과. */
    static List<String> validate(String jwtSecret, String dbPassword, String corsOrigins, boolean seedDemo) {
        List<String> problems = new ArrayList<>();

        if (DEV_JWT_SECRET.equals(jwtSecret)) {
            problems.add("JWT_SECRET이 저장소에 공개된 개발용 기본값입니다. 임의의 값으로 교체하세요"
                    + " (예: openssl rand -base64 64).");
        } else if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            problems.add("JWT_SECRET이 " + MIN_JWT_SECRET_BYTES + "바이트 미만입니다. HS512 서명에 필요한 길이입니다.");
        }

        if (DEV_DB_PASSWORD.equals(dbPassword)) {
            problems.add("DB_PASSWORD가 개발용 기본값입니다. Secrets Manager에서 주입되고 있는지 확인하세요.");
        }

        List<String> origins = corsOrigins == null ? List.of()
                : Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (origins.isEmpty()) {
            problems.add("CORS_ALLOWED_ORIGINS가 비어 있습니다. 서비스 도메인을 지정하세요.");
        } else if (origins.contains("*")) {
            problems.add("CORS_ALLOWED_ORIGINS에 와일드카드(*)를 쓸 수 없습니다."
                    + " 인증 정보를 허용하는 설정이라 모든 오리진에 열어 주는 것과 같습니다.");
        }

        if (seedDemo) {
            problems.add("REPLYFIT_SEED_DEMO가 켜져 있습니다. 운영 DB에 데모 계정과 샘플 데이터가 생성됩니다.");
        }

        return problems;
    }
}
