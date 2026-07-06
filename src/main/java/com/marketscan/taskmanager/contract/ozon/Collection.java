package com.marketscan.taskmanager.contract.ozon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Тип коллекции карточки — как его классифицирует парсер.
 *
 * Значения соответствуют enum Collection в парсере (base / spring_summer /
 * autumn_winter). Парсер уже сам определяет коллекцию по характеристике
 * «Коллекция», поэтому таск-менеджеру дублировать эту логику не нужно.
 *
 * В JSON значения приходят строками в нижнем регистре ("base"), а имена
 * констант Java — в верхнем (BASE). Поле jsonValue хранит внешнюю строку;
 * @JsonValue велит Jackson использовать её при чтении и записи, @JsonCreator —
 * при разборе входящего JSON. Так снимается несовпадение регистра.
 */
public enum Collection {

    BASE("base"),
    SPRING_SUMMER("spring_summer"),
    AUTUMN_WINTER("autumn_winter");

    private final String jsonValue;

    Collection(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /** Значение, как оно выглядит в JSON парсера (для сериализации). */
    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    /** Разбор строки из JSON в константу enum (для десериализации). */
    @JsonCreator
    public static Collection fromJson(String value) {
        for (Collection c : values()) {
            if (c.jsonValue.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Неизвестная коллекция: " + value);
    }
}