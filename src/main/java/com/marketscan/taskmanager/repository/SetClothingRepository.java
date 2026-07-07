package com.marketscan.taskmanager.repository;

import com.marketscan.taskmanager.entity.SetClothingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Доступ к стратам одежды. */
@Repository
public interface SetClothingRepository extends JpaRepository<SetClothingEntity, UUID> {

    // Все страты конкретного сета. По полю "set" (связь @ManyToOne) и его id.
    List<SetClothingEntity> findBySetId(UUID setId);
}