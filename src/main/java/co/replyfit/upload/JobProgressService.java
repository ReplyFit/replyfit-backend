package co.replyfit.upload;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 업로드/리포트 작업 진행률 추적 (Redis).
 *
 * API 서버가 작업을 생성하고 Kafka 워커가 진행률을 갱신하면
 * 프론트엔드가 폴링으로 실시간 진행 상황을 표시한다.
 */
@Service
public class JobProgressService {

    private static final String PREFIX = "replyfit:job:";
    private static final Duration TTL = Duration.ofHours(6);

    public enum JobStatus {
        PROCESSING, COMPLETED, FAILED
    }

    public record JobState(String id, String type, String status, long total, long processed, long failed) {
    }

    private final StringRedisTemplate redis;

    public JobProgressService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String create(String type, long total) {
        String jobId = UUID.randomUUID().toString();
        String key = PREFIX + jobId;
        redis.opsForHash().putAll(key, Map.of(
                "type", type,
                "status", JobStatus.PROCESSING.name(),
                "total", String.valueOf(total),
                "processed", "0",
                "failed", "0"));
        redis.expire(key, TTL);
        return jobId;
    }

    public void incrementProcessed(String jobId) {
        redis.opsForHash().increment(PREFIX + jobId, "processed", 1);
    }

    public void incrementFailed(String jobId) {
        redis.opsForHash().increment(PREFIX + jobId, "failed", 1);
    }

    public void complete(String jobId) {
        redis.opsForHash().put(PREFIX + jobId, "status", JobStatus.COMPLETED.name());
    }

    public void fail(String jobId) {
        redis.opsForHash().put(PREFIX + jobId, "status", JobStatus.FAILED.name());
    }

    public JobState get(String jobId) {
        Map<Object, Object> entries = redis.opsForHash().entries(PREFIX + jobId);
        if (entries.isEmpty()) {
            return null;
        }
        return new JobState(
                jobId,
                (String) entries.getOrDefault("type", "unknown"),
                (String) entries.getOrDefault("status", JobStatus.PROCESSING.name()),
                Long.parseLong((String) entries.getOrDefault("total", "0")),
                Long.parseLong((String) entries.getOrDefault("processed", "0")),
                Long.parseLong((String) entries.getOrDefault("failed", "0")));
    }
}
