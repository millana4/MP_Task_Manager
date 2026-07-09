# Mercator Task Manager

Сервис управления сбором данных о товарах маркетплейсов. Принимает наборы страт
(сеты) в виде CSV, наполняет их реальными карточками через парсер и
периодически обходит собранные карточки, накапливая временной ряд цен, наличия и
характеристик для эконометрического анализа.

Оркеструет внешний парсер: раздаёт задачи подбора и обхода, принимает результаты,
раскладывает их по двум базам — Postgres (структура и статусы) и ClickHouse
(временные ряды и снимки).

Работает с парсером двумя способами: синхронно через HTTP (для отладки) или
асинхронно через Kafka (основной режим). Сейчас поддерживает Ozon, регион
Санкт-Петербург.

## Стек технологий

- Java 21
- Spring Boot 4.1
- Gradle (Kotlin DSL)
- Spring Data JPA + Hibernate — доступ к Postgres
- Spring JDBC — доступ к ClickHouse
- Spring Kafka — асинхронный транспорт задач
- Jackson 3 — сериализация, snake_case-контракт с парсером
- Flyway — миграции схемы Postgres
- RestClient — HTTP-клиент парсера (синхронный путь)
- Testcontainers, Awaitility — интеграционные тесты
- Docker Compose — инфраструктура (Postgres, ClickHouse, Kafka)

## Структура приложения

```
src/main/java/com/marketscan/taskmanager/
├── TaskmanagerApplication.java   # точка входа Spring Boot
├── contract/Ozon/                # контракты обмена с парсером
│   ├── OzonCard, OzonPrice, OzonSeller, OzonLocation, OzonVariant
│   ├── StratumRequest, SelectionResponse   # подбор
│   ├── ParseByIdRequest, CardResponse      # обход
│   ├── ExcludedCard, Collection            # исключения при доборе
├── entity/                       # JPA-сущности Postgres
│   ├── SetEntity, SetClothingEntity, CardEntity
├── repository/                   # репозитории Postgres (Spring Data)
│   ├── SetRepository, SetClothingRepository, CardRepository
├── clickhouse/                   # модели и репозитории ClickHouse
│   ├── Measurement, MeasurementRepository        # замеры (пульс)
│   ├── CardSnapshot, CardSnapshotRepository       # снимки (медленные данные)
├── set_csv/                      # разбор CSV со стратами
│   ├── StratumCsvParser, StratumRow
├── client/                       # HTTP-клиент парсера (синхронный путь)
│   ├── OzonParserClient
├── kafka/                        # Kafka-транспорт
│   ├── GeoTopics                 # маршрутизация по гео (гео → код топика)
│   ├── SelectTaskMessage, SelectResultMessage    # сообщения подбора
│   ├── ParseTaskMessage, ParseResultMessage      # сообщения обхода
│   ├── SelectTaskProducer, ParseTaskProducer     # отправка задач
│   ├── SelectResultConsumer, ParseResultConsumer # приём результатов
│   ├── DlqProducer               # dead-letter для необрабатываемых сообщений
├── service/                      # бизнес-логика
│   ├── SetImportService          # загрузка CSV → сет + страты
│   ├── SetFillService            # синхронное наполнение (прямой HTTP)
│   ├── SetFillKafkaService       # асинхронное наполнение (Kafka) + exclude
│   ├── CardWriteService          # запись карточки в 3 базы (общая для обоих путей)
│   ├── JsonHelper                # сериализация вариантов в JSON
│   ├── FillBatch, FillBatchRegistry    # учёт партий наполнения (в памяти)
│   ├── FillBatchScheduler        # закрытие партий (все пришли / таймаут)
│   ├── CardLifecycleService      # статусы карточек (active/stale/dropped)
│   ├── MonitoringScheduler       # ежечасный обход активных карточек
├── controller/                   # HTTP-эндпоинты
│   ├── SetImportController        # загрузка и наполнение сета
│   ├── AnalyticsController        # выдача данных для аналитики
└── config/                       # конфигурация
    ├── DataSourceConfig          # два источника данных (Postgres + ClickHouse)
    ├── RestClientConfig          # HTTP-клиент парсера (snake_case)
    ├── KafkaTopicsConfig         # объявление топиков

src/main/resources/
├── application.yml               # настройки (БД, Kafka, парсер, таймауты)
├── db/migration/                 # миграции Postgres (Flyway)
└── clickhouse/schema.sql         # схема ClickHouse (накатывается вручную)

src/test/java/...                 # интеграционные тесты (Testcontainers, живые контейнеры)
```

