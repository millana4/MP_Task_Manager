package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

/**
 * Уже подобранная карточка (из нашей базы), передаётся парсеру в исключения,
 * чтобы он не подобрал её повторно. Соответствует ExcludedCard из парсера.
 *
 * Обязателен только sku. Остальные поля помогают парсеру отсеять дубли
 * (по имени/url) и не брать карточку того же бренда. collection — тип
 * коллекции уже имеющейся карточки, нужен парсеру для расчёта остатка по слотам.
 */
@Data
public class ExcludedCard {
    private String sku;             // артикул
    private String name;            // полное название
    private String url;             // URL/slug - проверка дублей
    private String selle;           // магазин - у новых должен отличаться
    private Collection collection;  // base / spring_summer / autumn_winter
}
