package com.marketscan.taskmanager.controller;

import com.marketscan.taskmanager.clickhouse.Measurement;
import com.marketscan.taskmanager.clickhouse.MeasurementRepository;
import com.marketscan.taskmanager.entity.CardEntity;
import com.marketscan.taskmanager.repository.CardRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Выдача данных для внешнего сервиса аналитики.
 *
 * GET /api/v1/analytics/cards/{cardId}/measurements — временной ряд
 *     замеров карточки (цены, наличие, рейтинг во времени).
 * GET /api/v1/analytics/strata/{stratumId}/cards — карточки страты.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final MeasurementRepository measurementRepository;
    private final CardRepository cardRepository;

    public AnalyticsController(MeasurementRepository measurementRepository,
                               CardRepository cardRepository) {
        this.measurementRepository = measurementRepository;
        this.cardRepository = cardRepository;
    }

    /** Временной ряд замеров одной карточки. */
    @GetMapping("/cards/{cardId}/measurements")
    public ResponseEntity<List<Measurement>> measurements(@PathVariable UUID cardId) {
        return ResponseEntity.ok(measurementRepository.findByCardId(cardId));
    }

    /** Карточки страты (их id, sku, статус) — чтобы аналитика знала, что запрашивать. */
    @GetMapping("/strata/{stratumId}/cards")
    public ResponseEntity<List<CardEntity>> cardsOfStratum(@PathVariable UUID stratumId) {
        return ResponseEntity.ok(cardRepository.findByStratumId(stratumId));
    }
}