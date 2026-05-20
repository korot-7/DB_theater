package com.theater.app;

public record QueryDefinition(
        int id,
        String section,
        String title,
        String sql
) {
}
