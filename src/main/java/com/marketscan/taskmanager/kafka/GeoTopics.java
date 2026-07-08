package com.marketscan.taskmanager.kafka;

import java.util.Map;

/**
 * Сопоставление гео (как в сете) и кода для имени Kafka-топика.
 * В имени топика нельзя кириллицу/пробелы, поэтому регионам даём
 * латинские коды. Добавить регион = добавить строку в GEO_TO_CODE.
 *
 * Топики задач:      select.tasks.<код>   (парсер региона читает свой)
 * Топики результатов: select.results.<код> (таск-менеджер слушает по шаблону)
 */
public final class GeoTopics {

    private GeoTopics() {}  // утилитный класс, экземпляры не нужны

    public static final String TASKS_PREFIX = "select.tasks.";
    public static final String RESULTS_PREFIX = "select.results.";

    // Шаблон, по которому консьюмер слушает ВСЕ топики результатов
    // (любой регион). Регэксп: select.results.<что угодно>.
    public static final String RESULTS_PATTERN = "select\\.results\\..*";

    private static final Map<String, String> GEO_TO_CODE = Map.of(
            "Санкт-Петербург", "spb"
            // при масштабировании:
            // "Москва", "msk",
            // "Екатеринбург", "ekb"
    );

    /** Код региона для имени топика. Бросает ошибку, если гео неизвестно. */
    public static String codeOf(String geo) {
        String code = GEO_TO_CODE.get(geo);
        if (code == null) {
            throw new IllegalArgumentException(
                    "Неизвестное гео '" + geo + "' — нет кода топика. "
                            + "Добавь его в GeoTopics.GEO_TO_CODE.");
        }
        return code;
    }

    /** Имя топика задач для гео: "Санкт-Петербург" -> "select.tasks.spb". */
    public static String tasksTopic(String geo) {
        return TASKS_PREFIX + codeOf(geo);
    }

    /** Имя топика результатов для гео: -> "select.results.spb". */
    public static String resultsTopic(String geo) {
        return RESULTS_PREFIX + codeOf(geo);
    }
}