package com.mercator.taskmanager.service;

import com.mercator.taskmanager.contract.Ozon.ExcludedCard;
import com.mercator.taskmanager.entity.CardEntity;
import com.mercator.taskmanager.entity.SetClothingEntity;
import com.mercator.taskmanager.entity.SetEntity;
import com.mercator.taskmanager.kafka.SelectTaskMessage;
import com.mercator.taskmanager.kafka.SelectTaskProducer;
import com.mercator.taskmanager.repository.CardRepository;
import com.mercator.taskmanager.repository.SetClothingRepository;
import com.mercator.taskmanager.repository.SetRepository;
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

    /**
     * Добор недостающих карточек по всем стратам всех сетов.
     * По каждой страте: если (active + stale) < count — запросить подбор на
     * недостающее, с exclude уже известных (включая dropped, чтобы не
     * подбирать повторно выбывшие sku). Партию НЕ открываем — это фоновой
     * добор, а не первичное наполнение.
     *
     * @return сколько задач добора отправлено.
     */
    public int refillDeficits() {
        List<SetEntity> sets = setRepository.findAll();
        int sent = 0;
        for (SetEntity set : sets) {
            List<SetClothingEntity> strata = stratumRepository.findBySetId(set.getId());
            for (SetClothingEntity stratum : strata) {
                int active = cardRepository
                        .findByStratumIdAndStatus(stratum.getId(), "active").size();
                int deficit = stratum.getCount() - active;
                if (deficit <= 0) {
                    continue;
                }
                SelectTaskMessage task = new SelectTaskMessage();
                task.setTaskId(UUID.randomUUID());
                task.setSetId(set.getId());
                task.setStratumId(stratum.getId());
                task.setGeo(set.getGeo());
                task.setQuery(stratum.getQuery());
                task.setCount(deficit);                       // только недостающее
                task.setIsSeasonal(stratum.getIsSeasonal());
                task.setBaseShare(stratum.getBaseShare() != null
                        ? stratum.getBaseShare().doubleValue() : null);
                task.setExclude(buildExclude(stratum.getId()));
                taskProducer.send(task);
                sent++;
                log.info("Добор страты {} ({}): недостаёт {} (active={}, need={})",
                        stratum.getId(), stratum.getQuery(), deficit, active, stratum.getCount());
            }
        }
        if (sent > 0) {
            log.info("Добор запущен: отправлено {} задач", sent);
        } else {
            log.debug("Добор: дефицитов нет, все страты укомплектованы");
        }
        return sent;
    }

    // Собрать exclude для страты. Инвариант: sku уникален в пределах СЕТА,
    // поэтому исключаем карточки всех страт сета (не только текущей), чтобы
    // парсер не подобрал sku, уже занятый в соседней страте.
    private List<ExcludedCard> buildExclude(UUID stratumId) {
        SetClothingEntity stratum = stratumRepository.findById(stratumId)
                .orElseThrow(() -> new IllegalArgumentException("Страта не найдена: " + stratumId));
        UUID setId = stratum.getSet().getId();

        List<ExcludedCard> exclude = new ArrayList<>();
        for (SetClothingEntity s : stratumRepository.findBySetId(setId)) {
            for (CardEntity c : cardRepository.findByStratumId(s.getId())) {
                ExcludedCard ex = new ExcludedCard();
                ex.setSku(c.getSku());
                ex.setName(c.getName());
                ex.setUrl(c.getUrl());
                ex.setSeller(c.getSellerId());
                ex.setCollection(null);
                exclude.add(ex);
            }
        }
        return exclude;
    }
}