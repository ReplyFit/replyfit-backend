package co.replyfit.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import co.replyfit.kafka.KafkaTopics;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic inquiriesUploadedTopic() {
        return TopicBuilder.name(KafkaTopics.INQUIRIES_UPLOADED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reportsRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.REPORTS_REQUESTED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 컨슈머 공통 에러 핸들러 — 지수 백오프로 3회 재시도 후
     * Dead Letter Topic(<원본토픽>.DLT)으로 이동시킨다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
