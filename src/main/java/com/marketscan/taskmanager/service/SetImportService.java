package com.marketscan.taskmanager.service;

import com.marketscan.taskmanager.entity.SetClothingEntity;
import com.marketscan.taskmanager.entity.SetEntity;
import com.marketscan.taskmanager.repository.SetClothingRepository;
import com.marketscan.taskmanager.repository.SetRepository;
import com.marketscan.taskmanager.set_csv.StratumRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Создаёт сет и его страты из разобранного CSV.
 *
 * Одна загрузка = один новый сет (marketplace/category/geo задаются при
 * загрузке) + страты одежды по строкам файла. Всё в одной транзакции:
 * либо сохранится целиком, либо ничего (если что-то упадёт на середине).
 */
@Service
public class SetImportService {

    private final SetRepository setRepository;
    private final SetClothingRepository stratumRepository;


    public SetImportService(SetRepository setRepository, SetClothingRepository stratumRepositoty) {
        this.setRepository = setRepository;
        this.stratumRepository = stratumRepositoty;
    }

    @Transactional
    public SetEntity importSet(String marketplace, String category, String geo, List<StratumRow> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Нет ни одной страты для импорта");
        }

        // 1. Создаём шапку сета
        SetEntity set = new SetEntity();
        set.setMarketplace(marketplace);
        set.setCategory(category);
        set.setGeo(geo);
        SetEntity savedSet = setRepository.save(set);

        // 2. Создаём страту на каждую строку CSV
        for (StratumRow row : rows) {
            SetClothingEntity stratum = new SetClothingEntity();
            stratum.setSet(savedSet);
            stratum.setItem(row.getItem());
            stratum.setQuery(row.getQuery());
            stratum.setCount(row.getCount());
            stratum.setGender(row.getGender());
            stratum.setLayer(row.getLayer());
            stratum.setIsSeasonal(row.getIsSeasonal());
            stratum.setBaseShare(row.getBaseShare());
            stratumRepository.save(stratum);
        }

        return savedSet;
    }
}
