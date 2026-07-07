package com.marketscan.taskmanager.controller;

import com.marketscan.taskmanager.entity.SetEntity;
import com.marketscan.taskmanager.service.SetImportService;
import com.marketscan.taskmanager.set_csv.StratumCsvParser;
import com.marketscan.taskmanager.set_csv.StratumRow;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Приём CSV со стратами сета (админка).
 *
 * POST /api/v1/sets/import  — multipart-форма:
 *   file        — CSV-файл со стратами
 *   marketplace — ozon (пока)
 *   category    — clothing (пока)
 *   geo         — Санкт-Петербург (пока)
 *
 * Соединяет три слоя: парсер CSV → сервис импорта → БД.
 */
@RestController
@RequestMapping("/api/v1/sets")
public class SetImportController {

    private final StratumCsvParser parser;
    private final SetImportService importService;

    public SetImportController(StratumCsvParser parser, SetImportService importService) {
        this.parser = parser;
        this.importService = importService;
    }

    @PostMapping("/import")
    public ResponseEntity<?> importSet(
            @RequestParam("file") MultipartFile file,
            @RequestParam("marketplace") String marketplace,
            @RequestParam("category") String category,
            @RequestParam("geo") String geo) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Файл пустой"));
        }

        try {
            // 1. Разбираем CSV.
            List<StratumRow> rows = parser.parse(file.getInputStream());

            // 2. Импортируем как новый сет.
            SetEntity set = importService.importSet(marketplace, category, geo, rows);

            // 3. Отдаём id созданного сета и число страт.
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "setId", set.getId().toString(),
                    "marketplace", set.getMarketplace(),
                    "category", set.getCategory(),
                    "geo", set.getGeo(),
                    "strataCount", rows.size()
            ));

        } catch (IllegalArgumentException e) {
            // Ошибки разбора CSV или валидации — понятный ответ с текстом.
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Не удалось прочитать файл: " + e.getMessage()));
        }
    }
}