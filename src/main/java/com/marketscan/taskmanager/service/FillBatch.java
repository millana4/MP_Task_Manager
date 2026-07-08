package com.marketscan.taskmanager.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Одна партия наполнения сета — сколько задач отправлено, сколько
 * результатов вернулось, когда дедлайн. Живёт в памяти (см. FillBatchRegistry).
 *
 * Счётчик полученных — потокобезопасный (AtomicInteger): результаты
 * приходят из консьюмера в отдельном потоке, могут инкрементиться
 * параллельно.
 */
public class FillBatch {

    private final UUID setId;
    private final int totalTasks;              // сколько задач отправлено
    private final AtomicInteger receivedResults = new AtomicInteger(0);
    private final AtomicInteger savedCards = new AtomicInteger(0);
    private final Instant deadline;            // после этого — закрыть по таймауту
    private volatile boolean closed = false;

    public FillBatch(UUID setId, int totalTasks, Instant deadline) {
        this.setId = setId;
        this.totalTasks = totalTasks;
        this.deadline = deadline;
    }

    /** Отметить пришедший результат (одна страта). Возвращает новое число полученных. */
    public int markResultReceived(int cardsSaved) {
        savedCards.addAndGet(cardsSaved);
        return receivedResults.incrementAndGet();
    }

    /** Все ли результаты вернулись. */
    public boolean allReceived() {
        return receivedResults.get() >= totalTasks;
    }

    /** Истёк ли дедлайн. */
    public boolean isExpired() {
        return Instant.now().isAfter(deadline);
    }

    public UUID getSetId() { return setId; }
    public int getTotalTasks() { return totalTasks; }
    public int getReceivedResults() { return receivedResults.get(); }
    public int getSavedCards() { return savedCards.get(); }
    public boolean isClosed() { return closed; }
    public void close() { this.closed = true; }
}