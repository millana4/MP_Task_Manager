package com.marketscan.taskmanager.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Отправляет задачи обхода в parse.tasks.<гео>. Формат — JSON snake_case,
 * как у задач подбора (единый контракт с парсером).
 */
@Component
public class ParseTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskProducer.class);

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    public ParseTaskProducer(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    public void send(ParseTaskMessage task) {
        String json = mapper.writeValueAsString(task);
        String topic = "parse.tasks." + GeoTopics.codeOf(task.getGeo());
        kafka.send(topic, json);
        log.info("Задача обхода отправлена в {}: task_id={}, card_id={}, sku={}",
                topic, task.getTaskId(), task.getCardId(), task.getSku());
    }
}
