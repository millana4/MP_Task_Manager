package com.mercator.taskmanager.repository;

import com.mercator.taskmanager.entity.CardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Доступ к карточкам. */
@Repository
public interface CardRepository extends JpaRepository<CardEntity, UUID> {

    // Все карточки страты.
    List<CardEntity> findByStratumId(UUID stratumId);

    List<CardEntity> findByStatus(String status);

    // Активные карточки страты — понадобится для периодического обхода
    // (парсим только те, что в продаже).
    List<CardEntity> findByStratumIdAndStatus(UUID stratumId, String status);

    // Есть ли уже карточка с таким sku в этом сете, в любой его страте
    boolean existsByStratumSetIdAndSku(UUID setId, String sku);
}