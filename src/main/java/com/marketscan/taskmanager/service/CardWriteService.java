package com.marketscan.taskmanager.service;

import com.marketscan.taskmanager.clickhouse.CardSnapshot;
import com.marketscan.taskmanager.clickhouse.CardSnapshotRepository;
import com.marketscan.taskmanager.clickhouse.Measurement;
import com.marketscan.taskmanager.clickhouse.MeasurementRepository;
import com.marketscan.taskmanager.contract.Ozon.OzonCard;
import com.marketscan.taskmanager.entity.CardEntity;
import com.marketscan.taskmanager.entity.SetClothingEntity;
import com.marketscan.taskmanager.repository.CardRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Запись одной подобранной/распарсенной карточки в три базы:
 * Postgres (card) + ClickHouse (measurement + card_snapshot).
 *
 * Выделено из SetFillService, чтобы пользоваться и при синхронном
 * наполнении, и при приёме результатов из Kafka. Порядок важен:
 * сначала Postgres (даёт UUID), потом два ClickHouse с этим UUID.
 */
@Service
public class CardWriteService {

    private final CardRepository cardRepository;
    private final MeasurementRepository measurementRepository;
    private final CardSnapshotRepository snapshotRepository;
    private final JsonHelper json;

    public CardWriteService(CardRepository cardRepository,
                            MeasurementRepository measurementRepository,
                            CardSnapshotRepository snapshotRepository,
                            JsonHelper json) {
        this.cardRepository = cardRepository;
        this.measurementRepository = measurementRepository;
        this.snapshotRepository = snapshotRepository;
        this.json = json;
    }

    /** Записать карточку под стратой, с гео для замера. */
    public void saveCard(SetClothingEntity stratum, String geo, OzonCard card) {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Postgres: паспорт карточки (даёт UUID).
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

        // 2. ClickHouse: замер.
        Measurement m = Measurement.builder()
                .cardId(cardId)
                .sku(card.getSku())
                .parsedAt(now)
                .geo(geo)
                .cardPrice(card.getPrice() != null ? card.getPrice().getCardPrice() : null)
                .price(card.getPrice() != null ? card.getPrice().getPrice() : null)
                .originalPrice(card.getPrice() != null ? card.getPrice().getOriginalPrice() : null)
                .quantity(card.getQuantity())
                .rating(parseFloat(card.getRating()))
                .reviewsCount(parseInt(card.getReviewsCount()))
                .build();
        measurementRepository.insert(m);

        // 3. ClickHouse: снимок.
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
                .embedding(List.of())
                .build();
        snapshotRepository.insert(snap);
    }

    private Float parseFloat(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Float.parseFloat(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    // Коллекция карточки — из характеристик по ключу "Коллекция", как есть.
    // Парсер сам разбирает формы ("Весна-лето 2026", "Весна" и т.д.).
    private String resolveCollection(OzonCard card) {
        if (card.getCharacteristics() == null) {
            return null;
        }
        return card.getCharacteristics().get("Коллекция");
    }
}