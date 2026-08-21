package com.mercator.taskmanager.redis;

import java.util.Map;

/**
 * Сопоставление гео (как в сете) и латинского кода региона.
 * Код используется в ключах Redis-списков (parse:tasks:<код> и т.п.):
 * в ключе не должно быть кириллицы/пробелов. Добавить регион = добавить
 * строку в GEO_TO_CODE.
 */
public final class GeoTopics {

    private GeoTopics() {}  // утилитный класс, экземпляры не нужны

    private static final Map<String, String> GEO_TO_CODE = Map.of(
            "Санкт-Петербург", "spb"
            // при масштабировании:
            // "Москва", "msk",
            // "Екатеринбург", "ekb"
    );

    /** Код региона для ключа Redis. Бросает ошибку, если гео неизвестно. */
    public static String codeOf(String geo) {
        String code = GEO_TO_CODE.get(geo);
        if (code == null) {
            throw new IllegalArgumentException(
                    "Неизвестное гео '" + geo + "' — нет кода региона. "
                            + "Добавь его в GeoTopics.GEO_TO_CODE.");
        }
        return code;
    }
}