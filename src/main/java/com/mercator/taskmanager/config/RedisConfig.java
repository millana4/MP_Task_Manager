package com.mercator.taskmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Подключение к Redis. Redis — единственный транспорт задач и результатов
 * (обход и подбор): ядро кладёт задачи через LPUSH, воркеры-парсеры забирают
 * через BRPOP; результаты возвращаются симметрично. Значения — строки (JSON
 * сериализуем сами через Jackson), поэтому достаточно StringRedisTemplate.
 *
 * Connection factory (Lettuce) конфигурируется Spring Boot автоматически из
 * spring.data.redis.* — здесь только шаблон поверх него.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}