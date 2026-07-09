package com.marketscan.taskmanager.clickhouse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Доступ к временному ряду замеров в ClickHouse.
 *
 * В отличие от Postgres-репозиториев (Spring Data JPA), здесь нет
 * derived-методов — пишем SQL руками через clickhouseJdbcTemplate.
 * Это осознанно: ClickHouse аналитический, работаем прямым SQL.
 */
@Repository
public class MeasurementRepository {

    private final JdbcTemplate clickhouse;

    // Внедряем именно clickhouseJdbcTemplate (по имени бина из DataSourceConfig),
    // а не основной (Postgres) JdbcTemplate.
    public MeasurementRepository(
            @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
        this.clickhouse = clickhouseJdbcTemplate;
    }

    /** Вставить один замер. */
    public void insert(Measurement m) {
        clickhouse.update(
                "INSERT INTO measurement " +
                        "(card_id, sku, parsed_at, geo, card_price, price, original_price, quantity, rating, reviews_count) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                m.getCardId(),
                m.getSku(),
                Timestamp.from(m.getParsedAt().toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)),
                m.getGeo(),
                m.getCardPrice(),
                m.getPrice(),
                m.getOriginalPrice(),
                m.getQuantity(),
                m.getRating(),
                m.getReviewsCount()
        );
    }

    /** Сколько замеров у карточки — простая проверочная выборка. */
    public long countByCardId(java.util.UUID cardId) {
        Long count = clickhouse.queryForObject(
                "SELECT count() FROM measurement WHERE card_id = ?",
                Long.class,
                cardId
        );
        return count != null ? count : 0L;
    }

    /** Временной ряд замеров карточки, по возрастанию времени. */
    public List<Measurement> findByCardId(UUID cardId) {
        return clickhouse.query(
                "SELECT card_id, sku, parsed_at, geo, card_price, price, " +
                        "original_price, quantity, rating, reviews_count " +
                        "FROM measurement WHERE card_id = ? ORDER BY parsed_at",
                (rs, rowNum) -> Measurement.builder()
                        .cardId(UUID.fromString(rs.getString("card_id")))
                        .sku(rs.getString("sku"))
                        .parsedAt(rs.getObject("parsed_at", java.time.LocalDateTime.class)
                                .atOffset(java.time.ZoneOffset.UTC))
                        .geo(rs.getString("geo"))
                        .cardPrice((Double) rs.getObject("card_price"))
                        .price((Double) rs.getObject("price"))
                        .originalPrice((Double) rs.getObject("original_price"))
                        .quantity((Integer) rs.getObject("quantity"))
                        .rating((Float) rs.getObject("rating"))
                        .reviewsCount((Integer) rs.getObject("reviews_count"))
                        .build(),
                cardId);
    }
}
