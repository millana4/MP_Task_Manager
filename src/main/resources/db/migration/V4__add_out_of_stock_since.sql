-- Отдельная отметка начала непрерывного out_of_stock (нет в наличии).
-- Не смешивать с unavailable_since (то про сбой парсинга).
-- Порог notify-grace-days считается от этого момента.
ALTER TABLE card ADD COLUMN out_of_stock_since timestamptz;