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
            @Value("${parser.ozon.base-url}") String baseUrl,
            @Value("${parser.ozon.api-key:}") String apiKey) {

        JsonMapper snakeMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof JacksonJsonHttpMessageConverter);
                    converters.add(0, new JacksonJsonHttpMessageConverter(snakeMapper));
                });

        // Межсервисный ключ: парсер требует Authorization: Bearer <ключ>.
        // Если ключ задан — добавляем заголовок ко всем запросам клиента.
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        return builder.build();
    }
}