## Запуск в Docker

Инфраструктура - Postgres, ClickHouse, Kafka:

```
docker compose up -d
```

Поднимаются `tm-postgres` (5433), `tm-clickhouse` (8123), `tm-kafka` (9092).

Схема Postgres накатывается автоматически через Flyway при старте приложения.
Топики Kafka создаются автоматически при старте. Схему ClickHouse нужно накатить
вручную один раз:

```
docker exec -i tm-clickhouse clickhouse-client --database taskmanager \
  --password taskmanager --multiquery < src/main/resources/clickhouse/schema.sql
```

Приложение:

```
./gradlew bootRun
```

Стартует на порту 8080. Для наполнения и обхода нужен запущенный парсер Ozon
(`localhost:8010`, настраивается через `PARSER_OZON_URL`).

## Эндпоинты

Загрузка и наполнение сета:

```
POST /api/v1/sets/import          # загрузить CSV → создать сет и страты
POST /api/v1/sets/{id}/fill        # наполнить синхронно (прямой HTTP, для отладки)
POST /api/v1/sets/{id}/fill-async  # наполнить асинхронно через Kafka (202 Accepted)
```

Выдача данных для аналитики:

```
GET /api/v1/analytics/cards/{cardId}/measurements   # временной ряд замеров карточки
GET /api/v1/analytics/strata/{stratumId}/cards      # карточки страты
```

Служебное:

```
GET /actuator/health              # состояние сервиса
```

Пример загрузки:

```
curl -X POST http://localhost:8080/api/v1/sets/import \
  -F "file=@set.csv" \
  -F "marketplace=ozon" \
  -F "category=clothing" \
  -F "geo=Санкт-Петербург"
```

## Формат CSV

Полная рабочая таблица со стратами: разделитель — запятая, кодировка UTF-8, с
заголовком. Сервис берёт по именам колонок семь нужных полей: 
- гендер (Ж/М),
- слой («0 нательный» → 0), 
- предмет одежды, 
- кол-во SKU в страте, 
- сезонный сплит - TRUE/FALSE, 
- доля базы (только у сезонных), 
- поисковый запрос. 

Остальные колонки игнорируются, пустые строки отбрасываются. Одна загрузка = новый сет.

## Работа через Kafka

Основной режим взаимодействия с парсером. Таск-менеджер отправляет задачи в топики
и принимает результаты асинхронно — HTTP-соединение не удерживается на время
работы парсера. Логика записи данных одна и та же для синхронного и асинхронного
путей (CardWriteService).

Два независимых потока, топики именуются с суффиксом кода региона (сейчас `spb`):

| Поток   | Топик задач (пишет)  | Топик результатов (читает) |
|---------|----------------------|-----------------------------|
| Подбор  | `select.tasks.spb`   | `select.results.spb`        |
| Обход   | `parse.tasks.spb`    | `parse.results.spb`         |

Маршрутизация по гео — через разные топики: парсер региона читает только свой
топик задач. Результаты читаются по шаблону (`select.results.*`, `parse.results.*`)
— один консьюмер ловит все регионы.

Сообщения — UTF-8 JSON в snake_case. Каждая задача несёт эхо-поля трассировки
(`task_id`, `set_id`, `stratum_id`, `card_id`, `geo`), которые парсер возвращает в
результате без изменений — по ним результат привязывается к задаче без хранения
состояния. Необрабатываемые сообщения уходят в dead-letter топик
`select.results.dlq`.

Контракт сообщений для парсера описан в отдельном ТЗ (`tz-parser-kafka.md`).

## Учёт партий наполнения

