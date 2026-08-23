package co.replyfit.auth;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 저장소 (Redis).
 * 토큰 회전(rotation) 방식 — 재발급 시 기존 토큰은 즉시 폐기된다.
 */
@Component
public class RefreshTokenStore {

    private static final String PREFIX = "replyfit:refresh:";

    private final StringRedisTemplate redis;
    private final Duration validity;

    public RefreshTokenStore(StringRedisTemplate redis,
                             @Value("${replyfit.jwt.refresh-token-validity-days}") long days) {
        this.redis = redis;
        this.validity = Duration.ofDays(days);
    }

    public String issue(Long userId) {
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), validity);
        return token;
    }

    /** 유효하면 userId 반환 후 토큰 폐기(회전), 유효하지 않으면 null */
    public Long consume(String token) {
        String key = PREFIX + token;
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            return null;
        }
        redis.delete(key);
        return Long.valueOf(userId);
    }

    public void revoke(String token) {
        redis.delete(PREFIX + token);
    }
}
