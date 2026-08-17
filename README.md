# Mercator Task Manager

Сервис управления сбором данных о товарах маркетплейсов. Принимает наборы страт
(сеты) в виде CSV, наполняет их реальными карточками через парсер и
непрерывно обходит собранные карточки, накапливая временной ряд цен, наличия и
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
src/main/java/com/mercator/taskmanager/
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
│   ├── SetFillKafkaService       # асинхронное наполнение (Kafka) + exclude + добор
│   ├── CardWriteService          # запись карточки в 3 базы (общая для обоих путей)
│   ├── JsonHelper                # сериализация вариантов в JSON
│   ├── FillBatch, FillBatchRegistry    # учёт партий наполнения (в памяти)
│   ├── FillBatchScheduler        # закрытие партий (все пришли / таймаут)
│   ├── CardLifecycleService      # статусы карточек (active/stale/dropped) + наличие
│   ├── MonitoringCycle           # один проход обхода активных карточек
│   ├── MonitoringScheduler       # непрерывный цикл обхода
│   ├── RefillScheduler           # автодобор выбывших карточек (cron)
├── controller/                   # HTTP-эндпоинты
│   ├── SetImportController        # загрузка и наполнение сета
│   ├── AnalyticsController        # выдача данных для аналитики
└── config/                       # конфигурация
    ├── DataSourceConfig          # два источника данных (Postgres + ClickHouse)
    ├── RestClientConfig          # HTTP-клиент парсера (snake_case)
    ├── KafkaTopicsConfig         # объявление топиков

src/main/resources/
├── application.yaml              # настройки (БД, Kafka, парсер, таймауты)
├── db/migration/                 # миграции Postgres (Flyway)
└── clickhouse/schema.sql         # эталонная схема ClickHouse

docker/clickhouse-init/           # авто-накат схемы ClickHouse при первом старте

src/test/java/...                 # юнит- и интеграционные тесты
```

## Запуск в Docker

Инфраструктура — Postgres, ClickHouse, Kafka — и приложение поднимаются одним
compose:

```
docker compose up -d --build
```

Поднимаются `tm-postgres`, `tm-clickhouse`, `tm-kafka` (9092 наружу — к нему
подключается парсер) и `tm-app` (8080 наружу — API). Порты Postgres и ClickHouse
наружу по умолчанию не проброшены (общение внутри compose-сети); для отладки с
хоста проброс раскомментируется в `docker-compose.yml`.

Схема Postgres накатывается автоматически через Flyway при старте приложения.
Топики Kafka создаются автоматически при старте. Схема ClickHouse накатывается
автоматически при **первом** старте контейнера из `docker/clickhouse-init/`
(срабатывает только на пустом volume). Если volume уже с данными и таблиц нет —
накатить вручную один раз:

```
docker exec -i tm-clickhouse clickhouse-client --database taskmanager \
  --password "$CLICKHOUSE_PASSWORD" --multiquery < src/main/resources/clickhouse/schema.sql
