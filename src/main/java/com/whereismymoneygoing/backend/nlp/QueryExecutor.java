package com.whereismymoneygoing.backend.nlp;

import static com.whereismymoneygoing.backend.ai.FinanceFactsBuilder.rs;

import com.whereismymoneygoing.backend.ai.FinanceFactsBuilder;
import com.whereismymoneygoing.backend.analytics.AnalyticsService;
import com.whereismymoneygoing.backend.model.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Calculates the exact answer to a parsed question. All numbers come from here, never from the model. */
@Component
public class QueryExecutor {

    private final AnalyticsService analytics;

    public QueryExecutor(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    /** Returns null for intents that have no data answer (advice, unknown). */
    public Answer execute(ParsedQuery q, List<Transaction> all, LocalDate stmtFrom, LocalDate stmtTo) {
        if (!q.intent().hasDataAnswer()) return null;
        Language l = q.language();
        Period p = q.hasPeriod() ? q.periods().get(0) : Period.all(stmtFrom, stmtTo);
        String label = Messages.periodLabel(l, p, stmtFrom, stmtTo);
        String inP = Messages.inPeriod(l, p, label);
        List<Transaction> in = within(all, p);

        if (q.intent() == Intent.SUBSCRIPTIONS) return subscriptions(l, all);
        if (q.intent() == Intent.COMPARE) return compare(q, all, stmtFrom, stmtTo);

        boolean merchantOrCategory = q.intent() == Intent.MERCHANT_SPEND || q.intent() == Intent.CATEGORY_SPEND;
        boolean overlapsStatement = !p.to().isBefore(stmtFrom) && !p.from().isAfter(stmtTo);
        if (in.isEmpty() && !(merchantOrCategory && overlapsStatement)) {
            String text = Messages.t(l, "nodata", "inP", inP,
                    "from", Messages.date(l, stmtFrom), "to", Messages.date(l, stmtTo));
            return new Answer(text, "No transactions " + Messages.periodLabel(Language.ENGLISH, p, stmtFrom, stmtTo));
        }
        return switch (q.intent()) {
            case MERCHANT_SPEND -> merchant(q, all, in, p, label, inP, stmtFrom, stmtTo);
            case CATEGORY_SPEND -> category(q, in, label, inP);
            case TOTAL_SPEND -> total(l, in, label, inP);
            case INCOME -> income(l, in, label, inP);
            case SAVINGS -> savings(l, in, label, inP);
            case TOP_MERCHANTS -> topMerchants(l, in, label, inP);
            case BIGGEST_EXPENSE -> biggest(l, in, label, inP);
            default -> null;
        };
    }

    // ---------- intents ----------

    private Answer merchant(ParsedQuery q, List<Transaction> all, List<Transaction> in, Period p, String label,
                            String inP, LocalDate stmtFrom, LocalDate stmtTo) {
        Language l = q.language();
        String m = q.merchant();
        List<Transaction> mine = in.stream().filter(t -> t.isDebit() && m.equals(t.merchant())).toList();
        BigDecimal sum = sum(mine);
        StringBuilder data = new StringBuilder("MERCHANT: ").append(m).append("\nPERIOD: ")
                .append(Messages.periodLabel(Language.ENGLISH, p, stmtFrom, stmtTo)).append('\n');
        data.append("Spend on ").append(m).append(" in this period: ").append(rs(sum)).append(" over ")
                .append(mine.size()).append(" payments\n");

        // month-by-month history for this merchant, so the model can see the trend
        Map<YearMonth, BigDecimal> history = new TreeMap<>();
        for (Transaction t : all) {
            if (t.isDebit() && m.equals(t.merchant())) history.merge(YearMonth.from(t.date()), t.amount(), BigDecimal::add);
        }
        if (!history.isEmpty()) {
            data.append("Month by month spend on ").append(m).append(": ");
            List<String> parts = new ArrayList<>();
            history.forEach((ym, v) -> parts.add(Messages.monthYear(Language.ENGLISH, ym) + " " + rs(v)));
            data.append(String.join(", ", parts)).append('\n');
        }

        if (mine.isEmpty()) {
            return new Answer(Messages.t(l, "merchant.none", "m", m, "inP", inP), data.toString());
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(mine.size()), 0, RoundingMode.HALF_UP);
        StringBuilder text = new StringBuilder(Messages.t(l, "merchant.spend", "m", m, "amt", rs(sum),
                "n", String.valueOf(mine.size()), "pay", Messages.payments(l, mine.size()), "avg", rs(avg), "inP", inP));

        Period prev = p.previous();
        boolean monthly = p.kind() == Period.Kind.MONTH || p.kind() == Period.Kind.LAST_N_MONTHS;
        if (monthly && prev != null && covered(prev, stmtFrom, stmtTo)) {
            BigDecimal before = sum(within(all, prev).stream().filter(t -> t.isDebit() && m.equals(t.merchant())).toList());
            String prevLabel = Messages.periodLabel(l, prev, stmtFrom, stmtTo);
            data.append("Same merchant in the previous period (").append(Messages.periodLabel(Language.ENGLISH, prev, stmtFrom, stmtTo))
                    .append("): ").append(rs(before)).append('\n');
            if (before.signum() == 0) {
                text.append(Messages.t(l, "change.zero", "prev", prevLabel));
            } else {
                BigDecimal pct = percentChange(sum, before);
                String key = pct.signum() == 0 ? "change.same" : pct.signum() > 0 ? "change.up" : "change.down";
                text.append(Messages.t(l, key, "pct", pct.abs().toPlainString(),
                        "prev", prevLabel, "prevAmt", rs(before)));
            }
        }
        return new Answer(text.toString(), data.toString());
    }

    private Answer category(ParsedQuery q, List<Transaction> in, String label, String inP) {
        Language l = q.language();
        String cat = Messages.category(l, q.kind());
        List<Transaction> mine = in.stream().filter(t -> t.isDebit() && t.kind() == q.kind()).toList();
        String data = "CATEGORY: " + q.kind().label() + "\nPERIOD: " + label + "\nSpend: " + rs(sum(mine))
                + " over " + mine.size() + " payments\n";
        if (mine.isEmpty()) return new Answer(Messages.t(l, "category.none", "cat", cat, "inP", inP), data);
        return new Answer(Messages.t(l, "category.spend", "cat", cat, "amt", rs(sum(mine)), "inP", inP,
                "n", String.valueOf(mine.size()), "pay", Messages.payments(l, mine.size())), data);
    }

    private Answer total(Language l, List<Transaction> in, String label, String inP) {
        List<Transaction> debits = in.stream().filter(Transaction::isDebit).toList();
        if (debits.isEmpty()) {
            return new Answer(Messages.t(l, "nodata", "inP", inP, "from", "-", "to", "-"), "No spending in " + label);
        }
        var top = analytics.merchantSummary(in, 3);
        BigDecimal sum = sum(debits);
        StringBuilder data = new StringBuilder("PERIOD: ").append(label).append("\nTotal spend: ").append(rs(sum))
                .append(" over ").append(debits.size()).append(" payments\nTop merchants: ");
        List<String> parts = new ArrayList<>();
        top.forEach(m -> parts.add(displayName(Language.ENGLISH, m.merchant()) + " " + rs(m.spend())));
        data.append(String.join(", ", parts)).append('\n');
        var best = top.get(0);
        return new Answer(Messages.t(l, "total.spend", "inP", inP, "amt", rs(sum), "n", String.valueOf(debits.size()),
                "pay", Messages.payments(l, debits.size()), "top", displayName(l, best.merchant()),
                "topAmt", rs(best.spend())), data.toString());
    }

    private Answer income(Language l, List<Transaction> in, String label, String inP) {
        List<Transaction> credits = in.stream().filter(t -> !t.isDebit()).toList();
        String data = "PERIOD: " + label + "\nMoney received: " + rs(sum(credits)) + " over " + credits.size() + " credits\n";
        if (credits.isEmpty()) return new Answer(Messages.t(l, "income.none", "inP", inP), data);
        return new Answer(Messages.t(l, "income", "amt", rs(sum(credits)), "n", String.valueOf(credits.size()), "inP", inP), data);
    }

    private Answer savings(Language l, List<Transaction> in, String label, String inP) {
        BigDecimal inc = sum(in.stream().filter(t -> !t.isDebit()).toList());
        BigDecimal out = sum(in.stream().filter(Transaction::isDebit).toList());
        BigDecimal net = inc.subtract(out);
        String data = "PERIOD: " + label + "\nMoney in: " + rs(inc) + "\nMoney out: " + rs(out) + "\nNet: " + rs(net) + "\n";
        String text = Messages.t(l, net.signum() >= 0 ? "savings.pos" : "savings.neg", "inP", Messages.cap(inP),
                "inc", rs(inc), "out", rs(out), "amt", rs(net.abs()));
        return new Answer(text, data);
    }

    private Answer topMerchants(Language l, List<Transaction> in, String label, String inP) {
        var top = analytics.merchantSummary(in, 5);
        BigDecimal totalOut = sum(in.stream().filter(Transaction::isDebit).toList());
        if (top.isEmpty()) return new Answer(Messages.t(l, "nodata", "inP", inP, "from", "-", "to", "-"), "No spending");
        StringBuilder text = new StringBuilder(Messages.cap(Messages.t(l, "top.header", "inP", inP)));
        StringBuilder data = new StringBuilder("PERIOD: ").append(label).append("\nTop merchants by spend:\n");
        int i = 1;
        for (var m : top) {
            String pct = percentOf(m.spend(), totalOut);
            text.append('\n').append(Messages.t(l, "top.line", "i", String.valueOf(i), "m", displayName(l, m.merchant()),
                    "amt", rs(m.spend()), "n", String.valueOf(m.transactions()),
                    "pay", Messages.payments(l, m.transactions()), "pct", pct));
            data.append("- ").append(displayName(Language.ENGLISH, m.merchant())).append(": ").append(rs(m.spend()))
                    .append(" (").append(m.transactions()).append(" payments, ").append(pct).append(" of spending)\n");
            i++;
        }
        return new Answer(text.toString(), data.toString());
    }

    private Answer biggest(Language l, List<Transaction> in, String label, String inP) {
        List<Transaction> top = in.stream().filter(Transaction::isDebit)
                .sorted(Comparator.comparing(Transaction::amount).reversed()).limit(3).toList();
        if (top.isEmpty()) return new Answer(Messages.t(l, "nodata", "inP", inP, "from", "-", "to", "-"), "No spending");
        StringBuilder text = new StringBuilder(Messages.cap(Messages.t(l, "biggest.header", "inP", inP)));
        StringBuilder data = new StringBuilder("PERIOD: ").append(label).append("\nLargest payments:\n");
        int i = 1;
        for (Transaction t : top) {
            String payee = payee(l, t);
            text.append('\n').append(Messages.t(l, "biggest.line", "i", String.valueOf(i), "date", Messages.date(l, t.date()),
                    "amt", rs(t.amount()), "m", payee));
            data.append("- ").append(Messages.date(Language.ENGLISH, t.date())).append(": ").append(rs(t.amount()))
                    .append(" to ").append(payee(Language.ENGLISH, t)).append('\n');
            i++;
        }
        return new Answer(text.toString(), data.toString());
    }

    private Answer subscriptions(Language l, List<Transaction> all) {
        var subs = analytics.recurringSubscriptions(all);
        if (subs.isEmpty()) return new Answer(Messages.t(l, "subs.none"), "No recurring payments detected");
        StringBuilder text = new StringBuilder(Messages.t(l, "subs.header"));
        StringBuilder data = new StringBuilder("Recurring payments:\n");
        BigDecimal yearly = BigDecimal.ZERO;
        for (var s : subs.stream().limit(5).toList()) {
            text.append('\n').append(Messages.t(l, "subs.line", "m", FinanceFactsBuilder.clean(s.merchant()),
                    "amt", rs(s.amount()), "cad", Messages.cadence(l, s.cadence()), "yr", rs(s.annualCost())));
            data.append("- ").append(FinanceFactsBuilder.clean(s.merchant())).append(": ").append(rs(s.amount()))
                    .append(' ').append(Messages.cadence(Language.ENGLISH, s.cadence())).append(", about ")
                    .append(rs(s.annualCost())).append(" a year\n");
            yearly = yearly.add(s.annualCost());
        }
        data.append("Total of these per year: ").append(rs(yearly)).append('\n');
        return new Answer(text.toString(), data.toString());
    }

    private Answer compare(ParsedQuery q, List<Transaction> all, LocalDate stmtFrom, LocalDate stmtTo) {
        Language l = q.language();
        Period newer;
        Period older;
        List<Period> ps = q.periods();
        if (ps.size() >= 2) {
            Period a = ps.get(0), b = ps.get(1);
            newer = a.from().isAfter(b.from()) ? a : b;
            older = newer == a ? b : a;
        } else {
            newer = ps.isEmpty() ? Period.month(YearMonth.from(stmtTo)) : ps.get(0);
            older = newer.previous();
        }
        String newLabel = Messages.periodLabel(l, newer, stmtFrom, stmtTo);
        if (older == null || !covered(older, stmtFrom, stmtTo)) {
            return new Answer(Messages.t(l, "compare.nopast", "new", newLabel), "No earlier data to compare with " + newLabel);
        }
        String oldLabel = Messages.periodLabel(l, older, stmtFrom, stmtTo);
        List<Transaction> newTx = within(all, newer).stream().filter(Transaction::isDebit).toList();
        List<Transaction> oldTx = within(all, older).stream().filter(Transaction::isDebit).toList();
        BigDecimal newSum = sum(newTx), oldSum = sum(oldTx);

        StringBuilder data = new StringBuilder("COMPARISON: ").append(Messages.periodLabel(Language.ENGLISH, newer, stmtFrom, stmtTo))
                .append(" vs ").append(Messages.periodLabel(Language.ENGLISH, older, stmtFrom, stmtTo)).append('\n')
                .append("Spend (newer): ").append(rs(newSum)).append("\nSpend (older): ").append(rs(oldSum)).append('\n');
        StringBuilder text = new StringBuilder();
        if (oldSum.signum() == 0) {
            text.append(Messages.t(l, "compare.nobase", "new", newLabel, "newAmt", rs(newSum), "old", oldLabel));
        } else {
            BigDecimal pct = percentChange(newSum, oldSum);
            text.append(Messages.t(l, "compare.main", "new", newLabel, "newAmt", rs(newSum), "old", oldLabel,
                    "oldAmt", rs(oldSum), "pct", pct.abs().toPlainString(),
                    "dir", Messages.direction(l, pct.signum() >= 0)));
            data.append("Change: ").append(pct.toPlainString()).append("%\n");
        }

        Map<String, BigDecimal> delta = new LinkedHashMap<>();
        for (Transaction t : newTx) delta.merge(name(t), t.amount(), BigDecimal::add);
        for (Transaction t : oldTx) delta.merge(name(t), t.amount().negate(), BigDecimal::add);
        List<Map.Entry<String, BigDecimal>> changes = delta.entrySet().stream()
                .filter(e -> e.getValue().signum() != 0)
                .sorted((a, b) -> b.getValue().abs().compareTo(a.getValue().abs())).limit(3).toList();
        if (!changes.isEmpty()) {
            text.append('\n').append(Messages.t(l, "compare.header"));
            data.append("Biggest changes by merchant:\n");
            for (var e : changes) {
                String shown = e.getKey().equals(AnalyticsService.NEEDS_REVIEW) ? Messages.unrecognised(l) : e.getKey();
                text.append("\n- ").append(shown).append(": ").append(signed(e.getValue()));
                data.append("- ").append(e.getKey().equals(AnalyticsService.NEEDS_REVIEW)
                        ? Messages.unrecognised(Language.ENGLISH) : e.getKey()).append(": ").append(signed(e.getValue())).append('\n');
            }
        }
        return new Answer(text.toString(), data.toString());
    }

    // ---------- helpers ----------

    private static List<Transaction> within(List<Transaction> all, Period p) {
        return all.stream().filter(t -> p.contains(t.date())).toList();
    }

    /** The statement actually contains the period (not just touches its edge by a day). */
    private static boolean covered(Period p, LocalDate stmtFrom, LocalDate stmtTo) {
        return !p.from().isBefore(stmtFrom.withDayOfMonth(1)) && !p.from().isAfter(stmtTo);
    }

    private static BigDecimal sum(List<Transaction> txns) {
        return txns.stream().map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal percentChange(BigDecimal now, BigDecimal before) {
        return now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP);
    }

    private static String percentOf(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) return "0%";
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String signed(BigDecimal v) {
        return (v.signum() >= 0 ? "+" : "-") + rs(v.abs());
    }

    private static String name(Transaction t) {
        return t.isUnknownMerchant() ? AnalyticsService.NEEDS_REVIEW : FinanceFactsBuilder.clean(t.merchant());
    }

    private static String displayName(Language l, String merchant) {
        return AnalyticsService.NEEDS_REVIEW.equals(merchant) ? Messages.unrecognised(l) : FinanceFactsBuilder.clean(merchant);
    }

    private static String payee(Language l, Transaction t) {
        if (!t.isUnknownMerchant()) return FinanceFactsBuilder.clean(t.merchant());
        String n = FinanceFactsBuilder.clean(t.narration());
        return Messages.unrecognised(l) + " (" + (n.length() <= 30 ? n : n.substring(0, 30) + "...") + ")";
    }
}
