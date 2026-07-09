package com.marketscan.taskmanager.kafka;

import lombok.Data;
import java.util.UUID;

/**
 * Задача обхода: перепарсить карточку по sku. Отправляется в parse.tasks.<гео>.
 * card_id — наша карточка (для привязки замера), sku — что парсить.
 */
@Data
public class ParseTaskMessage {
    private UUID taskId;
    private UUID cardId;
    private String sku;
    private String geo;
}
