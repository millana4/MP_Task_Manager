package com.marketscan.taskmanager.clickhouse;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Снимок карточки — строка таблицы card_snapshot в ClickHouse.
 * Медленно меняющиеся данные: название, описание, характеристики,
 * варианты, продавец, вектор. Обновляется через ReplacingMergeTree.
 */
@Data
@Builder
public class CardSnapshot {
    private UUID cardId;
    private String sku;
    private OffsetDateTime parsedAt;

    private String name;
    private String description;
    private String brand;
    private String category;
    private String categoryPath;

    private Map<String, String> characteristics;
    private String variantsAspect;
    private String variants;          // JSON-строка со списком вариантов

    private String sellerId;
    private String sellerName;
    private String sellerLegalName;
    private String sellerOgrn;

    private List<Float> embedding;    // вектор; пока может быть пустым
}
