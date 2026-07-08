package com.marketscan.taskmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Периодически проверяет открытые партии наполнения и закрывает
 * завершённые: либо все результаты пришли, либо истёк таймаут.
 * Закрытие пока = только лог (добор навесим позже на это событие).
 */
@Component
public class FillBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(FillBatchScheduler.class);

    private final FillBatchRegistry registry;

    public FillBatchScheduler(FillBatchRegistry registry) {
        this.registry = registry;
    }

    /** Проверка каждые 30 секунд. */
    @Scheduled(fixedDelay = 30_000)
    public void checkBatches() {
        for (FillBatch batch : registry.openBatches()) {
            boolean allDone = batch.allReceived();
            boolean expired = batch.isExpired();

            if (allDone || expired) {
                batch.close();
                registry.remove(batch.getSetId());

                String reason = allDone ? "все результаты получены" : "истёк таймаут";
                log.info("Партия сета {} закрыта ({}): получено {}/{}, карточек записано {}",
                        batch.getSetId(), reason,
                        batch.getReceivedResults(), batch.getTotalTasks(),
                        batch.getSavedCards());
                // Здесь позже: если сет неполный — поставить задачу добора.
            }
        }
    }
}
