-- V2__schema.sql
-- Схема актуального состояния: сет (общее) → страты одежды → карточки.
-- Категорийная модель: set общий для всех категорий, set_clothing —
-- страты именно одежды. Новая категория = новая таблица set_<категория>,
-- set и card при этом не меняются.

-- ============================================================
-- set — шапка задания на сбор. Общее для любой категории.
-- Регион = отдельный сет (гео — свойство сета).
-- ============================================================
CREATE TABLE set (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    marketplace  TEXT NOT NULL,
    category     TEXT NOT NULL,
    geo          TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT set_marketplace_check CHECK (marketplace IN ('ozon', 'wb', 'yandex_market')),
    CONSTRAINT set_category_check    CHECK (category IN ('clothing', 'food', 'hygiene'))
);

-- ============================================================
-- set_clothing — страты одежды. Одна строка = одна страта = один предмет (item).
-- Общие атрибуты (item, query, count) + частные для одежды
-- (gender, layer, is_seasonal, base_share).
-- ============================================================
CREATE TABLE set_clothing (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    set_id       UUID NOT NULL REFERENCES set(id) ON DELETE CASCADE,

    item         TEXT NOT NULL,
    query        TEXT NOT NULL,
    count        INTEGER NOT NULL,

    gender       TEXT NOT NULL,
    layer        SMALLINT NOT NULL,
    is_seasonal  BOOLEAN NOT NULL,
    base_share   NUMERIC(4, 3),

    CONSTRAINT set_clothing_gender_check CHECK (gender IN ('m', 'f')),
    CONSTRAINT set_clothing_layer_check  CHECK (layer BETWEEN 0 AND 3),
    CONSTRAINT set_clothing_count_check  CHECK (count > 0),
    CONSTRAINT set_clothing_base_share_check
        CHECK (base_share IS NULL OR (base_share >= 0 AND base_share <= 1)),
    CONSTRAINT set_clothing_seasonal_share_check
        CHECK ((is_seasonal = false) OR (is_seasonal = true AND base_share IS NOT NULL))
);

CREATE INDEX idx_set_clothing_set_id ON set_clothing(set_id);

-- ============================================================
-- card — карточки, подобранные парсером под страту.
-- stratum_id ссылается на страту (физически — строка set_clothing).
-- seller_id и collection нужны для формирования exclude при доборе.
-- Блок жизненного цикла: status, счётчик попыток, отметки времени.
-- ============================================================
CREATE TABLE card (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stratum_id          UUID NOT NULL REFERENCES set_clothing(id) ON DELETE CASCADE,

    sku                 TEXT NOT NULL,
    name                TEXT,
    url                 TEXT,
    seller_id           TEXT,
    collection          TEXT,

    status              TEXT NOT NULL DEFAULT 'active',
    failed_attempts     INTEGER NOT NULL DEFAULT 0,
    unavailable_since   TIMESTAMPTZ,
    dropped_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT card_status_check
        CHECK (status IN ('active', 'stale', 'dropped')),
    CONSTRAINT card_collection_check
        CHECK (collection IS NULL OR collection IN ('base', 'spring_summer', 'autumn_winter')),
    CONSTRAINT card_stratum_sku_unique UNIQUE (stratum_id, sku)
);

CREATE INDEX idx_card_stratum_id ON card(stratum_id);
CREATE INDEX idx_card_status ON card(status);