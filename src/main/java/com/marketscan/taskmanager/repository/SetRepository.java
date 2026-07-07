package com.marketscan.taskmanager.repository;

import com.marketscan.taskmanager.entity.SetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Доступ к сетам. Наследуя JpaRepository, автоматически получаем
 * save, findById, findAll, delete и т.д. Spring генерирует их сам.
 *
 * Второй параметр UUID — тип первичного ключа сущности.
 */
@Repository
public interface SetRepository extends JpaRepository<SetEntity, UUID> {

    // Метод, которого нет в базовом наборе: найти сеты по маркетплейсу,
    // категории и гео. Spring построит SQL по имени метода — ничего писать
    // не нужно, только объявить сигнатуру.
    List<SetEntity> findByMarketplaceAndCategoryAndGeo(
            String marketplace, String category, String geo);
}
