package com.marketscan.taskmanager;

import com.marketscan.taskmanager.entity.SetClothingEntity;
import com.marketscan.taskmanager.entity.SetEntity;
import com.marketscan.taskmanager.repository.CardRepository;
import com.marketscan.taskmanager.repository.SetClothingRepository;
import com.marketscan.taskmanager.repository.SetRepository;
import com.marketscan.taskmanager.service.FillBatch;
import com.marketscan.taskmanager.service.FillBatchRegistry;
import com.marketscan.taskmanager.service.SetFillKafkaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тест полного асинхронного контура наполнения через Kafka.
 *
 * Роль парсера играет FakeParserForTest (отдельный тестовый компонент):
 * слушает select.tasks.spb и на каждую задачу отвечает результатом
 * в select.results.spb, возвращая task_id/set_id/stratum_id эхом.
 *
 * Проверяем цепочку: запуск наполнения → задачи в топик → фейк-парсер
 * ответил → консьюмер записал карточки и отметил партию → 2/2 получено.
 *
 * Требует запущенных Kafka, Postgres, ClickHouse. Настоящий парсер НЕ нужен.
 */
@SpringBootTest
class AsyncFillFlowTest {

    @Autowired SetRepository setRepository;
    @Autowired SetClothingRepository stratumRepository;
    @Autowired CardRepository cardRepository;
    @Autowired SetFillKafkaService fillKafkaService;
    @Autowired FillBatchRegistry batchRegistry;

    @Test
    void fullAsyncFillFlow() throws InterruptedException {
        // 1. Сет с двумя стратами.
        SetEntity set = new SetEntity();
        set.setMarketplace("ozon");
        set.setCategory("clothing");
        set.setGeo("Санкт-Петербург");
        SetEntity savedSet = setRepository.save(set);

        createStratum(savedSet, "Рубашка", "Рубашка женская");
        createStratum(savedSet, "Носки", "Носки мужские");

        // Пауза, чтобы оба консьюмера (реальный + фейк-парсер) успели
        // подписаться на свои топики до отправки задач.
        Thread.sleep(5000);

        // 2. Запускаем асинхронное наполнение.
        int sent = fillKafkaService.startFill(savedSet.getId());
        assertEquals(2, sent, "Должны уйти 2 задачи (по числу страт)");

        // 3. Ждём, пока фейк-парсер ответит и консьюмер отметит оба результата.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            FillBatch batch = batchRegistry.find(savedSet.getId());
            assertNotNull(batch, "Партия должна существовать");
            assertTrue(batch.allReceived(),
                    "Оба результата должны прийти: получено "
                            + batch.getReceivedResults() + "/" + batch.getTotalTasks());
        });

        // 4. Карточки записаны в Postgres (по одной на страту от фейк-парсера).
        List<SetClothingEntity> strata = stratumRepository.findBySetId(savedSet.getId());
        int totalCards = strata.stream()
                .mapToInt(s -> cardRepository.findByStratumId(s.getId()).size())
                .sum();
        assertEquals(2, totalCards, "Должно записаться 2 карточки");

        // 5. Убираем за собой.
        setRepository.deleteById(savedSet.getId());
    }

    private void createStratum(SetEntity set, String item, String query) {
        SetClothingEntity s = new SetClothingEntity();
        s.setSet(set);
        s.setItem(item);
        s.setQuery(query);
        s.setCount(1);
        s.setGender("f");
        s.setLayer((short) 1);
        s.setIsSeasonal(false);
        s.setBaseShare(null);
        stratumRepository.save(s);
    }
}