```

Приложение стартует на порту 8080. Для наполнения и обхода нужен запущенный
парсер Ozon (`localhost:8010`, настраивается через `PARSER_OZON_URL`).

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
- сезонный сплит — TRUE/FALSE,
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

Топик обхода `parse.tasks.spb` создаётся с **3 партициями** — под несколько
параллельных парсеров-воркеров (мультипарсер, схема 2 primary + 1 reserve). Все
воркеры в одной consumer-группе `ozon-parser-spb`; Kafka сама раздаёт партиции
между ними. Остальные топики — с 1 партицией (их читает только ядро). Число
партиций задаётся в `KafkaTopicsConfig`.

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

Статус карточки хранится в Postgres (`card.status`), признак наличия — в
`card.out_of_stock_since`, счётчик неудач парсинга — в `card.failed_attempts`.

- `active` — карточка в наборе, её обходят;
- `stale` — обход не удался, счётчик неудач растёт (порог
  `taskmanager.card.max-failed-attempts`, по умолчанию 5);
- `dropped` — карточка выбыла из набора.

Переходы (`CardLifecycleService`):

- **успешный обход, товар в наличии** → `active`, счётчик неудач сброшен,
  `out_of_stock_since` очищен;
- **товар пропал из продажи** → фиксируется `out_of_stock_since`; если наличие
  не вернулось в течение grace-периода (`taskmanager.card.notify-grace-days`, по
  умолчанию 7 дней) → `dropped`;
- **карточка удалена с маркетплейса (404, `not_found`)** → сразу `dropped`;
- **обход стабильно падает** (не антибот, а реальная ошибка) → счётчик растёт,
  после порога → `dropped`. Блокировки антибота (`antibot_blocked`) счётчик
  неудач **не** увеличивают.

Выбывшие (`dropped`) карточки восполняются автодобором (см. ниже).

## Автодобор

Выбывшие карточки восполняются автоматически, чтобы держать наполнение страт на
целевом уровне. `RefillScheduler` по расписанию (cron) считает дефицит по каждой
страте (`count − active`) и отправляет парсеру задачи подбора замены, передавая
список уже собранных SKU в `exclude` (чтобы не подбирать дубли — ни внутри страты,
ни между стратами сета). Управляется флагом `taskmanager.refill.enabled`.

## Непрерывный обход

`MonitoringScheduler` непрерывным циклом обходит активные карточки: отправляет
задачи парсинга в `parse.tasks.spb` и по результатам дописывает свежие замеры в
ClickHouse. Один проход обхода — `MonitoringCycle`. Новый цикл не стартует, пока
идёт предыдущий. Так по каждой карточке накапливается временной ряд замеров.

## Хранилища

- **Postgres** — сеты, страты, карточки, статусы. Схема через Flyway
  (`db/migration`).
- **ClickHouse** — замеры цен/наличия, характеристики, варианты товара, эмбеддинг.
  Схема накатывается автоматически при первом старте из `docker/clickhouse-init/`.

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

### Автоматические бэкапы (cron)

На боевом сервере дампы снимаются по расписанию скриптом `backup.sh` в папке
ядра, а свежие копии автоматически забираются на локальный компьютер. Данные и
так лежат в Docker volumes и переживают перезапуск — автобэкап нужен на случай
потери сервера и для выгрузки рядов к анализу.

Как это устроено:

- **`backup.sh`** на сервере — снимает дамп Postgres (`pg_*.sql`) и выгружает
  таблицы ClickHouse (`ch_measurement_*.csv`, `ch_snapshot_*.csv`) в `./backups`,
  сам подхватывает пароли из `.env` (cron не наследует переменные сессии) и
  удаляет копии старше 60 дней.
- **cron на сервере** — запускает `backup.sh` еженедельно (понедельник 3:00).
- **cron на локальном компьютере** — скрипт `pull-backups.sh` через `rsync`
  забирает свежие дампы с сервера (понедельник 12:00, после серверного дампа).

Полная пошаговая настройка (тексты обоих скриптов, строки crontab, вход по
SSH-ключу) — в `deploy-guide.md`, часть «Автоматические резервные копии».

### Ручной дамп

Разовый дамп обеих баз — просто запустить скрипт на сервере:

```bash
cd ~/mercator_taskmanager && ./backup.sh && ls -lh backups/
```

Появятся `pg_*.sql`, `ch_measurement_*.csv`, `ch_snapshot_*.csv` с текущей датой.

Восстановление Postgres из дампа:

```bash
cat backups/pg_ГГГГММДД_ЧЧММ.sql | docker exec -i -e PGPASSWORD="$POSTGRES_PASSWORD" \
  tm-postgres psql -U taskmanager -d taskmanager
```

## Тесты

Быстрые (юнит) тесты — без Docker и БД:

```
./gradlew test
```

Интеграционные тесты используют Testcontainers (временный Postgres) и живые
контейнеры (ClickHouse, Kafka). Приёмный контур Kafka проверяется подстановкой
фейкового результата в топик — роль парсера играет тестовый двойник, что позволяет
проверить приём и запись без настоящего парсера. Такие тесты помечены
`@Tag("integration")` и вынесены из обычного прогона в отдельную задачу — при
поднятой инфраструктуре:

```
./gradlew integrationTest
```

## Настройки (application.yaml)

| Параметр | Назначение |
|----------|------------|
| `spring.datasource.*` | подключение к Postgres |
| `clickhouse.*` | подключение к ClickHouse |
| `spring.kafka.bootstrap-servers` | адрес брокера Kafka (`KAFKA_BOOTSTRAP`) |
| `parser.ozon.base-url` | адрес парсера Ozon (`PARSER_OZON_URL`) |
| `taskmanager.fill.batch-timeout` | таймаут партии наполнения (по умолчанию PT4H) |
| `taskmanager.card.max-failed-attempts` | порог неудач до списания карточки (по умолчанию 5) |
| `taskmanager.card.notify-grace-days` | grace-период отсутствия товара до списания (по умолчанию 7) |
| `taskmanager.refill.enabled` | включение автодобора выбывших карточек |
