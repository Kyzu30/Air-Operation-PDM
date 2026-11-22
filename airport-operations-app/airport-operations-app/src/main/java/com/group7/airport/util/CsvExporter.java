package com.group7.airport.util;

import com.group7.airport.model.QueryResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/* Saves a QueryResult as CSV (UTF-8 with BOM so Excel shows Vietnamese correctly). */
public final class CsvExporter {

    private CsvExporter() { }

    public static void write(QueryResult result, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('﻿');
            w.write(line(result.columns().toArray()));
            for (Object[] row : result.rows()) {
                w.write(line(row));
            }
        }
    }

    private static String line(Object[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            String v = values[i] == null ? "" : values[i].toString();
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                v = "\"" + v.replace("\"", "\"\"") + "\"";
            }
            sb.append(v);
        }
        return sb.append("\r\n").toString();
    }
}
