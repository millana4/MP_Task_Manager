package com.marketscan.taskmanager.contract.Ozon;

import lombok.Data;

/** Данные продавца. Соответствует OzonSeller из парсера. */
@Data
public class OzonSeller {
    private String id;          // ID продавца (sellerId)
    private String name;        // название магазина на Ozon
    private String legalName;   // legal_name — юр. название (ООО/АО/ИП)
    private String ogrn;        // ОГРН/ОГРНИП
}
