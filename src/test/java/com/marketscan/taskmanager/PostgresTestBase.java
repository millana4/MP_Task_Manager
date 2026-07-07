package com.marketscan.taskmanager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Базовый класс для тестов, которым нужна настоящая Postgres.
 *
 * Testcontainers поднимает одноразовый контейнер Postgres на время тестов
 * и гасит после. @DynamicPropertySource на лету подменяет адрес базы в
 * настройках Spring на адрес этого временного контейнера — поэтому тест
 * работает с чистой БД, а не с твоим рабочим контейнером на 5433.
 *
 * Flyway накатит миграции (V1, V2) в этот временный контейнер автоматически
 * при старте контекста — значит тест проверяет и сами миграции тоже.
 */
@SpringBootTest
@Testcontainers
public abstract class PostgresTestBase {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
