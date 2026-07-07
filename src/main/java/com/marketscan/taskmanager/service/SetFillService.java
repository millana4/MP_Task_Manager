package com.marketscan.taskmanager.service;

import com.marketscan.taskmanager.clickhouse.CardSnapshot;
import com.marketscan.taskmanager.clickhouse.CardSnapshotRepository;
import com.marketscan.taskmanager.clickhouse.Measurement;
import com.marketscan.taskmanager.clickhouse.MeasurementRepository;
import com.marketscan.taskmanager.client.OzonParserClient;
import com.marketscan.taskmanager.contract.Ozon.OzonCard;
import com.marketscan.taskmanager.contract.Ozon.SelectionResponse;
import com.marketscan.taskmanager.contract.Ozon.StratumRequest;
import com.marketscan.taskmanager.entity.CardEntity;
import com.marketscan.taskmanager.entity.SetClothingEntity;
import com.marketscan.taskmanager.entity.SetEntity;
import com.marketscan.taskmanager.repository.CardRepository;
import com.marketscan.taskmanager.repository.SetClothingRepository;
import com.marketscan.taskmanager.repository.SetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Наполняет сет карточками: по каждой страте зовёт парсер (select),
 * раскладывает подобранные карточки в Postgres (card) + ClickHouse
 * (measurement + card_snapshot).
 *
 * Устойчив к сбою отдельной страты: ошибку логирует и продолжает
 * остальные, а не валит всё наполнение.
 */
@Service
public class SetFillService {

    private static final Logger log = LoggerFactory.getLogger(SetFillService.class);

    private final SetRepository setRepository;
    private final SetClothingRepository stratumRepository;
    private final CardRepository cardRepository;
    private final MeasurementRepository measurementRepository;
    private final CardSnapshotRepository snapshotRepository;
    private final OzonParserClient parserClient;
    private final JsonHelper json;

    public SetFillService(SetRepository setRepository,
                          SetClothingRepository stratumRepository,
                          CardRepository cardRepository,
                          MeasurementRepository measurementRepository,
                          CardSnapshotRepository snapshotRepository,
                          OzonParserClient parserClient,
                          JsonHelper json) {
        this.setRepository = setRepository;
        this.stratumRepository = stratumRepository;
        this.cardRepository = cardRepository;
        this.measurementRepository = measurementRepository;
        this.snapshotRepository = snapshotRepository;
        this.parserClient = parserClient;
        this.json = json;
    }

    /**
     * Наполнить сет. Возвращает, сколько карточек всего записано.
     */
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
                // Сбой одной страты не должен рушить весь сбор.
                log.error("Страта '{}' (id={}) не наполнена: {}",
                        stratum.getItem(), stratum.getId(), e.getMessage(), e);
            }
        }
        log.info("Наполнение сета {} завершено: {} карточек", setId, totalCards);
        return totalCards;
    }

    /** Наполнить одну страту: позвать парсер и разложить карточки. */
    private int fillStratum(SetEntity set, SetClothingEntity stratum) {
        // 1. Формируем запрос подбора из параметров страты.
        StratumRequest request = new StratumRequest();
        request.setQuery(stratum.getQuery());
        request.setCount(stratum.getCount());
        request.setIsSeasonal(stratum.getIsSeasonal());
        request.setBaseShare(stratum.getBaseShare() != null
                ? stratum.getBaseShare().doubleValue() : null);
        request.setExclude(List.of());  // первое наполнение — исключать нечего

        // 2. Зовём парсер.
        log.info("Страта '{}': запрос подбора {} карточек", stratum.getItem(), stratum.getCount());
        SelectionResponse response = parserClient.select(request);

        if (response == null || response.getCards() == null) {
            log.warn("Страта '{}': пустой ответ парсера", stratum.getItem());
            return 0;
        }

        // 3. Раскладываем каждую карточку.
        int saved = 0;
        for (OzonCard card : response.getCards()) {
            saveCard(set, stratum, card);
            saved++;
        }
        log.info("Страта '{}': записано {} из запрошенных {}",
                stratum.getItem(), saved, stratum.getCount());
        return saved;
    }

    /** Записать одну карточку в три места. Порядок важен: сначала Postgres (даёт UUID). */
    private void saveCard(SetEntity set, SetClothingEntity stratum, OzonCard card) {
        OffsetDateTime now = OffsetDateTime.now();

        // --- 1. Postgres: паспорт карточки ---
        CardEntity entity = new CardEntity();
        entity.setStratum(stratum);
        entity.setSku(card.getSku());
        entity.setName(card.getName());
        entity.setUrl(card.getUrl());
        entity.setSellerId(card.getSeller() != null ? card.getSeller().getId() : null);
        entity.setCollection(resolveCollection(card));
        entity.setStatus("active");
        entity.setFailedAttempts(0);
        CardEntity savedCard = cardRepository.save(entity);
        UUID cardId = savedCard.getId();

        // --- 2. ClickHouse: замер (пульс) ---
        Measurement m = Measurement.builder()
                .cardId(cardId)
                .sku(card.getSku())
                .parsedAt(now)
                .geo(set.getGeo())
                .cardPrice(card.getPrice() != null ? card.getPrice().getCardPrice() : null)
                .price(card.getPrice() != null ? card.getPrice().getPrice() : null)
                .originalPrice(card.getPrice() != null ? card.getPrice().getOriginalPrice() : null)
                .quantity(card.getQuantity())
                .rating(parseFloat(card.getRating()))
                .reviewsCount(parseInt(card.getReviewsCount()))
                .build();
        measurementRepository.insert(m);

        // --- 3. ClickHouse: снимок ---
        CardSnapshot snap = CardSnapshot.builder()
                .cardId(cardId)
                .sku(card.getSku())
                .parsedAt(now)
                .name(card.getName())
                .description(card.getDescription())
                .brand(card.getBrand())
                .category(card.getCategory())
                .categoryPath(card.getCategoryPath())
                .characteristics(card.getCharacteristics())
                .variantsAspect(card.getVariantsAspect())
                .variants(json.toJson(card.getVariants()))
                .sellerId(card.getSeller() != null ? card.getSeller().getId() : null)
                .sellerName(card.getSeller() != null ? card.getSeller().getName() : null)
                .sellerLegalName(card.getSeller() != null ? card.getSeller().getLegalName() : null)
                .sellerOgrn(card.getSeller() != null ? card.getSeller().getOgrn() : null)
                .embedding(List.of())  // вектор пока не считаем — добавим позже
                .build();
        snapshotRepository.insert(snap);
    }

    // Коллекция карточки: берём из характеристики, если парсер не отдал отдельно.
    // Пока просто null — парсер присылает коллекцию в поле карточки, если оно есть.
    private String resolveCollection(OzonCard card) {
        return null;  // TODO: заполнить, когда определим источник коллекции в карточке
    }

    // "4.9" -> 4.9f; пусто/мусор -> null
    private Float parseFloat(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Float.parseFloat(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    // "282" -> 282; пусто/мусор -> null
    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}