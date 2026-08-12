package com.mercator.taskmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Периодический добор недостающих карточек. Ежедневно в 13:00 по Москве
 * проверяет дефициты по всем стратам и досылает задачи подбора на
 * недостающее (count - active). Включается флагом taskmanager.refill.enabled.
 */
@Component
public class RefillScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefillScheduler.class);

    private final SetFillKafkaService fillService;
    private final boolean enabled;

    public RefillScheduler(SetFillKafkaService fillService,
                           @Value("${taskmanager.refill.enabled:true}") boolean enabled) {
        this.fillService = fillService;
        this.enabled = enabled;
    }

    // Ежедневно в 13:00 по Москве. Зона задана явно — не зависит от TZ контейнера.
    @Scheduled(cron = "0 0 13 * * *", zone = "Europe/Moscow")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            int sent = fillService.refillDeficits();
            log.info("Плановый добор (13:00 МСК) выполнен: отправлено {} задач", sent);
        } catch (Exception e) {
            log.error("Ошибка планового добора: {}", e.getMessage(), e);
        }
    }
}