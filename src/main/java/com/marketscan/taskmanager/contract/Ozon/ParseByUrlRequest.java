package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

/** Запрос на парсинг карточки по URL. Эндпоинт /api/v1/ozon/card/by-url. */
@Data
public class ParseByUrlRequest {
    private String url;   // URL карточки товара Ozon
}
