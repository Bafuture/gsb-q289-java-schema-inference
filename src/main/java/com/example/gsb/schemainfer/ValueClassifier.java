package com.example.gsb.schemainfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Classifies a single non-null sample value into one of the atomic base types
 * ({@link FieldType#INTEGER}, {@link FieldType#DECIMAL}, {@link FieldType#BOOLEAN},
 * {@link FieldType#DATETIME}, {@link FieldType#STRING}).
 *
 * <p>Checks run in a fixed priority order: boolean tokens, integers, decimals,
 * date/times, and finally string as the fallback. Enum detection is a
 * field-level concern and therefore not handled here.
 */
public final class ValueClassifier {

    private static final Set<String> BOOLEAN_TOKENS = Set.of("true", "false", "yes", "no");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^[+-]?\\d+$");
    private static final Pattern DECIMAL_PATTERN =
            Pattern.compile("^[+-]?(\\d+\\.\\d*|\\.\\d+|\\d+)([eE][+-]?\\d+)?$");

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    public FieldType classify(String rawValue) {
        String value = rawValue.trim();
        if (BOOLEAN_TOKENS.contains(value.toLowerCase(Locale.ROOT))) {
            return FieldType.BOOLEAN;
        }
        if (INTEGER_PATTERN.matcher(value).matches() && fitsInLong(value)) {
            return FieldType.INTEGER;
        }
        if (DECIMAL_PATTERN.matcher(value).matches() && isParseableDecimal(value)) {
            return FieldType.DECIMAL;
        }
        if (isDateTime(value)) {
            return FieldType.DATETIME;
        }
        return FieldType.STRING;
    }

    private static boolean fitsInLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isParseableDecimal(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDateTime(String value) {
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                formatter.parse(value);
                return true;
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            // try next
        }
        try {
            ZonedDateTime.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            // try next
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
