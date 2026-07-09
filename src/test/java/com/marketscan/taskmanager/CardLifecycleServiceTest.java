package com.marketscan.taskmanager;

import com.marketscan.taskmanager.entity.CardEntity;
import com.marketscan.taskmanager.entity.SetClothingEntity;
import com.marketscan.taskmanager.entity.SetEntity;
import com.marketscan.taskmanager.repository.CardRepository;
import com.marketscan.taskmanager.repository.SetClothingRepository;
import com.marketscan.taskmanager.repository.SetRepository;
import com.marketscan.taskmanager.service.CardLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Жизненный цикл карточки: успех -> active, неудачи -> stale -> dropped.
 * Порог max-failed-attempts по умолчанию 5.
 */
class CardLifecycleServiceTest extends PostgresTestBase {

    @Autowired CardLifecycleService lifecycle;
    @Autowired CardRepository cardRepository;
    @Autowired SetRepository setRepository;
    @Autowired SetClothingRepository stratumRepository;

    @Test
    @Transactional
    void statusTransitions() {
        UUID cardId = createCard();

        // 4 неудачи (порог 5) — карточка ещё stale, не dropped.
        for (int i = 0; i < 4; i++) lifecycle.markFailure(cardId);
        CardEntity c = cardRepository.findById(cardId).orElseThrow();
        assertEquals("stale", c.getStatus());
        assertEquals(4, c.getFailedAttempts());
        assertNotNull(c.getUnavailableSince());

        // 5-я неудача — dropped.
        lifecycle.markFailure(cardId);
        c = cardRepository.findById(cardId).orElseThrow();
        assertEquals("dropped", c.getStatus());
        assertNotNull(c.getDroppedAt());

        // Успех — карточка воскресает, счётчики сброшены.
        lifecycle.markSuccess(cardId);
        c = cardRepository.findById(cardId).orElseThrow();
        assertEquals("active", c.getStatus());
        assertEquals(0, c.getFailedAttempts());
        assertNull(c.getUnavailableSince());
    }

    private UUID createCard() {
        SetEntity set = new SetEntity();
        set.setMarketplace("ozon"); set.setCategory("clothing"); set.setGeo("Санкт-Петербург");
        set = setRepository.save(set);

        SetClothingEntity st = new SetClothingEntity();
        st.setSet(set); st.setItem("X"); st.setQuery("x"); st.setCount(1);
        st.setGender("f"); st.setLayer((short) 1); st.setIsSeasonal(false);
        st = stratumRepository.save(st);

        CardEntity card = new CardEntity();
        card.setStratum(st); card.setSku("SKU" + System.nanoTime());
        card.setStatus("active"); card.setFailedAttempts(0);
        return cardRepository.save(card).getId();
    }
}
