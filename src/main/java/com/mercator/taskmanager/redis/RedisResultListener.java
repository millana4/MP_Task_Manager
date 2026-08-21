package com.mercator.taskmanager.redis;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Фоновый приём результатов из Redis: на каждый
 * поток результатов (обход, подбор) — свой поток, крутящий блокирующий BRPOP
 * по ключу результатов региона, и передающий JSON соответствующему обработчику.
 *
 * Пока регион один (geo из конфига). Для нескольких регионов здесь заводится
 * по потоку на ключ (или BRPOP по нескольким ключам) — обработчики те же.
 *
 * BRPOP с таймаутом: поток периодически «просыпается», чтобы корректно
 * реагировать на остановку (running=false) и не висеть вечно на выключении.
 */
@Component
public class RedisResultListener {

    private static final Logger log = LoggerFactory.getLogger(RedisResultListener.class);
    private static final Duration BRPOP_TIMEOUT = Duration.ofSeconds(2);

    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final ParseResultConsumer parseConsumer;
    private final SelectResultConsumer selectConsumer;
    private final String geo;

    private volatile boolean running = true;
    private ExecutorService pool;

    public RedisResultListener(StringRedisTemplate redis,
                               RedisKeys keys,
                               ParseResultConsumer parseConsumer,
                               SelectResultConsumer selectConsumer,
                               @Value("${taskmanager.geo:Санкт-Петербург}") String geo) {
        this.redis = redis;
        this.keys = keys;
        this.parseConsumer = parseConsumer;
        this.selectConsumer = selectConsumer;
        this.geo = geo;
    }

    @PostConstruct
    public void start() {
        pool = Executors.newFixedThreadPool(2);
        String parseKey = keys.parseResults(geo);
        String selectKey = keys.selectResults(geo);
        pool.submit(() -> loop(parseKey, parseConsumer::handle, "обход"));
        pool.submit(() -> loop(selectKey, selectConsumer::handle, "подбор"));
        log.info("RedisResultListener запущен: слушаю {} и {}", parseKey, selectKey);
    }

    private void loop(String key, java.util.function.Consumer<String> handler, String label) {
        log.info("Поток результатов ({}) слушает {}", label, key);
        while (running) {
            try {
                String json = redis.opsForList().rightPop(key, BRPOP_TIMEOUT);
                if (json == null) {
                    continue;  // таймаут BRPOP — проверяем running и ждём дальше
                }
                handler.accept(json);
            } catch (Exception e) {
                if (!running) {
                    // Идёт остановка контекста — соединение закрывается, это ожидаемо.
                    log.debug("Поток результатов ({}) завершается на остановке: {}", label, e.getMessage());
                    break;
                }
                // Не роняем поток из-за одной ошибки — логируем и продолжаем.
                log.error("Ошибка обработки результата ({}) из {}: {}", label, key, e.getMessage(), e);
            }
        }
        log.info("Поток результатов ({}) остановлен", label);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (pool != null) {
            pool.shutdown();
        }
        log.info("RedisResultListener остановлен");
    }
}