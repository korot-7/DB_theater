package com.theater.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueryCatalog {
    private static final Pattern QUERY_MARKER = Pattern.compile("^\\s*--\\s*Query\\s*#(\\d+)\\s*$");
    private static final int EXPECTED_QUERY_COUNT = 30;

    private static final String RAW_SQL = readRawSql();
    private static final List<QueryDefinition> QUERIES = parseQueries(RAW_SQL);
    private static final List<String> SETUP_STATEMENTS = parseSetupStatements(RAW_SQL);

    private QueryCatalog() {
    }

    public static List<QueryDefinition> queries() {
        return QUERIES;
    }

    public static List<String> setupStatements() {
        return SETUP_STATEMENTS;
    }

    private static String readRawSql() {
        try (InputStream stream = QueryCatalog.class.getResourceAsStream("/sql/select_query.sql")) {
            if (stream == null) {
                throw new IllegalStateException("Не найден ресурс /sql/select_query.sql в приложении.");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать SQL-каталог из ресурсов приложения.", e);
        }
    }

    private static List<QueryDefinition> parseQueries(String rawSql) {
        String[] lines = rawSql.split("\\R", -1);
        List<QuerySeed> seeds = new ArrayList<>();

        String section = "Запросы";
        String lastUsefulComment = null;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.startsWith("--")) {
                continue;
            }

            String comment = trimmed.substring(2).trim();
            if (comment.isBlank()) {
                continue;
            }

            if (comment.toUpperCase(Locale.ROOT).startsWith("ЗАПРОС")) {
                section = comment;
                continue;
            }

            Matcher markerMatcher = QUERY_MARKER.matcher(trimmed);
            if (markerMatcher.matches()) {
                int id = Integer.parseInt(markerMatcher.group(1));
                String title = lastUsefulComment == null ? "Query #" + id : lastUsefulComment;
                seeds.add(new QuerySeed(id, section, title, i));
                continue;
            }

            if (!comment.startsWith("=")) {
                lastUsefulComment = comment;
            }
        }

        List<QueryDefinition> definitions = new ArrayList<>(seeds.size());
        for (int i = 0; i < seeds.size(); i++) {
            QuerySeed current = seeds.get(i);
            int fromLine = current.markerLine() + 1;
            int toLine = (i + 1 < seeds.size()) ? seeds.get(i + 1).markerLine() : lines.length;
            String sql = toPositionalPlaceholders(joinRange(lines, fromLine, toLine).trim());
            definitions.add(new QueryDefinition(current.id(), current.section(), current.title(), sql));
        }

        if (definitions.size() != EXPECTED_QUERY_COUNT) {
            throw new IllegalStateException("Ожидалось " + EXPECTED_QUERY_COUNT + " запросов, найдено: " + definitions.size());
        }

        return List.copyOf(definitions);
    }

    private static List<String> parseSetupStatements(String rawSql) {
        String[] lines = rawSql.split("\\R", -1);
        int firstQueryMarker = -1;
        for (int i = 0; i < lines.length; i++) {
            if (QUERY_MARKER.matcher(lines[i].trim()).matches()) {
                firstQueryMarker = i;
                break;
            }
        }
        if (firstQueryMarker < 0) {
            throw new IllegalStateException("Не найдено маркеров Query #N в SQL-каталоге.");
        }

        String setupBlock = joinRange(lines, 0, firstQueryMarker);
        return List.copyOf(splitSqlStatements(setupBlock));
    }


    static String toPositionalPlaceholders(String sql) {
        return sql.replaceAll("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)", "?");
    }

    private static String joinRange(String[] lines, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (i > startInclusive) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inLineComment = false;
        String dollarTag = null;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                current.append(c);
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }

            if (dollarTag != null) {
                String maybeTag = matchDollarTag(sql, i);
                if (maybeTag != null && maybeTag.equals(dollarTag)) {
                    current.append(maybeTag);
                    i += maybeTag.length() - 1;
                    dollarTag = null;
                } else {
                    current.append(c);
                }
                continue;
            }

            if (inSingleQuote) {
                current.append(c);
                if (c == '\'') {
                    if (next == '\'') {
                        current.append(next);
                        i++;
                    } else {
                        inSingleQuote = false;
                    }
                }
                continue;
            }

            if (c == '-' && next == '-') {
                inLineComment = true;
                current.append(c);
                continue;
            }

            if (c == '\'') {
                inSingleQuote = true;
                current.append(c);
                continue;
            }

            if (c == '$') {
                String maybeTag = matchDollarTag(sql, i);
                if (maybeTag != null) {
                    dollarTag = maybeTag;
                    current.append(maybeTag);
                    i += maybeTag.length() - 1;
                    continue;
                }
            }

            current.append(c);
            if (c == ';') {
                pushStatement(statements, current);
            }
        }

        pushStatement(statements, current);
        return statements;
    }

    private static String matchDollarTag(String sql, int index) {
        if (sql.charAt(index) != '$') {
            return null;
        }
        int end = sql.indexOf('$', index + 1);
        if (end < 0) {
            return null;
        }
        for (int i = index + 1; i < end; i++) {
            char c = sql.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return null;
            }
        }
        return sql.substring(index, end + 1);
    }

    private static void pushStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        current.setLength(0);
        if (statement.isBlank()) {
            return;
        }
        String withoutLineComments = statement.replaceAll("(?m)^\\s*--.*$", "").trim();
        if (withoutLineComments.isBlank()) {
            return;
        }
        statements.add(statement);
    }

    private record QuerySeed(int id, String section, String title, int markerLine) {
        private QuerySeed {
            Objects.requireNonNull(section);
            Objects.requireNonNull(title);
        }
    }
}
