package com.mercator.taskmanager.service;

import com.mercator.taskmanager.entity.CardEntity;
import com.mercator.taskmanager.kafka.ParseTaskMessage;
import com.mercator.taskmanager.kafka.ParseTaskProducer;
import com.mercator.taskmanager.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Непрерывный цикл обхода: перепарсить ВСЕ активные карточки, дописать замеры.
 *
 * Цикл не привязан к расписанию. Отправили задачи по всем активным карточкам,
 * ждём, пока придут все результаты (счётчик в MonitoringCycle обновляет
 * ParseResultConsumer). Как только собрали все (или вышел таймаут по зависшим)
 * — сразу стартует следующий цикл. Так очередь не переполняется, и каждая
 * карточка гарантированно попадает в каждый цикл (замеры равномерны).
 *
 * "Тик" планировщика частый (проверка каждые 30с), но реально новый цикл
 * запускается ТОЛЬКО когда предыдущий завершён.
 */
@Component
public class MonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final CardRepository cardRepository;
    private final ParseTaskProducer parseTaskProducer;
    private final MonitoringCycle cycle;

    // Таймаут цикла: сколько ждать зависшие карточки, прежде чем закрыть цикл
    // и стартовать следующий. Не даёт застрять навсегда из-за пары карточек,
    // чьи результаты не пришли (антибот, потеря сообщения).
    private final Duration cycleTimeout;

    public MonitoringScheduler(CardRepository cardRepository,
                               ParseTaskProducer parseTaskProducer,
                               MonitoringCycle cycle,
                               @Value("${taskmanager.monitoring.cycle-timeout-minutes:360}") long timeoutMinutes) {
        this.cardRepository = cardRepository;
        this.parseTaskProducer = parseTaskProducer;
        this.cycle = cycle;
        this.cycleTimeout = Duration.ofMinutes(timeoutMinutes);
    }

    /**
     * Тик проверки. Часто (каждые 30с), но реальное действие — только на
     * границах: если цикл не идёт → запустить новый; если идёт и завершён
     * (все результаты пришли ИЛИ таймаут) → закрыть, следующий тик запустит новый.
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void tick() {
        // Цикл идёт — проверяем, не пора ли его закрыть.
        if (cycle.isRunning()) {
            boolean allDone = cycle.isComplete();
            boolean timedOut = cycle.startedAt() != null
                    && Instant.now().isAfter(cycle.startedAt().plus(cycleTimeout));

            if (allDone) {
                log.info("Цикл обхода завершён: получено все {}/{} результатов за {}",
                        cycle.received(), cycle.total(),
                        Duration.between(cycle.startedAt(), Instant.now()));
                cycle.finish();
            } else if (timedOut) {
                log.warn("Цикл обхода закрыт по таймауту: получено {}/{} (не дождались {})",
                        cycle.received(), cycle.total(), cycle.total() - cycle.received());
                cycle.finish();
            }
            return; // пока цикл идёт (или только что закрыли) — новый не стартуем в этот тик
        }

        // Цикла нет — запускаем новый обход по всем активным карточкам.
        startNewCycle();
    }

    private void startNewCycle() {
        List<CardEntity> active = cardRepository.findByStatus("active");
        if (active.isEmpty()) {
            log.warn("Обход: активных карточек нет — цикл не запускаем");
            return;
        }

        cycle.start(active.size());
        log.info("Цикл обхода начат: {} активных карточек", active.size());

        for (CardEntity card : active) {
            ParseTaskMessage task = new ParseTaskMessage();
            task.setTaskId(UUID.randomUUID());
            task.setCardId(card.getId());
            task.setSku(card.getSku());
            task.setGeo(geoOfCard(card));
            parseTaskProducer.send(task);
        }
        log.info("Цикл обхода: отправлено {} задач, ждём результаты", active.size());
    }

    private String geoOfCard(CardEntity card) {
        // TODO: card -> stratum -> set -> geo. Пока один регион.
        return "Санкт-Петербург";
    }
}