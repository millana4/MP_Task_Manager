package com.marketscan.taskmanager.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Отправляет задачи подбора в топик select.tasks.
 *
 * Сообщение сериализуем в JSON snake_case вручную (как для RestClient),
 * чтобы формат совпадал с тем, что ждёт Python-парсер, и не зависел
 * от Java-специфики.
 */
@Component
public class SelectTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(SelectTaskProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    // Свой snake_case-маппер — тот же приём, что в RestClient.
    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    public SelectTaskProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Отправить задачу подбора. Ключ партиционирования — geo: задачи
     * одного региона идут в одну партицию (пригодится, когда парсеры
     * будут привязаны к регионам/IP). Пока это просто равномерное
     * распределение по гео.
     */
    public void send(SelectTaskMessage task) {
        String json = mapper.writeValueAsString(task);

        // Топик выбираем по гео задачи: питерские задачи -> select.tasks.spb.
        // Так парсер нужного региона читает только свой топик.
        String topic = GeoTopics.tasksTopic(task.getGeo());

        kafkaTemplate.send(topic, json);

        log.info("Задача подбора отправлена в {}: task_id={}, stratum_id={}, query='{}', geo={}",
                topic, task.getTaskId(), task.getStratumId(), task.getQuery(), task.getGeo());
    }
}