package com.marketscan.taskmanager;

import com.marketscan.taskmanager.kafka.SelectTaskMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Тестовый двойник парсера: слушает select.tasks.spb и на каждую задачу
 * отвечает результатом в select.results.spb (одна карточка), возвращая
 * task_id/set_id/stratum_id/geo эхом. Отдельная группа fake-parser.
 *
 * Только для тестов (лежит в src/test). Проверяет асинхронный контур
 * без настоящего парсера.
 */
@Component
public class FakeParserForTest {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    public FakeParserForTest(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @KafkaListener(topics = "select.tasks.spb", groupId = "fake-parser")
    void onTask(String taskJson) {
        SelectTaskMessage task = mapper.readValue(taskJson, SelectTaskMessage.class);

        String sku = "FAKE" + System.nanoTime();
        String resultJson = """
            {
              "task_id": "%s",
              "set_id": "%s",
              "stratum_id": "%s",
              "geo": "%s",
              "ok": true,
              "requested_count": %d,
              "found_count": 1,
              "cards": [
                { "sku": "%s", "name": "Фейковая карточка",
                  "price": { "price": 1000 }, "seller": { "id": "999" } }
              ]
            }
            """.formatted(task.getTaskId(), task.getSetId(), task.getStratumId(),
                task.getGeo(), task.getCount(), sku);

        kafka.send("select.results.spb", resultJson);
        System.out.println(">>> FakeParser ответил на task_id=" + task.getTaskId());
    }
}