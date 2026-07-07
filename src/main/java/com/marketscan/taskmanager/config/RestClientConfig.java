package com.marketscan.taskmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP-клиент для вызова парсера Ozon.
 *
 * RestClient — рекомендованный в Spring Boot 4 синхронный HTTP-клиент
 * baseUrl задаём один раз здесь, дальше клиент парсера просто указывает пути эндпоинтов.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient ozonParserRestClient(
            @Value("${parser.ozon.base-url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}