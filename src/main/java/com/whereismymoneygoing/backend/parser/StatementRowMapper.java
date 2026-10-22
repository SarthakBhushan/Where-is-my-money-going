package com.whereismymoneygoing.backend.parser;

import com.whereismymoneygoing.backend.model.Transaction;
import com.whereismymoneygoing.backend.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Shared cell, date and amount parsing. Rows use canonical keys from {@link ColumnAliases}. */
final class StatementRowMapper {

    private static final Pattern DR_CR = Pattern.compile("\\s*(?:dr|cr|debit|credit)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            fmt("d/M/uuuu"), fmt("d/M/uu"), fmt("d-M-uuuu"), fmt("d-M-uu"),
            fmt("d.M.uuuu"), fmt("d.M.uu"), fmt("d MMM uuuu"), fmt("d-MMM-uuuu"),
            fmt("d MMM uu"), fmt("d-MMM-uu"), fmt("uuuu-M-d"));

    private StatementRowMapper() {}

    private static DateTimeFormatter fmt(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern(pattern).toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    /** Drops control characters and non-ASCII spaces, then trims. */
    static String clean(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (ch == ' ' || (!Character.isISOControl(ch) && !Character.isSpaceChar(ch))) sb.append(ch);
        }
        return sb.toString().strip();
    }

    static LocalDate parseDate(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    static BigDecimal parseAmount(String value) {
        String cleaned = clean(value).replace("₹", "").strip();
        cleaned = DR_CR.matcher(cleaned).replaceAll("").replace(",", "").strip();
        if (cleaned.isEmpty()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean nonZero(BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    /** Returns null for rows that are not transactions (headers, banners, totals, blanks). */
    static Transaction toTransaction(Map<String, String> row) {
        String dateText = row.getOrDefault("post_date", "");
        if (dateText.isEmpty()) dateText = row.getOrDefault("value_date", "");
        String narration = row.getOrDefault("details", "");
        if (dateText.isEmpty() || narration.isEmpty()) return null;

        LocalDate date = parseDate(dateText);
        if (date == null) return null;

        BigDecimal debit = parseAmount(row.getOrDefault("debit", ""));
        BigDecimal credit = parseAmount(row.getOrDefault("credit", ""));

        BigDecimal amount;
        TransactionType type;
        if (nonZero(debit)) {
            amount = debit;
            type = TransactionType.DEBIT;
        } else if (nonZero(credit)) {
            amount = credit;
            type = TransactionType.CREDIT;
        } else {
            return null;
        }

        String reference = row.getOrDefault("ref", "");
        return Transaction.parsed(date, narration, amount, type,
                parseAmount(row.getOrDefault("balance", "")),
                reference.isEmpty() ? null : reference);
    }
}