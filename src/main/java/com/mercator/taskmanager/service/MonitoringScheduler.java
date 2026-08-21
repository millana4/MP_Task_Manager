package com.mercator.taskmanager.service;

import com.mercator.taskmanager.entity.CardEntity;
import com.mercator.taskmanager.redis.ParseTaskMessage;
import com.mercator.taskmanager.redis.ParseTaskProducer;
import com.mercator.taskmanager.redis.RedisKeys;
import com.mercator.taskmanager.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Потоковый обход без барьера. Вместо «залить все active разом и ждать всех»
 * планировщик держит в Redis-очереди небольшой буфер задач и доливает его по
 * мере разбора воркерами.
 *
 * Каждый тик:
 *   1. смотрит длину очереди parse:tasks:<гео> (LLEN);
 *   2. добирает (buffer − LLEN) карточек-кандидатов из Postgres: самые давно
 *      не мерянные (last_measured_at ASC NULLS FIRST), исключая те, что уже
 *      в работе (last_enqueued_at свежее окна ожидания);
 *   3. кладёт задачи в очередь (LPUSH через ParseTaskProducer) и помечает
 *      last_enqueued_at, чтобы не переотправить их следующим тиком.
 *
 * Равномерность: карточку обошли → обновился last_measured_at → она уходит
 * в хвост сортировки → вперёд выходят те, кого дольше не трогали.
 * Потерянные задачи (результат не пришёл) сами вернутся в кандидаты, когда
 * last_enqueued_at выйдет за окно ожидания (inflight-timeout).
 */
@Component
public class MonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final CardRepository cardRepository;
    private final ParseTaskProducer parseTaskProducer;
    private final StringRedisTemplate redis;
    private final RedisKeys keys;

    private final int queueBuffer;
    private final long inflightTimeoutMinutes;
    private final String geo;

    public MonitoringScheduler(CardRepository cardRepository,
                               ParseTaskProducer parseTaskProducer,
                               StringRedisTemplate redis,
                               RedisKeys keys,
                               @Value("${taskmanager.monitoring.queue-buffer}") int queueBuffer,
                               @Value("${taskmanager.monitoring.inflight-timeout-minutes}") long inflightTimeoutMinutes,
                               @Value("${taskmanager.geo}") String geo) {
        this.cardRepository = cardRepository;
        this.parseTaskProducer = parseTaskProducer;
        this.redis = redis;
        this.keys = keys;
        this.queueBuffer = queueBuffer;
        this.inflightTimeoutMinutes = inflightTimeoutMinutes;
        this.geo = geo;
    }

    /**
     * Тик долива. Интервал берётся из конфига (refill-interval-ms).
     * initialDelay даёт приложению и Redis подняться перед первым доливом.
     */
    @Scheduled(fixedDelayString = "${taskmanager.monitoring.refill-interval-ms}", initialDelay = 15_000)
    @Transactional
    public void refill() {
        String queueKey = keys.parseTasks(geo);

        Long len = redis.opsForList().size(queueKey);
        int inQueue = (len == null) ? 0 : len.intValue();
        int need = queueBuffer - inQueue;
        if (need <= 0) {
            return;  // очередь уже полна — ничего не доливаем
        }

        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime threshold = now.minusMinutes(inflightTimeoutMinutes);

        List<CardEntity> candidates = cardRepository.findQueueCandidates(threshold, need);
        if (candidates.isEmpty()) {
            return;  // нет активных, кого пора обойти
        }

        List<UUID> enqueuedIds = new ArrayList<>(candidates.size());
        for (CardEntity card : candidates) {
            ParseTaskMessage task = new ParseTaskMessage();
            task.setTaskId(UUID.randomUUID());
            task.setCardId(card.getId());
            task.setSku(card.getSku());
            task.setGeo(geo);
            parseTaskProducer.send(task);
            enqueuedIds.add(card.getId());
        }

        // Помечаем отправленные: не переотправятся, пока не выйдет окно ожидания.
        cardRepository.markEnqueued(enqueuedIds, now);

        log.info("Обход: долив очереди {} — было {}, добавлено {} (буфер {})",
                queueKey, inQueue, enqueuedIds.size(), queueBuffer);
    }
}