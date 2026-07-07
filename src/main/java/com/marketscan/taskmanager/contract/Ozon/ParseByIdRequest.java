package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

/** Запрос на парсинг карточки по артикулу. Эндпоинт /api/v1/ozon/card/by-id. */
@Data
public class ParseByIdRequest {
    private String sku;   // артикул (SKU) товара Ozon — число из адреса карточки
}