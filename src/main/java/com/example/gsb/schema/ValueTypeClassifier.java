package com.example.gsb.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.regex.Pattern;

/**
 * 单个字符串样本值的类型分类器。按 BOOLEAN -> INTEGER -> DECIMAL -> DATETIME -> STRING 判定，
 * 空白字符串视为空值（由调用方处理）。
 */
public final class ValueTypeClassifier {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("[+-]?\\d{1,39}");
    private static final Pattern DECIMAL_PATTERN =
            Pattern.compile("[+-]?(?:\\d+\\.\\d*|\\.\\d+|\\d+)(?:[eE][+-]?\\d+)?");
    private static final Pattern DECIMAL_REQUIRES_FRACTION =
            Pattern.compile("\\.\\d|[eE]");

    /** 本地日期时间格式（无偏移量），按特异程度从高到低排列。 */
    private static final DateTimeFormatter[] LOCAL_DATE_TIME_FORMATTERS = new DateTimeFormatter[] {
            new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE)
                    .appendLiteral(' ')
                    .appendPattern("HH:mm")
                    .optionalStart().appendLiteral(':').appendPattern("ss").optionalEnd()
                    .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
                    .toFormatter(),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    };

    private ValueTypeClassifier() {
    }

    public static FieldType classify(String raw) {
        String value = raw.trim();
        if (isBoolean(value)) {
            return FieldType.BOOLEAN;
        }
        if (isInteger(value)) {
            return FieldType.INTEGER;
        }
        if (isDecimal(value)) {
            return FieldType.DECIMAL;
        }
        if (isDateTime(value)) {
            return FieldType.DATETIME;
        }
        return FieldType.STRING;
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private static boolean isInteger(String value) {
        if (!INTEGER_PATTERN.matcher(value).matches()) {
            return false;
        }
        try {
            new BigInteger(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDecimal(String value) {
        if (!DECIMAL_PATTERN.matcher(value).matches()
                || !DECIMAL_REQUIRES_FRACTION.matcher(value).find()) {
            return false;
        }
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDateTime(String value) {
        return tryDateTime(value) != null;
    }

    static TemporalAccessor tryDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            // try next
        }
        try {
            return ZonedDateTime.parse(value);
        } catch (Exception ignored) {
            // try next
        }
        boolean hasTimePart = value.indexOf('T') > 0 || value.matches(".*\\d \\d.*");
        if (hasTimePart) {
            for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATTERS) {
                try {
                    return LocalDateTime.parse(value, formatter);
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }
}
