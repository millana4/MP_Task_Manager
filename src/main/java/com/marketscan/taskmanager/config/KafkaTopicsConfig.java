package com.marketscan.taskmanager.config;

import com.marketscan.taskmanager.kafka.GeoTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Объявление Kafka-топиков. Пока — регион Санкт-Петербург.
 * Топики задач/результатов именуются по гео-коду (GeoTopics).
 * Новый регион = новая пара топиков здесь + код в GeoTopics.
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic selectTasksSpbTopic() {
        return TopicBuilder.name(GeoTopics.tasksTopic("Санкт-Петербург"))
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic selectResultsSpbTopic() {
        return TopicBuilder.name(GeoTopics.resultsTopic("Санкт-Петербург"))
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic selectResultsDlqTopic() {
        return TopicBuilder.name("select.results.dlq")
                .partitions(1).replicas(1).build();
    }
}