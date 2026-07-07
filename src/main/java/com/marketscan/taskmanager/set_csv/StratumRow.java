package com.marketscan.taskmanager.set_csv;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Одна строка CSV, разобранная в поля страты одежды.
 * Только то, что нужно для записи в set_clothing — расчётные колонки
 * исходной таблицы (доли, нормализованные SKU и пр.) сюда не попадают.
 */
@Data
public class StratumRow {
    private String item;        // предмет: "Трусы"
    private String query;       // поисковый запрос: "Трусы женские"
    private Integer count;      // кол-во итоговое с округлением: 4
    private String gender;      // f / m (переведён из Ж / М)
    private Short layer;        // слой 0-3 (извлечён из "0 нательный")
    private Boolean isSeasonal;  // из "TRUE" / "FALSE"
    private BigDecimal baseShare; // доля базы; null если сезонности нет
}