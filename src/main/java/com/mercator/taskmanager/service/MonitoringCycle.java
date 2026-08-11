package com.mercator.taskmanager.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Состояние текущего цикла обхода (в памяти).
 *
 * Один общий счётчик на активный цикл: сколько задач отправлено (total) и
 * сколько результатов пришло (received). Планировщик стартует/закрывает цикл,
 * ParseResultConsumer инкрементит received на каждый пришедший результат.
 *
 * Потокобезопасно: старт/финиш — из планировщика, инкремент — из консьюмера
 * (другой поток). Считаем И успехи, И неудачи — цикл завершён, когда пришли
 * ВСЕ ответы, независимо от их исхода.
 */
@Component
public class MonitoringCycle {

    private volatile boolean running = false;
    private volatile Instant startedAt = null;
    private volatile int total = 0;
    private final AtomicInteger received = new AtomicInteger(0);

    /** Запустить цикл на totalTasks карточек. */
    public synchronized void start(int totalTasks) {
        this.running = true;
        this.startedAt = Instant.now();
        this.total = totalTasks;
        this.received.set(0);
    }

    /** Закрыть цикл (все пришли или таймаут). */
    public synchronized void finish() {
        this.running = false;
        this.startedAt = null;
        this.total = 0;
        this.received.set(0);
    }

    /**
     * Отметить пришедший результат (любой — успех или неудача).
     * Вызывается консьюмером на каждый результат обхода.
     */
    public void recordResult() {
        if (running) {
            received.incrementAndGet();
        }
    }

    public boolean isRunning() {
        return running;
    }

    /** Все ли результаты собраны. */
    public boolean isComplete() {
        return running && received.get() >= total;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public int total() {
        return total;
    }

    public int received() {
        return received.get();
    }
}