package com.marketscan.taskmanager.service;

import com.marketscan.taskmanager.contract.Ozon.OzonCard;
import com.marketscan.taskmanager.contract.Ozon.SelectionResponse;
import com.marketscan.taskmanager.contract.Ozon.StratumRequest;
import com.marketscan.taskmanager.client.OzonParserClient;
import com.marketscan.taskmanager.entity.SetClothingEntity;
import com.marketscan.taskmanager.entity.SetEntity;
import com.marketscan.taskmanager.repository.SetClothingRepository;
import com.marketscan.taskmanager.repository.SetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Оркестрация синхронного наполнения сета: обходит страты сета,
 * по каждой запрашивает у парсера подбор карточек (прямой HTTP)
 * и передаёт результат в CardWriteService для записи в базы.
 *
 * Сам записью не занимается — только координирует обход и вызовы.
 * Устойчив к сбою отдельной страты: логирует ошибку и продолжает
 * с остальными, а не прерывает наполнение целиком.
 *
 * Это синхронный путь, оставленный для ручной отладки. Основной
 * (асинхронный) путь наполнения идёт через Kafka: там задачи уходят
 * в топик, а результаты принимает консьюмер, использующий тот же
 * CardWriteService. Логика записи карточки у обоих путей общая.
 */
@Service
public class SetFillService {

    private static final Logger log = LoggerFactory.getLogger(SetFillService.class);

    private final SetRepository setRepository;
    private final SetClothingRepository stratumRepository;
    private final OzonParserClient parserClient;
    private final CardWriteService cardWriteService;

    public SetFillService(SetRepository setRepository,
                          SetClothingRepository stratumRepository,
                          OzonParserClient parserClient,
                          CardWriteService cardWriteService) {
        this.setRepository = setRepository;
        this.stratumRepository = stratumRepository;
        this.parserClient = parserClient;
        this.cardWriteService = cardWriteService;
    }

    /** Наполнить сет. Возвращает, сколько карточек всего записано. */
    public int fillSet(UUID setId) {
        SetEntity set = setRepository.findById(setId)
                .orElseThrow(() -> new IllegalArgumentException("Сет не найден: " + setId));

        List<SetClothingEntity> strata = stratumRepository.findBySetId(setId);
        log.info("Наполнение сета {}: {} страт", setId, strata.size());

        int totalCards = 0;
        for (SetClothingEntity stratum : strata) {
            try {
                totalCards += fillStratum(set, stratum);
            } catch (Exception e) {
                log.error("Страта '{}' (id={}) не наполнена: {}",
                        stratum.getItem(), stratum.getId(), e.getMessage(), e);
            }
        }
        log.info("Наполнение сета {} завершено: {} карточек", setId, totalCards);
        return totalCards;
    }

    /** Наполнить одну страту: позвать парсер и разложить карточки. */
    private int fillStratum(SetEntity set, SetClothingEntity stratum) {
        StratumRequest request = new StratumRequest();
        request.setQuery(stratum.getQuery());
        request.setCount(stratum.getCount());
        request.setIsSeasonal(stratum.getIsSeasonal());
        request.setBaseShare(stratum.getBaseShare() != null
                ? stratum.getBaseShare().doubleValue() : null);
        request.setExclude(List.of());

        log.info("Страта '{}': запрос подбора {} карточек", stratum.getItem(), stratum.getCount());
        SelectionResponse response = parserClient.select(request);

        if (response == null || response.getCards() == null) {
            log.warn("Страта '{}': пустой ответ парсера", stratum.getItem());
            return 0;
        }

        int saved = 0;
        for (OzonCard card : response.getCards()) {
            cardWriteService.saveCard(stratum, set.getGeo(), card);
            saved++;
        }
        log.info("Страта '{}': записано {} из запрошенных {}",
                stratum.getItem(), saved, stratum.getCount());
        return saved;
    }
}