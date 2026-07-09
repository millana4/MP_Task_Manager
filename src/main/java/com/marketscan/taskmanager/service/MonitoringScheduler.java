package com.marketscan.taskmanager.service;

import com.marketscan.taskmanager.entity.CardEntity;
import com.marketscan.taskmanager.kafka.ParseTaskMessage;
import com.marketscan.taskmanager.kafka.ParseTaskProducer;
import com.marketscan.taskmanager.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ежечасный обход: перепарсить все активные карточки, дописать замеры.
 *
 * Защита от наложения: пока идёт цикл (флаг cycleRunning), новый не
 * стартует. Флаг сбрасывается, когда обход отработан. Пока — упрощённо
 * через флаг в памяти; строгий учёт «все результаты пришли» можно
 * навесить через партию, как в подборе.
 */
@Component
public class MonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final CardRepository cardRepository;
    private final ParseTaskProducer parseTaskProducer;

    // Идёт ли сейчас цикл обхода (защита от наложения).
    private final AtomicBoolean cycleRunning = new AtomicBoolean(false);

    public MonitoringScheduler(CardRepository cardRepository,
                               ParseTaskProducer parseTaskProducer) {
        this.cardRepository = cardRepository;
        this.parseTaskProducer = parseTaskProducer;
    }

    /** Каждый час (3_600_000 мс). Первый запуск — через час после старта. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void runCycle() {
        // Если предыдущий цикл ещё идёт — пропускаем (не накладываем).
        if (!cycleRunning.compareAndSet(false, true)) {
            log.warn("Обход пропущен: предыдущий цикл ещё не завершён");
            return;
        }

        try {
            List<CardEntity> active = cardRepository.findByStatus("active");
            log.info("Цикл обхода: {} активных карточек", active.size());

            for (CardEntity card : active) {
                ParseTaskMessage task = new ParseTaskMessage();
                task.setTaskId(UUID.randomUUID());
                task.setCardId(card.getId());
                task.setSku(card.getSku());
                task.setGeo(geoOfCard(card));
                parseTaskProducer.send(task);
            }
            log.info("Цикл обхода: отправлено {} задач", active.size());
        } finally {
            // Упрощённо освобождаем сразу после рассылки. Для строгого
            // «дождаться всех результатов» — освобождать по закрытию партии.
            cycleRunning.set(false);
        }
    }

    // Гео карточки берём через страту -> сет. Здесь заглушка: нужно
    // достать set.geo по card.stratum.set. Упрощённо возвращаем СПб.
    private String geoOfCard(CardEntity card) {
        // TODO: card -> stratum -> set -> geo. Пока один регион.
        return "Санкт-Петербург";
    }
}