Наполнение сета отслеживается как партия: сервис знает, сколько задач отправлено и
сколько результатов вернулось. Партия закрывается, когда пришли все результаты либо
истёк таймаут (по умолчанию 4 часа, `taskmanager.fill.batch-timeout`). Планировщик
проверяет партии и закрывает завершённые. Новый цикл наполнения сета не стартует,
пока открыта партия предыдущего.

Состояние партий — в памяти (изолировано в `FillBatchRegistry`), не переживает
перезапуск приложения. При перезапуске данные не теряются (карточки пишутся в
любом случае), теряется лишь знание о завершённости партии.

## Жизненный цикл карточки

- `active` — карточка отвечает при обходе;
- `stale` — не ответила, счётчик неудач растёт (порог
  `taskmanager.card.max-failed-attempts`, по умолчанию 5);
- `dropped` — списана после превышения порога.

Успешный обход возвращает карточку в `active` и сбрасывает счётчик. Выбывшие
карточки восполняются добором через `exclude`.

## Периодический обход

Планировщик раз в час обходит активные карточки: отправляет задачи парсинга в
`parse.tasks.spb` и дописывает свежие замеры в ClickHouse по результатам. Новый
цикл не стартует, пока идёт предыдущий.

## Хранилища

- **Postgres** — сеты, страты, карточки, статусы. Схема через Flyway
  (`db/migration`).
- **ClickHouse** — замеры цен/наличия, характеристики, варианты товара, эмбеддинг. Схема накатывается
  вручную.

Связь между базами — по `card_id` (UUID из Postgres) и артикулу.

## Резервные копии и выгрузка данных

Данные Postgres и ClickHouse лежат в Docker volumes (`pg-data`, `ch-data`) и
переживают перезапуск контейнеров. Папка `./backups` смонтирована в оба
контейнера как `/backups` — файлы, созданные там внутри, появляются в `./backups`
на хосте. Папка исключена из git.

### Postgres

Полный дамп:

```bash
docker exec tm-postgres pg_dump -U taskmanager taskmanager > backups/pg_$(date +%Y%m%d_%H%M).sql
```

Восстановление:

```bash
cat backups/pg_ГГГГММДД_ЧЧММ.sql | docker exec -i tm-postgres psql -U taskmanager -d taskmanager
```

### ClickHouse

Пароль — из `.env` (`CLICKHOUSE_PASSWORD`), при необходимости подставь явно.

```bash
# замеры
docker exec tm-clickhouse clickhouse-client --database taskmanager \
  --password "$CLICKHOUSE_PASSWORD" \
  --query "SELECT * FROM measurement FORMAT CSVWithNames" > backups/measurement_$(date +%Y%m%d).csv

# снимки карточек (FINAL — актуальная версия)
docker exec tm-clickhouse clickhouse-client --database taskmanager \
  --password "$CLICKHOUSE_PASSWORD" \
  --query "SELECT * FROM card_snapshot FINAL FORMAT CSVWithNames" > backups/snapshot_$(date +%Y%m%d).csv
```
## Тесты

```
./gradlew test
```

Интеграционные тесты используют Testcontainers (временный Postgres) и живые
контейнеры (ClickHouse, Kafka). Приёмный контур Kafka проверяется подстановкой
фейкового результата в топик — роль парсера играет тестовый двойник, что позволяет
проверить приём и запись без настоящего парсера. Живые тесты, требующие
запущенного парсера, помечены `@Disabled` и запускаются вручную.

## Настройки (application.yml)

| Параметр | Назначение |
|----------|------------|
| `spring.datasource.*` | подключение к Postgres (порт 5433) |
| `clickhouse.*` | подключение к ClickHouse (порт 8123) |
| `spring.kafka.bootstrap-servers` | адрес брокера Kafka (`KAFKA_BOOTSTRAP`) |
| `parser.ozon.base-url` | адрес парсера Ozon (`PARSER_OZON_URL`) |
| `taskmanager.fill.batch-timeout` | таймаут партии наполнения (по умолчанию PT4H) |
| `taskmanager.card.max-failed-attempts` | порог неудач до списания карточки (по умолчанию 5) |

