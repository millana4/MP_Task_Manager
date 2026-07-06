package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

import java.util.List;

/**
 * Одна страта на вход эндпоинта подбора /api/v1/ozon/select.
 * Соответствует StratumRequest из парсера.
 *
 * Обязательны query, count, isSeasonal. baseShare обязателен, только если
 * isSeasonal=true (доля базовой коллекции). exclude — уже имеющиеся карточки.
 */
@Data
public class StratumRequest {
    private String query;                 // поисковый запрос (что писать в поиск Ozon)
    private Integer count;                // сколько карточек нужно (итоговое)
    private Boolean isSeasonal;           // is_seasonal — нужен ли сезонный сплит
    private Double baseShare;             // base_share — доля базы (при is_seasonal=true)
    private List<ExcludedCard> exclude;   // уже подобранные карточки (не дублировать)
}