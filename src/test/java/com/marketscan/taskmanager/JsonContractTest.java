package com.marketscan.taskmanager;

import com.marketscan.taskmanager.contract.ozon.Collection;
import com.marketscan.taskmanager.contract.ozon.OzonPrice;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Проверяет фундамент контрактов до создания остальных классов:
 * 1) что snake_case из JSON парсера корректно ложится в camelCase-поля Java;
 * 2) что значение коллекции ("base") читается в enum Collection.
 *
 * Jackson 3: ObjectMapper неизменяем, стратегия задаётся через builder
 * при создании JsonMapper (аналог настройки в application.yml).
 */
class JsonContractTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void snakeCaseFieldsMapToCamelCase() {
        String json = """
            {
              "card_price": 1330,
              "price": 1400,
              "original_price": 7500
            }
            """;

        OzonPrice parsed = mapper.readValue(json, OzonPrice.class);

        assertEquals(1330.0, parsed.getCardPrice());
        assertEquals(1400.0, parsed.getPrice());
        assertEquals(7500.0, parsed.getOriginalPrice());
    }

    @Test
    void collectionEnumReadsFromLowercaseJson() {
        Collection parsed = mapper.readValue("\"base\"", Collection.class);
        assertEquals(Collection.BASE, parsed);
    }
}