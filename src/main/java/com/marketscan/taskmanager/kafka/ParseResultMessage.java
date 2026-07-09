package com.marketscan.taskmanager.kafka;

import com.marketscan.taskmanager.contract.Ozon.OzonCard;
import lombok.Data;
import java.util.UUID;

/**
 * Результат обхода: свежие данные карточки. Эхо task_id/card_id/sku/geo.
 * ok=false + error, если карточка не ответила (тогда card может быть null).
 */
@Data
public class ParseResultMessage {
    private UUID taskId;
    private UUID cardId;
    private String sku;
    private String geo;
    private Boolean ok;
    private OzonCard card;
    private String error;
}