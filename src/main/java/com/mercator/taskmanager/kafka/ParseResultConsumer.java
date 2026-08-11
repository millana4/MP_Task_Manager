package com.mercator.taskmanager.kafka;

import com.mercator.taskmanager.clickhouse.Measurement;
import com.mercator.taskmanager.clickhouse.MeasurementRepository;
import com.mercator.taskmanager.service.CardLifecycleService;
import com.mercator.taskmanager.service.MonitoringCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Принимает результаты обхода из parse.results.* (все регионы по шаблону).
 * На успех: дописывает свежий замер в ClickHouse + карточка active.
 * На неудачу: помечает карточку через жизненный цикл (failed_attempts++).
 *
 * ВАЖНО: обход НЕ создаёт новую карточку и НЕ пишет снимок каждый раз —
 * только замер (пульс цен/наличия). Снимок можно обновлять реже (отдельно).
 */
@Component
public class ParseResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParseResultConsumer.class);

    private final MeasurementRepository measurementRepository;
    private final CardLifecycleService lifecycle;
    private final MonitoringCycle monitoringCycle;

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    public ParseResultConsumer(MeasurementRepository measurementRepository,
                               CardLifecycleService lifecycle,
                               MonitoringCycle monitoringCycle) {
        this.measurementRepository = measurementRepository;
        this.lifecycle = lifecycle;
        this.monitoringCycle = monitoringCycle;
    }

    @KafkaListener(topicPattern = "parse\\.results\\..*", groupId = "taskmanager")
    public void onResult(String json) {
        ParseResultMessage result;
        try {
            result = mapper.readValue(json, ParseResultMessage.class);
        } catch (Exception e) {
            log.error("Не разобрать результат обхода: {}", e.getMessage());
            return;
        }

        // Карточка не ответила — фиксируем неудачу.
        if (Boolean.FALSE.equals(result.getOk()) || result.getCard() == null) {
            log.warn("Обход card_id={} неуспешен: {}", result.getCardId(), result.getError());
            lifecycle.markFailure(result.getCardId());
            monitoringCycle.recordResult();
            return;
        }

        // Успех: дописываем свежий замер.
        var card = result.getCard();
        Measurement m = Measurement.builder()
                .cardId(result.getCardId())
                .sku(result.getSku())
                .parsedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))
                .geo(result.getGeo())
                .cardPrice(card.getPrice() != null ? card.getPrice().getCardPrice() : null)
                .price(card.getPrice() != null ? card.getPrice().getPrice() : null)
                .originalPrice(card.getPrice() != null ? card.getPrice().getOriginalPrice() : null)
                .quantity(card.getQuantity())
                .rating(parseFloat(card.getRating()))
                .reviewsCount(parseInt(card.getReviewsCount()))
                .build();
        measurementRepository.insert(m);

        lifecycle.markSuccess(result.getCardId());
        monitoringCycle.recordResult();
        log.info("Обход card_id={}: замер записан", result.getCardId());
    }

    private Float parseFloat(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Float.parseFloat(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
