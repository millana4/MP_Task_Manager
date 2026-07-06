package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

import java.util.List;

/**
 * Ответ эндпоинта подбора. Соответствует SelectionResponse из парсера.
 *
 * found_count может быть меньше requested_count — это штатный исход
 * (не хватило карточек нужного сезона, строгие пороги качества и т.п.).
 */
@Data
public class SelectionResponse {
    private Boolean ok;
    private List<OzonCard> cards;    // полные карточки подобранных товаров
    private Integer requestedCount;  // requested_count — сколько запрашивалось
    private Integer foundCount;      // found_count — сколько реально подобрано
}