package com.stagepass.organizer.service;

import java.util.List;
import java.util.stream.Collectors;

/** RFC 4180 CSV cells and rows, with a guard against spreadsheet formula injection. */
public final class CsvWriter {

    public static final String BOM = "﻿";

    private CsvWriter() {
    }

    /** One cell: formula-like text gets a leading {@code '}; commas, quotes and line breaks are quoted. */
    public static String cell(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String v = raw;
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Cells joined with commas, ending in CRLF. */
    public static String row(List<String> cells) {
        return cells.stream().map(CsvWriter::cell).collect(Collectors.joining(",")) + "\r\n";
    }
}
