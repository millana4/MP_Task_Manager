package com.mercator.taskmanager;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Базовый класс для тестов, которым нужны настоящие Postgres и Redis.
 *
 * Контейнеры — singleton: стартуют ОДИН раз на весь прогон тестов и не гасятся
 * между классами (Ryuk уберёт их после JVM). Так нет гонок «контейнер уже
 * останавливается» между тест-классами и не тратится время на переподъём.
 *
 * @DynamicPropertySource подменяет адреса БД в настройках Spring на адреса
 * этих контейнеров (перебивает явные URL из application-test.yml). Flyway
 * накатывает миграции (включая V5) в тестовый Postgres.
 *
 * Наследники помечаются @SpringBootTest — он и активирует Spring-контекст.
 */
public abstract class PostgresTestBase {

    static final PostgreSQLContainer<?> postgres;
    static final GenericContainer<?> redis;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16");
        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        // Стартуем один раз; не вызываем stop() — контейнеры живут до конца JVM.
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}