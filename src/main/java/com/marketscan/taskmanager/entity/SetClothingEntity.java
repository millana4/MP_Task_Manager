package com.marketscan.taskmanager.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Сущность страты одежды — строка таблицы "set_clothing".
 * Один предмет (item) со всеми атрибутами: общими (item, query, count)
 * и частными для одежды (gender, layer, isSeasonal, baseShare).
 *
 * Связь с сетом: много страт → один сет (@ManyToOne).
 */
@Entity
@Table(name = "set_clothing")
@Getter
@Setter
public class SetClothingEntity {

    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    // Много страт относятся к одному сету. LAZY — сет подгружается
    // только когда к нему реально обращаются, а не всегда.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_id", nullable = false)
    private SetEntity set;

    @Column(nullable = false)
    private String item;

    @Column(nullable = false)
    private String query;

    @Column(nullable = false)
    private Integer count;

    @Column(nullable = false)
    private String gender;

    @Column(nullable = false)
    private Short layer;

    @Column(name = "is_seasonal", nullable = false)
    private Boolean isSeasonal;

    @Column(name = "base_share")
    private BigDecimal baseShare;
}
