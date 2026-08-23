package co.replyfit.auth;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import co.replyfit.common.ApiException;

/**
 * 로그인 시도 레이트 리미터 (Redis).
 * 10분 내 5회 실패 시 잠금 — 크리덴셜 스터핑 방어.
 */
@Component
public class LoginRateLimiter {

    private static final String PREFIX = "replyfit:login-fail:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkAllowed(String email) {
        String value = redis.opsForValue().get(PREFIX + email);
        if (value != null && Integer.parseInt(value) >= MAX_ATTEMPTS) {
            throw ApiException.tooManyRequests("로그인 시도가 너무 많습니다. 10분 후 다시 시도해 주세요.");
        }
    }

    public void recordFailure(String email) {
        String key = PREFIX + email;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, WINDOW);
        }
    }

    public void reset(String email) {
        redis.delete(PREFIX + email);
    }
}
