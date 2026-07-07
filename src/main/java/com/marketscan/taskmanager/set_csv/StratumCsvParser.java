package com.marketscan.taskmanager.set_csv;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Разбирает CSV со стратами одежды в список StratumRow.
 *
 * На вход идёт «полная» рабочая таблица со всеми расчётными колонками —
 * парсер берёт только нужные 7 по их заголовкам, остальное игнорирует.
 * Поэтому разбор идёт по ИМЕНАМ колонок, а не по позициям.
 */
@Component
public class StratumCsvParser {

    // Имена нужных колонок в заголовке (как в твоём файле).
    // trim() при чтении снимет висячие пробелы, поэтому здесь без них.
    private static final String COL_GENDER = "Гендер";
    private static final String COL_LAYER = "Слой";
    private static final String COL_ITEM = "Предмет";
    private static final String COL_COUNT = "Кол-во итоговое с округлением";
    private static final String COL_SEASONAL = "Сезонный сплит";
    private static final String COL_BASE_SHARE = "Доля базы";
    private static final String COL_QUERY = "Поисковый запрос";

    public List<StratumRow> parse(InputStream csvStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV пустой: нет строки заголовка");
            }

            // Разбираем заголовок и запоминаем, в какой позиции какая нужная колонка.
            String[] headers = splitCsvLine(headerLine);
            int idxGender = indexOf(headers, COL_GENDER);
            int idxLayer = indexOf(headers, COL_LAYER);
            int idxItem = indexOf(headers, COL_ITEM);
            int idxCount = indexOf(headers, COL_COUNT);
            int idxSeasonal = indexOf(headers, COL_SEASONAL);
            int idxBaseShare = indexOf(headers, COL_BASE_SHARE);
            int idxQuery = indexOf(headers, COL_QUERY);

            List<StratumRow> rows = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = splitCsvLine(line);
                // Пропускаем «пустые по смыслу» строки: например хвостовая
                // строка из одних запятых (,,,,) не isBlank(), но данных не несёт.
                // Признак — пустой предмет (item): без него страта бессмысленна.
                String item = get(cells, idxItem).trim();
                if (item.isEmpty()) {
                    continue;
                }
                rows.add(parseRow(cells, lineNumber,
                        idxGender, idxLayer, idxItem, idxCount,
                        idxSeasonal, idxBaseShare, idxQuery));
            }
            return rows;

        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать CSV: " + e.getMessage(), e);
        }
    }

    private StratumRow parseRow(String[] cells, int lineNumber,
                                int idxGender, int idxLayer, int idxItem, int idxCount,
                                int idxSeasonal, int idxBaseShare, int idxQuery) {
        StratumRow row = new StratumRow();

        row.setItem(get(cells, idxItem).trim());
        row.setQuery(get(cells, idxQuery).trim());
        row.setGender(parseGender(get(cells, idxGender).trim(), lineNumber));
        row.setLayer(parseLayer(get(cells, idxLayer).trim(), lineNumber));
        row.setCount(parseCount(get(cells, idxCount).trim(), lineNumber));
        row.setIsSeasonal(parseBoolean(get(cells, idxSeasonal).trim()));
        row.setBaseShare(parseBaseShare(get(cells, idxBaseShare).trim()));

        return row;
    }

    // "Ж" -> f, "М" -> m
    private String parseGender(String raw, int line) {
        return switch (raw) {
            case "Ж", "ж" -> "f";
            case "М", "м" -> "m";
            default -> throw new IllegalArgumentException(
                    "Строка " + line + ": непонятный гендер '" + raw + "' (ожидалось Ж или М)");
        };
    }

    // "0 нательный" -> 0, "1 базовый" -> 1 ...  Берём первую цифру.
    private Short parseLayer(String raw, int line) {
        for (char c : raw.toCharArray()) {
            if (Character.isDigit(c)) {
                return (short) Character.getNumericValue(c);
            }
        }
        throw new IllegalArgumentException(
                "Строка " + line + ": не нашёл номер слоя в '" + raw + "'");
    }

    private Integer parseCount(String raw, int line) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Строка " + line + ": количество '" + raw + "' не число");
        }
    }

    // "TRUE"/"FALSE" (в любом регистре) -> boolean
    private Boolean parseBoolean(String raw) {
        return "TRUE".equalsIgnoreCase(raw);
    }

    // "0.5" -> 0.5; пусто -> null (доля базы есть только у сезонных)
    private BigDecimal parseBaseShare(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw);
    }

    // Находит позицию колонки по имени (с учётом trim и без учёта регистра).
    private int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("В CSV нет колонки '" + name + "'");
    }

    private String get(String[] cells, int idx) {
        return idx < cells.length ? cells[idx] : "";
    }

    /**
     * Простой разбор строки CSV с учётом кавычек: значения в кавычках
     * (например «Носки, колготки и чулки женские») могут содержать запятые.
     */
    private String[] splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }
}
