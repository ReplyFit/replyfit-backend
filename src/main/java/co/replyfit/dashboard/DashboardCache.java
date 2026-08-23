package co.replyfit.dashboard;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 대시보드 통계 캐시 (Redis).
 *
 * API 서버가 60초 TTL로 캐싱하고, Kafka 워커가 초안 생성을 마치면
 * 해당 셀러의 캐시를 무효화해 즉시 최신 통계가 보이도록 한다.
 */
@Component
public class DashboardCache {

    private static final String PREFIX = "replyfit:cache:dashboard:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public DashboardCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String get(Long userId) {
        return redis.opsForValue().get(PREFIX + userId);
    }

    public void put(Long userId, String json) {
        redis.opsForValue().set(PREFIX + userId, json, TTL);
    }

    public void evict(Long userId) {
        redis.delete(PREFIX + userId);
    }
}
