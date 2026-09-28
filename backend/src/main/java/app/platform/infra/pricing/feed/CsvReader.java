package app.platform.infra.pricing.feed;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 reader: quoted fields may contain the delimiter, line breaks and doubled quotes.
 * Streams one record at a time so large feeds are not held in memory.
 */
final class CsvReader {

    private final Reader reader;
    private final char delimiter;
    private int peeked = -2;

    CsvReader(Reader reader, char delimiter) {
        this.reader = reader;
        this.delimiter = delimiter;
    }

    /** Next record, or {@code null} at end of input. Blank lines are skipped. */
    List<String> next() throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        int c;
        while ((c = read()) != -1) {
            any = true;
            if (quoted) {
                if (c == '"') {
                    if (peek() == '"') {
                        read();
                        field.append('"');
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append((char) c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && peek() == '\n') {
                    read();
                }
                if (fields.isEmpty() && field.isEmpty()) {
                    any = false;
                    continue;
                }
                fields.add(field.toString());
                return fields;
            } else {
                field.append((char) c);
            }
        }
        if (!any) {
            return null;
        }
        fields.add(field.toString());
        return fields;
    }

    private int read() throws IOException {
        if (peeked != -2) {
            int value = peeked;
            peeked = -2;
            return value;
        }
        return reader.read();
    }

    private int peek() throws IOException {
        if (peeked == -2) {
            peeked = reader.read();
        }
        return peeked;
    }
}
