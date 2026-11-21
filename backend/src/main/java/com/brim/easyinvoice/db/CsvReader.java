package com.brim.easyinvoice.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny CSV parser (RFC 4180): commas, "quoted, fields", doubled "" quotes, newlines inside quotes.
 * Returns one map per row, keyed by the header names, in column order.
 */
public final class CsvReader {

    private CsvReader() {
    }

    public static List<Map<String, String>> read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (text.startsWith("﻿")) text = text.substring(1); // byte-order mark from Excel

        List<List<String>> rows = parse(text);
        List<String> header = rows.getFirst();
        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            if (row.size() == 1 && row.getFirst().isEmpty()) continue; // blank line
            Map<String, String> record = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                record.put(header.get(i), i < row.size() ? row.get(i) : "");
            }
            result.add(record);
        }
        return result;
    }

    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
