package com.gameexpert.sign.dto;

import java.util.List;

/** Durable four-line sign text at one placed sign coordinate. */
public record SignBlockData(int x, int y, int z, List<String> lines) {
    public static final int LINE_COUNT = 4;
    public static final int MAX_LINE_LENGTH = 90;

    public SignBlockData {
        if (lines == null || lines.size() != LINE_COUNT) {
            throw new IllegalArgumentException("sign requires exactly four lines");
        }
        lines = lines.stream().map(SignBlockData::validateLine).toList();
    }

    public static SignBlockData empty(int x, int y, int z) {
        return new SignBlockData(x, y, z, List.of("", "", "", ""));
    }

    private static String validateLine(String line) {
        if (line == null || line.length() > MAX_LINE_LENGTH || line.indexOf('\n') >= 0
                || line.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("invalid sign line");
        }
        return line;
    }
}
