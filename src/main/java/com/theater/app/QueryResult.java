package com.theater.app;

import java.util.List;

public final class QueryResult {
    private final List<String> columns;
    private final List<List<String>> rows;
    private final int updateCount;
    private final boolean hasResultSet;

    private QueryResult(List<String> columns, List<List<String>> rows, int updateCount, boolean hasResultSet) {
        this.columns = columns;
        this.rows = rows;
        this.updateCount = updateCount;
        this.hasResultSet = hasResultSet;
    }

    public static QueryResult rows(List<String> columns, List<List<String>> rows) {
        return new QueryResult(columns, rows, -1, true);
    }

    public static QueryResult updated(int updateCount) {
        return new QueryResult(List.of(), List.of(), updateCount, false);
    }

    public List<String> columns() {
        return columns;
    }

    public List<List<String>> rows() {
        return rows;
    }

    public int updateCount() {
        return updateCount;
    }

    public boolean hasResultSet() {
        return hasResultSet;
    }
}
