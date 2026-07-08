package com.marketscan.taskmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр открытых партий наполнения (в памяти).
 *
 * Изолирует хранилище: снаружи работают только через методы. Если позже
 * решим хранить партии в БД (переживать перезапуск) — меняется только
 * этот класс, остальной код не трогается.
 *
 * ConcurrentHashMap — потокобезопасная карта: партии создаёт оркестратор,
 * обновляет консьюмер (другой поток), проверяет планировщик (третий).
 */
@Component
public class FillBatchRegistry {

    private static final Logger log = LoggerFactory.getLogger(FillBatchRegistry.class);

    // Ключ — setId: партия привязана к сету (вариант А).
    private final Map<UUID, FillBatch> batches = new ConcurrentHashMap<>();

    /** Открыть партию для сета. Возвращает созданную партию. */
    public FillBatch open(UUID setId, int totalTasks, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        FillBatch batch = new FillBatch(setId, totalTasks, deadline);
        batches.put(setId, batch);
        log.info("Партия открыта: сет={}, задач={}, дедлайн через {}",
                setId, totalTasks, timeout);
        return batch;
    }

    /** Найти открытую партию сета (или null, если нет). */
    public FillBatch find(UUID setId) {
        return batches.get(setId);
    }

    /** Отметить результат в партии сета (если она есть и открыта). */
    public void recordResult(UUID setId, int cardsSaved) {
        FillBatch batch = batches.get(setId);
        if (batch == null) {
            // Партии нет — например, поздний результат после закрытия по таймауту.
            // Это не ошибка: карточки уже записаны консьюмером, просто партию
            // не обновляем (её счётчик уже неактуален).
            log.debug("Результат для сета {} без открытой партии (поздний?)", setId);
            return;
        }
        int received = batch.markResultReceived(cardsSaved);
        log.info("Партия сета {}: получено {}/{}, карточек {}",
                setId, received, batch.getTotalTasks(), batch.getSavedCards());
    }

    /** Все открытые (незакрытые) партии — для планировщика проверки. */
    public java.util.Collection<FillBatch> openBatches() {
        return batches.values().stream()
                .filter(b -> !b.isClosed())
                .toList();
    }

    /** Убрать партию из реестра (после закрытия). */
    public void remove(UUID setId) {
        batches.remove(setId);
    }
}