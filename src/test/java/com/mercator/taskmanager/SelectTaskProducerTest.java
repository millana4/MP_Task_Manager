package com.mercator.taskmanager;

import com.mercator.taskmanager.kafka.SelectTaskProducer;
import com.mercator.taskmanager.kafka.SelectTaskMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

/**
 * Отправляет одну задачу подбора в select.tasks (реальная Kafka).
 * Проверяем факт отправки без ошибок; содержимое топика посмотрим
 * консольной командой Kafka после теста.
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class SelectTaskProducerTest {

    @Autowired
    SelectTaskProducer producer;

    @Test
    void sendsSelectTask() {
        SelectTaskMessage task = new SelectTaskMessage();
        task.setTaskId(UUID.randomUUID());
        task.setStratumId(UUID.randomUUID());
        task.setGeo("Санкт-Петербург");
        task.setQuery("Трусы женские");
        task.setCount(4);
        task.setIsSeasonal(false);
        task.setBaseShare(null);
        task.setExclude(List.of());

        producer.send(task);

        System.out.println(">>> Задача отправлена, task_id=" + task.getTaskId());
        // Сообщение ушло в топик. Проверим содержимое консолью Kafka.
    }
}