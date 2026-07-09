package com.marketscan.taskmanager.service;

import com.marketscan.taskmanager.contract.Ozon.ExcludedCard;
import com.marketscan.taskmanager.entity.CardEntity;
import com.marketscan.taskmanager.entity.SetClothingEntity;
import com.marketscan.taskmanager.entity.SetEntity;
import com.marketscan.taskmanager.kafka.SelectTaskMessage;
import com.marketscan.taskmanager.kafka.SelectTaskProducer;
import com.marketscan.taskmanager.repository.CardRepository;
import com.marketscan.taskmanager.repository.SetClothingRepository;
import com.marketscan.taskmanager.repository.SetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Асинхронное наполнение сета через Kafka.
 *
 * По каждой страте формирует задачу подбора и отправляет в топик
 * (не ждёт ответа). Результаты придут асинхронно в SelectResultConsumer.
 * Открывает партию наполнения, чтобы отслеживать, когда все результаты
 * вернутся (или истечёт таймаут).
 */
@Service
public class SetFillKafkaService {

    private static final Logger log = LoggerFactory.getLogger(SetFillKafkaService.class);

    private final SetRepository setRepository;
    private final SetClothingRepository stratumRepository;
    private final SelectTaskProducer taskProducer;
    private final FillBatchRegistry batchRegistry;
    private final CardRepository cardRepository;
    // Таймаут партии — настраиваемый, по умолчанию 4 часа.
    private final Duration batchTimeout;

    public SetFillKafkaService(SetRepository setRepository,
                               SetClothingRepository stratumRepository,
                               SelectTaskProducer taskProducer,
                               FillBatchRegistry batchRegistry, CardRepository cardRepository,
                               @Value("${taskmanager.fill.batch-timeout:PT4H}") Duration batchTimeout) {
        this.setRepository = setRepository;
        this.stratumRepository = stratumRepository;
        this.taskProducer = taskProducer;
        this.batchRegistry = batchRegistry;
        this.cardRepository = cardRepository;
        this.batchTimeout = batchTimeout;
    }

    /**
     * Запустить асинхронное наполнение сета. Возвращает число отправленных
     * задач (= число страт). Сами карточки придут позже, в консьюмер.
     */
    public int startFill(UUID setId) {
        SetEntity set = setRepository.findById(setId)
                .orElseThrow(() -> new IllegalArgumentException("Сет не найден: " + setId));

        List<SetClothingEntity> strata = stratumRepository.findBySetId(setId);
        if (strata.isEmpty()) {
            throw new IllegalArgumentException("У сета нет страт: " + setId);
        }

        // Открываем партию: ждём столько результатов, сколько страт.
        batchRegistry.open(setId, strata.size(), batchTimeout);

        // Рассылаем задачи по стратам.
        int sent = 0;
        for (SetClothingEntity stratum : strata) {
            SelectTaskMessage task = new SelectTaskMessage();
            task.setTaskId(UUID.randomUUID());
            task.setSetId(setId);
            task.setStratumId(stratum.getId());
            task.setGeo(set.getGeo());
            task.setQuery(stratum.getQuery());
            task.setCount(stratum.getCount());
            task.setIsSeasonal(stratum.getIsSeasonal());
            task.setBaseShare(stratum.getBaseShare() != null
                    ? stratum.getBaseShare().doubleValue() : null);
            // exclude = уже собранные карточки страты. При первом наполнении их нет
            // (пустой список), при доборе — парсер не повторит собранное.
            task.setExclude(buildExclude(stratum.getId()));

            taskProducer.send(task);
            sent++;
        }

        log.info("Наполнение сета {} запущено: отправлено {} задач", setId, sent);
        return sent;
    }

    // Собрать exclude для страты — карточки, которые уже есть (по sku/seller).
    private List<ExcludedCard> buildExclude(UUID stratumId) {
        List<CardEntity> existing = cardRepository.findByStratumId(stratumId);
        List<ExcludedCard> exclude = new ArrayList<>();
        for (CardEntity c : existing) {
            ExcludedCard ex = new ExcludedCard();
            ex.setSku(c.getSku());
            ex.setName(c.getName());
            ex.setUrl(c.getUrl());
            ex.setSeller(c.getSellerId());     // seller = id магазина
            // collection в exclude не нужен парсеру для отсева дублей (он по seller/sku)
            ex.setCollection(null);
            exclude.add(ex);
        }
        return exclude;
    }
}