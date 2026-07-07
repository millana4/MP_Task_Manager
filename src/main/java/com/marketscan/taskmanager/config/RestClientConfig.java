package com.marketscan.taskmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.PropertyNamingStrategies;

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
        // Отдельный JsonMapper со snake_case: парсер ждёт is_seasonal / base_share,
        // а наши поля — isSeasonal / baseShare. Эта стратегия переводит одно в другое
        // для всех запросов и ответов клиента.
        JsonMapper snakeMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();

        return RestClient.builder()
                .baseUrl(baseUrl)
                .messageConverters(converters -> {
                    // Убираем стандартный JSON-конвертер и ставим свой со snake_case.
                    converters.removeIf(c -> c instanceof JacksonJsonHttpMessageConverter);
                    converters.add(0, new JacksonJsonHttpMessageConverter(snakeMapper));
                })
                .build();
    }
}