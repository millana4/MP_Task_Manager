package com.marketscan.taskmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность сета — строка таблицы "set". Общая шапка задания на сбор
 * (маркетплейс, категория, гео). Одна на регион.
 */

@Entity
@Table(name="set")
@Getter
@Setter
public class SetEntity {
    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String marketplace;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String geo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
