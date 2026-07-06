package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

/**
 * Регион, в контексте которого Ozon отдал страницу (цены/наличие).
 * Определяется по IP окружения парсера. Соответствует OzonLocation из парсера.
 */
@Data
public class OzonLocation {
    private String city;         // город
    private String country;      // страна
    private String countryCode;  // country_code — код страны (например, RUS)
    private Integer areaId;      // area_id — ID региона Ozon
    private String fias;         // ФИАС-идентификатор региона
    private String timezone;     // часовой пояс (например, UTC+3)
}
