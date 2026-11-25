package com.meanderatlas.ingestion.domain.takeout;

import java.util.List;
import java.util.stream.Stream;

final class CsvRecordReader {

    private static final char COMMA = ',';
    private static final char QUOTE = '"';
    private static final char CARRIAGE_RETURN = '\r';
    private static final char LINE_FEED = '\n';

    private CsvRecordReader() {}

    static List<List<String>> readRecords(String content) {
        Stream.Builder<List<String>> records = Stream.builder();
        Stream.Builder<String> currentRow = Stream.builder();
        boolean rowHasFields = false;
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int length = content.length();

        for (int i = 0; i < length; i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == QUOTE) {
                    if (i + 1 < length && content.charAt(i + 1) == QUOTE) {
                        currentField.append(QUOTE);
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentField.append(ch);
                }
            } else if (ch == QUOTE && currentField.isEmpty()) {
                inQuotes = true;
            } else if (ch == COMMA) {
                currentRow.add(currentField.toString());
                rowHasFields = true;
                currentField.setLength(0);
            } else if (ch == CARRIAGE_RETURN || ch == LINE_FEED) {
                if (ch == CARRIAGE_RETURN && i + 1 < length && content.charAt(i + 1) == LINE_FEED) {
                    i++;
                }
                currentRow.add(currentField.toString());
                currentField.setLength(0);
                records.add(currentRow.build().toList());
                currentRow = Stream.builder();
                rowHasFields = false;
            } else {
                currentField.append(ch);
            }
        }
        if (!currentField.isEmpty() || rowHasFields) {
            currentRow.add(currentField.toString());
            records.add(currentRow.build().toList());
        }
        return records.build().toList();
    }
}
