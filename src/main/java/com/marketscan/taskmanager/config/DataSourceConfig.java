package com.marketscan.taskmanager.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Настройка двух источников данных.
 *
 * Postgres — основная база (актуальное состояние), работает через JPA,
 * поэтому помечена @Primary: Spring Data JPA берёт именно primary-источник.
 *
 * ClickHouse — база замеров, доступ через отдельный JdbcTemplate (чистый JDBC,
 * без Hibernate). Spring сам второй источник не поднимет — объявляем вручную.
 */
@Configuration
public class DataSourceConfig {

    // --- Postgres (главный источник, для JPA) ---

    @Bean
    @Primary
    public DataSource postgresDataSource(
            @org.springframework.beans.factory.annotation.Value("${spring.datasource.url}") String url,
            @org.springframework.beans.factory.annotation.Value("${spring.datasource.username}") String username,
            @org.springframework.beans.factory.annotation.Value("${spring.datasource.password}") String password) {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    // --- ClickHouse (отдельный источник, для прямого JDBC) ---

    @Bean
    public DataSource clickhouseDataSource(
            @org.springframework.beans.factory.annotation.Value("${clickhouse.url}") String url,
            @org.springframework.beans.factory.annotation.Value("${clickhouse.username}") String username,
            @org.springframework.beans.factory.annotation.Value("${clickhouse.password}") String password) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("com.clickhouse.jdbc.Driver")
                .build();
        ds.setPoolName("clickhouse-pool");
        ds.setMaximumPoolSize(4);
        return ds;
    }

    /**
     * JdbcTemplate поверх ClickHouse — через него позже будем писать и читать
     * замеры. Репозитории ClickHouse (Этап 3–4) возьмут именно этот бин.
     */
    @Bean
    @Qualifier("clickhouseJdbcTemplate")
    public JdbcTemplate clickhouseJdbcTemplate(
            @Qualifier("clickhouseDataSource") DataSource clickhouseDataSource) {
        return new JdbcTemplate(clickhouseDataSource);
    }
}