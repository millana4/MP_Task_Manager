package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

/**
 * Ответ эндпоинтов card/by-id и card/by-url — обёртка над одной карточкой.
 * Соответствует CardResponse из парсера. Поле card может быть null,
 * если распарсить не удалось.
 */
@Data
public class CardResponse {
    private Boolean ok;
    private OzonCard card;
}