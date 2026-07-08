package com.marketscan.taskmanager;

import com.marketscan.taskmanager.entity.SetClothingEntity;
import com.marketscan.taskmanager.entity.SetEntity;
import com.marketscan.taskmanager.repository.CardRepository;
import com.marketscan.taskmanager.repository.SetClothingRepository;
import com.marketscan.taskmanager.repository.SetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тест приёмной цепочки: кладём фейковый результат подбора в
 * select.results.spb (как будто прислал парсер) и проверяем, что
 * консьюмер прочитал его и записал карточку в Postgres.
 *
 * Требует запущенных Kafka, Postgres, ClickHouse. Парсер НЕ нужен —
 * его роль играем сами, отправляя готовый JSON результата.
 */
@SpringBootTest
class SelectResultConsumerTest {

    @Autowired SetRepository setRepository;
    @Autowired SetClothingRepository stratumRepository;
    @Autowired CardRepository cardRepository;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void consumesResultAndWritesCard() {
        // 1. Готовим сет со стратой — карточке нужно к чему привязаться.
        SetEntity set = new SetEntity();
        set.setMarketplace("ozon");
        set.setCategory("clothing");
        set.setGeo("Санкт-Петербург");
        SetEntity savedSet = setRepository.save(set);

        SetClothingEntity stratum = new SetClothingEntity();
        stratum.setSet(savedSet);
        stratum.setItem("Рубашка");
        stratum.setQuery("Рубашка женская");
        stratum.setCount(3);
        stratum.setGender("f");
        stratum.setLayer((short) 2);
        stratum.setIsSeasonal(true);
        stratum.setBaseShare(new java.math.BigDecimal("0.500"));
        SetClothingEntity savedStratum = stratumRepository.save(stratum);

        UUID taskId = UUID.randomUUID();
        String uniqueSku = "TEST" + System.currentTimeMillis();  // уникальный, чтобы не конфликтовать

        // 2. Фейковый результат подбора — как его пришлёт парсер (snake_case).
        //    Одна карточка, с task_id/stratum_id/geo эхом.
        String resultJson = """
            {
              "task_id": "%s",
              "stratum_id": "%s",
              "geo": "Санкт-Петербург",
              "ok": true,
              "requested_count": 3,
              "found_count": 1,
              "cards": [
                {
                  "sku": "%s",
                  "url": "https://www.ozon.ru/product/test-%s/",
                  "name": "Рубджа тестовая",
                  "brand": "TestBrand",
                  "price": { "card_price": 1282, "price": 1425, "original_price": 10000 },
                  "quantity": 85,
                  "rating": "4.9",
                  "reviews_count": "5",
                  "characteristics": { "Материал": "Хлопок", "Коллекция": "Весна-лето 2026" },
                  "category": "Блузы и рубашки",
                  "variants_aspect": "Цвет",
                  "variants": [ { "sku": "111", "value": "белый", "availability": "inStock", "price": 1282 } ],
                  "seller": { "id": "554625", "name": "Zella", "legal_name": "ИП Калинин", "ogrn": null }
                }
              ]
            }
            """.formatted(taskId, savedStratum.getId(), uniqueSku, uniqueSku);

        // 3. Кладём результат в топик — консьюмер должен его подхватить.
        kafkaTemplate.send("select.results.spb", resultJson);

        // 4. Ждём (до 20 сек), пока консьюмер асинхронно запишет карточку.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<com.marketscan.taskmanager.entity.CardEntity> cards =
                    cardRepository.findByStratumId(savedStratum.getId());
            assertFalse(cards.isEmpty(), "Консьюмер должен был записать карточку");
            assertEquals(uniqueSku, cards.get(0).getSku());
            assertEquals("Рубджа тестовая", cards.get(0).getName());
        });

        // 5. Убираем за собой.
        setRepository.deleteById(savedSet.getId());
    }
}
