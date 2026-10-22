package com.whereismymoneygoing.backend.ai;

import com.whereismymoneygoing.backend.analytics.AnalyticsService;
import com.whereismymoneygoing.backend.model.ParsedStatement;
import com.whereismymoneygoing.backend.model.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Turns the already-computed analytics into a compact text block for the LLM.
 * All arithmetic (totals, percentages, shares) happens here in Java; the model only explains.
 */
@Component
public class FinanceFactsBuilder {

    private static final int TOP_MERCHANTS = 10;
    private static final int TOP_PAYMENTS = 5;
    private static final int MAX_RECURRING = 8;
    private static final int MAX_MONTHS = 12;
    private static final int MAX_MERCHANT_NAMES = 60;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final AnalyticsService analytics;

    public FinanceFactsBuilder(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    public String build(ParsedStatement statement) {
        List<Transaction> txns = statement.transactions();
        if (txns.isEmpty()) return "The statement contains no transactions.";

        var summary = analytics.summary(txns);
        LocalDate from = txns.stream().map(Transaction::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = txns.stream().map(Transaction::date).max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal totalOut = summary.totalDebit();

        StringBuilder sb = new StringBuilder();
        sb.append("STATEMENT: ").append(clean(statement.bank())).append(", ")
          .append(from.format(DAY)).append(" to ").append(to.format(DAY)).append(", ")
          .append(summary.monthCount()).append(" month(s), ")
          .append(summary.count()).append(" transactions\n");

        sb.append("TOTALS: money in ").append(rs(summary.totalCredit()))
          .append("; money out ").append(rs(totalOut))
          .append("; net ").append(rs(summary.net()))
          .append("; average monthly spend ").append(rs(summary.avgMonthlySpend()))
          .append("; savings rate ")
          .append(summary.totalCredit().signum() > 0 ? summary.savingsRate() + "%" : "not available (no credits)")
          .append('\n');

        sb.append("\nMONTH BY MONTH (money in / money out / net):\n");
        analytics.monthlyCashFlow(txns).stream().limit(MAX_MONTHS).forEach(m ->
                sb.append("- ").append(m.label()).append(": in ").append(rs(m.income()))
                  .append(", out ").append(rs(m.expense()))
                  .append(", net ").append(rs(m.savings())).append('\n'));

        sb.append("\nSPENDING BY CATEGORY (money out):\n");
        analytics.spendByKind(txns).forEach(k ->
                sb.append("- ").append(clean(k.kind())).append(": ").append(rs(k.amount()))
                  .append(" (").append(share(k.amount(), totalOut)).append(" of spending)\n"));

        sb.append("\nTOP MERCHANTS (money out):\n");
        analytics.merchantSummary(txns, TOP_MERCHANTS).forEach(m -> {
            String name = AnalyticsService.NEEDS_REVIEW.equals(m.merchant())
                    ? "Unrecognised merchants" : clean(m.merchant());
            sb.append("- ").append(name).append(": ").append(rs(m.spend()))
              .append(" (").append(m.transactions()).append(" payments, ")
              .append(share(m.spend(), totalOut)).append(" of spending)\n");
        });

        sb.append("\nSPEND ON EACH TOP MERCHANT, MONTH BY MONTH:\n");
        DateTimeFormatter monthLabel = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        analytics.merchantSummary(txns, TOP_MERCHANTS).stream()
                .filter(m -> !AnalyticsService.NEEDS_REVIEW.equals(m.merchant())).forEach(m -> {
            Map<YearMonth, BigDecimal> byMonth = new TreeMap<>();
            for (Transaction t : txns) {
                if (t.isDebit() && m.merchant().equals(t.merchant())) {
                    byMonth.merge(YearMonth.from(t.date()), t.amount(), BigDecimal::add);
                }
            }
            List<String> parts = new ArrayList<>();
            byMonth.forEach((ym, v) -> parts.add(ym.format(monthLabel) + " " + rs(v)));
            sb.append("- ").append(clean(m.merchant())).append(": ").append(String.join(", ", parts)).append('\n');
        });

        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Transaction t : txns) if (!t.isUnknownMerchant()) names.add(clean(t.merchant()));
        sb.append("\nALL MERCHANTS RECOGNISED IN THIS STATEMENT: ")
          .append(String.join(", ", names.stream().limit(MAX_MERCHANT_NAMES).toList())).append('\n');

        var recurring = analytics.recurringSubscriptions(txns);
        sb.append("\nRECURRING PAYMENTS DETECTED:\n");
        if (recurring.isEmpty()) {
            sb.append("- none detected\n");
        } else {
            recurring.stream().limit(MAX_RECURRING).forEach(s ->
                    sb.append("- ").append(clean(s.merchant())).append(": ").append(rs(s.amount()))
                      .append(' ').append(clean(s.cadence()).toLowerCase(Locale.ROOT))
                      .append(", about ").append(rs(s.annualCost())).append(" per year\n"));
        }

        sb.append("\nLARGEST SINGLE PAYMENTS:\n");
        txns.stream().filter(Transaction::isDebit)
                .sorted(Comparator.comparing(Transaction::amount).reversed())
                .limit(TOP_PAYMENTS)
                .forEach(t -> sb.append("- ").append(t.date().format(DAY)).append(": ")
                        .append(rs(t.amount())).append(" to ").append(payee(t)).append('\n'));

        return sb.toString();
    }

    /** Whole rupees with Indian digit grouping (12,34,567), no L/Cr abbreviations that small models misread. */
    public static String rs(BigDecimal amount) {
        if (amount == null) return "n/a";
        String digits = amount.abs().setScale(0, RoundingMode.HALF_UP).toPlainString();
        // Java's NumberFormat cannot do the 2-digit grouping used in India, so group by hand.
        StringBuilder grouped = new StringBuilder();
        int head = digits.length() - 3;
        if (head <= 0) {
            grouped.append(digits);
        } else {
            String lead = digits.substring(0, head);
            for (int i = 0; i < lead.length(); i++) {
                if (i > 0 && (lead.length() - i) % 2 == 0) grouped.append(',');
                grouped.append(lead.charAt(i));
            }
            grouped.append(',').append(digits, head, digits.length());
        }
        return (amount.signum() < 0 ? "-₹" : "₹") + grouped;
    }

    private static String share(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return "0%";
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP) + "%";
    }

    private static String payee(Transaction t) {
        return t.isUnknownMerchant() ? "an unrecognised merchant (" + shorten(clean(t.narration())) + ")"
                                     : clean(t.merchant());
    }

    private static String shorten(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "...";
    }

    /**
     * Statement text is untrusted input. Spring AI renders prompt text as a template where { } are
     * placeholders, so strip those (and backslashes and control characters) before the text reaches a prompt.
     */
    public static String clean(String s) {
        if (s == null) return "";
        return s.replace('{', '(').replace('}', ')').replace('\\', '/')
                .replaceAll("\\p{Cntrl}", " ").strip();
    }

    /** For prompt text we write ourselves from already-clean parts: only the template characters need removing. */
    public static String noBraces(String s) {
        return s == null ? "" : s.replace('{', '(').replace('}', ')').replace('\\', '/');
    }
}
