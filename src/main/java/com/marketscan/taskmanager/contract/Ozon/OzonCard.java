package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Агрегированная карточка товара Ozon. Соответствует OzonCard из парсера.
 *
 * Поля, которые парсер не нашёл на странице, приходят null — это нормально,
 * набор виджетов у разных товаров отличается. Поэтому типы — обёртки
 * (Integer, Double, String), способные быть пустыми, а не примитивы.
 *
 * price, seller, location в парсере всегда объект (не null), characteristics
 * и variants — всегда коллекция (в худшем случае пустая).
 */
@Data
public class OzonCard {

    // Идентификация
    private String sku;         // артикул товара
    private String url;         // URL карточки
    private String parsedAt;    // parsed_at — дата и время парсинга (ISO-строка)
    private String name;        // наименование
    private String brand;       // бренд

    // Цены и остаток
    private OzonPrice price;
    private Integer quantity;   // количество/остаток, если указано

    // Оценки (парсер отдаёт строками: "4.9", "103")
    private String rating;
    private String reviewsCount;  // reviews_count

    // Характеристики: плоский словарь {название: значение}.
    // Здесь среди прочего лежит "Коллекция", материал, состав, размер.
    private Map<String, String> characteristics;

    // Описание и категория
    private String description;
    private String category;
    private String categoryPath;    // category_path — путь иерархии

    // Варианты (размерная сетка)
    private String variantsAspect;         // variants_aspect — по чему варианты
    private List<OzonVariant> variants;

    // Продавец и регион выдачи
    private OzonSeller seller;
    private OzonLocation location;
}