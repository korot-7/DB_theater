package com.theater.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseService {
    private Connection connection;

    public void connect(String host, String port, String database, String user, String password) throws SQLException {
        disconnect();
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        connection = DriverManager.getConnection(url, user, password);
    }

    public void disconnect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        connection = null;
    }

    public boolean isConnected() throws SQLException {
        return connection != null && !connection.isClosed();
    }

    public QueryResult executeSql(String sql) throws SQLException {
        ensureConnected();
        try (Statement statement = connection.createStatement()) {
            boolean hasResult = statement.execute(sql);
            if (hasResult) {
                try (ResultSet rs = statement.getResultSet()) {
                    return toQueryResult(rs);
                }
            }
            return QueryResult.updated(statement.getUpdateCount());
        }
    }

    public QueryResult executePrepared(String sql, SqlBinder binder) throws SQLException {
        ensureConnected();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            boolean hasResult = statement.execute();
            if (hasResult) {
                try (ResultSet rs = statement.getResultSet()) {
                    return toQueryResult(rs);
                }
            }
            return QueryResult.updated(statement.getUpdateCount());
        }
    }

    public int executeScript(List<String> statements) throws SQLException {
        ensureConnected();
        int executed = 0;
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
                executed++;
            }
        }
        return executed;
    }

    private QueryResult toQueryResult(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnLabel(i));
        }

        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                row.add(value == null ? "" : value.toString());
            }
            rows.add(row);
        }
        return QueryResult.rows(columns, rows);
    }

    private void ensureConnected() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("Нет подключения к базе данных.");
        }
    }

    @FunctionalInterface
    public interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
