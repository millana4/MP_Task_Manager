package com.marketscan.taskmanager.service;

import com.marketscan.taskmanager.entity.CardEntity;
import com.marketscan.taskmanager.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Управляет жизненным циклом карточки по итогам обхода.
 *
 * Успех  -> active, счётчик неудач сброшен, unavailable_since очищен.
 * Неудача -> failed_attempts++, при первой неудаче фиксируем
 *            unavailable_since; после N неудач -> dropped (выбыла).
 *
 * Порог N настраивается (taskmanager.card.max-failed-attempts).
 */
@Service
public class CardLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(CardLifecycleService.class);

    private final CardRepository cardRepository;
    private final int maxFailedAttempts;

    public CardLifecycleService(CardRepository cardRepository,
                                @Value("${taskmanager.card.max-failed-attempts:5}") int maxFailedAttempts) {
        this.cardRepository = cardRepository;
        this.maxFailedAttempts = maxFailedAttempts;
    }

    /** Карточка успешно распарсена — она жива. */
    @Transactional
    public void markSuccess(UUID cardId) {
        cardRepository.findById(cardId).ifPresent(card -> {
            card.setStatus("active");
            card.setFailedAttempts(0);
            card.setUnavailableSince(null);
            cardRepository.save(card);
        });
    }

    /** Карточка не ответила при обходе. */
    @Transactional
    public void markFailure(UUID cardId) {
        cardRepository.findById(cardId).ifPresent(card -> {
            int attempts = card.getFailedAttempts() + 1;
            card.setFailedAttempts(attempts);

            if (card.getUnavailableSince() == null) {
                card.setUnavailableSince(OffsetDateTime.now());  // первая неудача
            }

            if (attempts >= maxFailedAttempts) {
                card.setStatus("dropped");
                card.setDroppedAt(OffsetDateTime.now());
                log.info("Карточка {} выбыла после {} неудач", cardId, attempts);
            } else {
                card.setStatus("stale");  // временно недоступна, ещё пробуем
            }
            cardRepository.save(card);
        });
    }
}