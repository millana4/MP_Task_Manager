package com.marketscan.taskmanager.client;

import com.marketscan.taskmanager.contract.Ozon.SelectionResponse;
import com.marketscan.taskmanager.contract.Ozon.StratumRequest;
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
}
