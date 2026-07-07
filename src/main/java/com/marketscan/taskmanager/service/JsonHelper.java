package com.marketscan.taskmanager.service;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Мелкий помощник для сериализации объектов в JSON-строку
 * (нужно, например, чтобы сложить список вариантов в снимок ClickHouse).
 */
@Component
public class JsonHelper {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public String toJson(Object value) {
        if (value == null) {
            return "[]";
        }
        return mapper.writeValueAsString(value);
    }
}
