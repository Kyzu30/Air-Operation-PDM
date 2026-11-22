package com.group7.airport.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Input checks done BEFORE anything is sent to the database:
 * required fields, lengths, formats, numeric ranges and date order.
 * Every failure throws {@link ValidationException} with a message for the user.
 */
public final class Validators {

    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Validators() { }

    /* Thrown when user input is wrong; shown as a warning, not as an error. */
    public static class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static String required(String value, String field, int maxLen) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new ValidationException(field + " is required.");
        }
        if (v.length() > maxLen) {
            throw new ValidationException(field + " must be at most " + maxLen + " characters.");
        }
        return v;
    }

    /** Empty -> null. */
    public static String optional(String value, String field, int maxLen) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (v.length() > maxLen) {
            throw new ValidationException(field + " must be at most " + maxLen + " characters.");
        }
        return v;
    }

    public static String pattern(String value, String field, String regex, String example) {
        if (value != null && !value.matches(regex)) {
            throw new ValidationException(field + " has the wrong format. " + example);
        }
        return value;
    }

    public static LocalDate date(String value, String field) {
        try {
            return LocalDate.parse(required(value, field, 10));
        } catch (DateTimeParseException e) {
            throw new ValidationException(field + " must be yyyy-MM-dd, e.g. 2026-11-14.");
        }
    }

    public static LocalDateTime dateTime(String value, String field) {
        try {
            return LocalDateTime.parse(required(value, field, 16), DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new ValidationException(field + " must be yyyy-MM-dd HH:mm, e.g. 2026-11-14 08:30.");
        }
    }

    /* minExclusive < value <= maxInclusive */
    public static BigDecimal decimalInRange(String value, String field, double minExclusive, double maxInclusive) {
        BigDecimal d;
        try {
            d = new BigDecimal(required(value, field, 12));
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " must be a number, e.g. 18.5");
        }
        if (d.doubleValue() <= minExclusive || d.doubleValue() > maxInclusive) {
            throw new ValidationException(field + " must be greater than " + fmt(minExclusive)
                    + " and at most " + fmt(maxInclusive) + ".");
        }
        return d;
    }

    public static int intInRange(String value, String field, int min, int max) {
        int n;
        try {
            n = Integer.parseInt(required(value, field, 9));
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " must be a whole number.");
        }
        if (n < min || n > max) {
            throw new ValidationException(field + " must be between " + min + " and " + max + ".");
        }
        return n;
    }

    public static void dateOrder(LocalDateTime first, LocalDateTime later, String message) {
        if (!later.isAfter(first)) {
            throw new ValidationException(message);
        }
    }

    /* Dates and times are sent as text, like FlightDAO does, so JDBC never shifts them by a time zone. */
    public static String sql(LocalDate d) {
        return d == null ? null : d.toString();
    }

    public static String sql(LocalDateTime t) {
        return t == null ? null : t.format(SQL_DATE_TIME);
    }

    /** Value read from the database -> text for a form field. */
    public static String display(Object dbValue) {
        if (dbValue == null) {
            return "";
        }
        if (dbValue instanceof LocalDateTime t) {
            return t.format(DATE_TIME);
        }
        if (dbValue instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().format(DATE_TIME);
        }
        return dbValue.toString();
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
