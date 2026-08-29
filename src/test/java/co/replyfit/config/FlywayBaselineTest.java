package co.replyfit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.replyfit.user.User;

/**
 * 베이스라인 마이그레이션이 JPA 엔티티와 일치하는지 실제 PostgreSQL로 검증한다(#57).
 *
 * <p>이 테스트의 핵심은 단언문이 아니라 <b>컨텍스트가 뜬다는 사실 자체</b>다.
 * 빈 컨테이너에 Flyway가 V1을 적용하고, 그 위에서 Hibernate {@code ddl-auto=validate}가
 * 통과해야만 컨텍스트가 올라온다. 베이스라인에 컬럼 하나가 빠지거나 타입이 어긋나면
 * 여기서 기동이 실패한다.
 *
 * <p>운영에서 이 검증이 실패하면 배포가 막히는 것이므로, 그 실패를 CI로 앞당기는 것이 목적이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Flyway 베이스라인 — 마이그레이션과 엔티티 정합성")
class FlywayBaselineTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("빈 DB에 V1이 적용되고 성공으로 기록된다")
    void baselineApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);

        assertThat(versions).contains("1");
    }

    @Test
    @DisplayName("엔티티가 기대하는 테이블이 모두 생성된다")
    void allTablesCreated() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains("users", "subscriptions", "policies", "inquiries",
                "answer_drafts", "reviews", "weekly_reports");
    }

    @Test
    @DisplayName("마이그레이션으로 만든 스키마에 JPA 저장·조회가 동작한다")
    void jpaRoundTripWorks() {
        User user = em.persistFlushFind(new User("flyway@replyfit.co", "hashed", "테스터", "테스트상점"));

        assertThat(user.getId()).isNotNull();
        assertThat(em.find(User.class, user.getId()).getEmail()).isEqualTo("flyway@replyfit.co");
    }

    @Test
    @DisplayName("enum 값을 강제하는 체크 제약이 살아 있다")
    void checkConstraintsEnforced() {
        List<String> checks = jdbc.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints"
                        + " WHERE table_schema = 'public' AND constraint_type = 'CHECK'"
                        + " AND constraint_name LIKE 'ck_%'",
                String.class);

        assertThat(checks).contains("ck_inquiries_status", "ck_inquiries_category",
                "ck_reviews_sentiment", "ck_subscriptions_plan", "ck_subscriptions_status",
                "ck_policies_type", "ck_weekly_reports_status");
    }
}
