package com.marketscan.taskmanager.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Отправляет необработанные сообщения в dead-letter топик для разбора.
 * Причина кладётся в заголовок сообщения (для диагностики).
 */
@Component
public class DlqProducer {

    private static final Logger log = LoggerFactory.getLogger(DlqProducer.class);
    private static final String DLQ_TOPIC = "select.results.dlq";

    private final KafkaTemplate<String, String> kafka;

    public DlqProducer(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    public void sendToDlq(String originalJson, String reason) {
        // Оборачиваем: причина + исходное сообщение (чтобы потом разобрать вручную).
        String wrapped = "{\"reason\":\"" + reason.replace("\"", "'")
                + "\",\"original\":" + jsonString(originalJson) + "}";
        kafka.send(DLQ_TOPIC, wrapped);
        log.warn("Сообщение отправлено в DLQ: причина={}", reason);
    }

    // Оборачивает произвольную строку как JSON-строку (экранирование).
    private String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}