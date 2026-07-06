package com.marketscan.taskmanager.contract.Ozon;

/**
 * Цены товара в рублях, числом (без символа валюты и разделителей).
 * Соответствует OzonPrice из парсера.
 *
 * Поля называются в camelCase (соглашение Java), а из JSON приходят в
 * snake_case (card_price, original_price) — перевод делает глобальная
 * настройка Jackson SNAKE_CASE.
 */
public class OzonPrice {

    private Double cardPrice;       // card_price — цена с Ozon Картой (со скидкой)
    private Double price;           // текущая цена
    private Double originalPrice;   // original_price — цена без скидки (зачёркнутая)

    public Double getCardPrice() {
        return cardPrice;
    }

    public void setCardPrice(Double cardPrice) {
        this.cardPrice = cardPrice;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(Double originalPrice) {
        this.originalPrice = originalPrice;
    }
}