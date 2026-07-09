package com.marketscan.taskmanager.kafka;

import com.marketscan.taskmanager.entity.SetClothingEntity;
import com.marketscan.taskmanager.repository.SetClothingRepository;
import com.marketscan.taskmanager.service.CardWriteService;
import com.marketscan.taskmanager.service.FillBatchRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Читает результаты подбора из топиков select.results.* (все регионы
 * по шаблону) и раскладывает карточки через CardWriteService.
 *
 * По topicPattern один листенер ловит топики результатов всех регионов,
 * включая будущие: добавили регион — консьюмер сам подхватит его топик,
 * код менять не нужно.
 *
 * Привязка результата к страте — по stratum_id из самого сообщения
 * (парсер вернул его эхом). geo — тоже из сообщения, для замера.
 */
@Component
public class SelectResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(SelectResultConsumer.class);

    private final SetClothingRepository stratumRepository;
    private final CardWriteService cardWriteService;
    private final FillBatchRegistry batchRegistry;
    private final DlqProducer dlqProducer;

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    public SelectResultConsumer(SetClothingRepository stratumRepository,
                                CardWriteService cardWriteService,
                                FillBatchRegistry batchRegistry, DlqProducer dlqProducer) {
        this.stratumRepository = stratumRepository;
        this.cardWriteService = cardWriteService;
        this.batchRegistry = batchRegistry;
        this.dlqProducer = dlqProducer;
    }

    /**
     * Слушает все топики результатов подбора по шаблону. Spring вызывает
     * метод на каждое пришедшее сообщение.
     */
    @KafkaListener(topicPattern = GeoTopics.RESULTS_PATTERN, groupId = "taskmanager")
    public void onResult(String json) {
        SelectResultMessage result;
        try {
            result = mapper.readValue(json, SelectResultMessage.class);
        } catch (Exception e) {
            dlqProducer.sendToDlq(json, "Не разобрать результат: " + e.getMessage());
            return;  // битое сообщение пропускаем, не застреваем
        }

        log.info("Результат подбора: task_id={}, stratum_id={}, ok={}, найдено={}",
                result.getTaskId(), result.getStratumId(),
                result.getOk(), result.getFoundCount());

        if (Boolean.FALSE.equals(result.getOk())) {
            log.warn("Подбор task_id={} завершился ошибкой: {}",
                    result.getTaskId(), result.getError());
            return;
        }

        if (result.getCards() == null || result.getCards().isEmpty()) {
            log.info("Подбор task_id={}: карточек нет", result.getTaskId());
            return;
        }

        // Находим страту по stratum_id из сообщения (парсер вернул эхом).
        Optional<SetClothingEntity> stratumOpt =
                stratumRepository.findById(result.getStratumId());
        if (stratumOpt.isEmpty()) {
            log.error("Страта {} не найдена — результат task_id={} отброшен",
                    result.getStratumId(), result.getTaskId());
            return;
        }
        SetClothingEntity stratum = stratumOpt.get();

        // Раскладываем каждую карточку. Устойчиво: сбой одной — лог и дальше.
        int saved = 0;
        for (var card : result.getCards()) {
            try {
                cardWriteService.saveCard(stratum, result.getGeo(), card);
                saved++;
            } catch (Exception e) {
                log.error("Карточка sku={} не записана (task_id={}): {}",
                        card.getSku(), result.getTaskId(), e.getMessage(), e);
            }
        }
        log.info("Результат task_id={}: записано {} карточек", result.getTaskId(), saved);
        // Отмечаем результат в партии наполнения (по set_id из сообщения).
        batchRegistry.recordResult(result.getSetId(), saved);
    }
}