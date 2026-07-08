package com.marketscan.taskmanager.client;

import com.marketscan.taskmanager.contract.Ozon.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Клиент к парсеру Ozon. Инкапсулирует HTTP-вызовы: сервисы работают
 * с этим клиентом, не зная про URL и детали HTTP.
 *
 * Пока один метод — подбор карточек (select). Позже добавим парсинг
 * карточки по id/url для периодического обхода.
 */
@Component
public class OzonParserClient {

    private final RestClient restClient;

    public OzonParserClient(RestClient ozonParserRestClient) {
        this.restClient = ozonParserRestClient;
    }

    /**
     * Подбор карточек под страту. Вызывает POST /api/v1/ozon/select.
     *
     * @param request параметры страты (query, count, isSeasonal, baseShare, exclude)
     * @return подобранные карточки + requestedCount/foundCount
     */
    public SelectionResponse select(StratumRequest request) {
        return restClient.post()
                .uri("/api/v1/ozon/select")
                .body(request)
                .retrieve()
                .body(SelectionResponse.class);
    }

    /**
     * Парсинг карточки по SKU. Вызывает POST /api/v1/ozon/card/by-id
     *
     * @return - очищенные данные карточки
     */
    public CardResponse parseById(ParseByIdRequest request) {
        return restClient.post()
                .uri("/api/v1/ozon/card/by-id")
                .body(request)
                .retrieve()
                .body(CardResponse.class);
    }

    /**
     * Парсинг карточки по URL. Вызывает POST /api/v1/ozon/card/by-url
     *
     * @return - очищенные данные карточки
     */
    public CardResponse parseById(ParseByUrlRequest request) {
        return restClient.post()
                .uri("/api/v1/ozon/card/by-url")
                .body(request)
                .retrieve()
                .body(CardResponse.class);
    }
}
