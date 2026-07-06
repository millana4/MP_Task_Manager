package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

/** Вариант товара (обычно размер) — отдельный sku. Соответствует OzonVariant. */
@Data
public class OzonVariant {
    private String sku;           // артикул варианта
    private String value;         // значение (например, '48 RU / L')
    private String availability;  // доступность (inStock/outOfStock)
    private Double price;         // цена варианта (руб., число)
}
