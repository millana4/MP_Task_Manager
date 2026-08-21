package com.mercator.taskmanager.redis;

import com.mercator.taskmanager.contract.Ozon.OzonCard;
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
    private String parserId;
    private Boolean ok;
    private OzonCard card;
    private String error;
    private String errorCode;
}