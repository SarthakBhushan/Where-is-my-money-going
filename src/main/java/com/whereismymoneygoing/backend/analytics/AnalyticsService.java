package com.whereismymoneygoing.backend.analytics;

import com.whereismymoneygoing.backend.model.Transaction;
import com.whereismymoneygoing.backend.model.TransactionKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    public static final String NEEDS_REVIEW = "🟡 Needs Review";

    private static final int MIN_GAP_DAYS = 21;
    private static final int MAX_GAP_DAYS = 45;
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.15");

    public record Summary(BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal net,
                          int count, int monthCount, BigDecimal avgMonthlySpend, BigDecimal savingsRate) {}

    public record MonthlyFlow(String month, String label, BigDecimal income,
                              BigDecimal expense, BigDecimal savings) {}

    public record BalancePoint(String date, BigDecimal balance) {}

    public record MerchantSpend(String merchant, BigDecimal spend, int transactions, BigDecimal average) {}

    public record KindSpend(String kind, BigDecimal amount) {}

    public record Subscription(String merchant, BigDecimal amount, String cadence, int occurrences,
                               BigDecimal monthlyCost, BigDecimal annualCost) {}

    public record Coverage(int recognized, int unknown) {
        public int total() { return recognized + unknown; }
        public double percent() { return total() == 0 ? 0.0 : recognized * 100.0 / total(); }
    }

    public record UnknownSpend(String narration, int transactions, BigDecimal totalSpend) {}

    public Summary summary(List<Transaction> txns) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        Set<YearMonth> months = new HashSet<>();
        for (Transaction t : txns) {
            if (t.isDebit()) debit = debit.add(t.amount());
            else credit = credit.add(t.amount());
            months.add(YearMonth.from(t.date()));
        }
        int monthCount = months.size();
        BigDecimal avg = monthCount == 0 ? BigDecimal.ZERO
                : debit.divide(BigDecimal.valueOf(monthCount), 2, RoundingMode.HALF_UP);
        BigDecimal rate = credit.signum() > 0
                ? credit.subtract(debit).multiply(BigDecimal.valueOf(100)).divide(credit, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new Summary(debit, credit, credit.subtract(debit), txns.size(), monthCount, avg, rate);
    }

    public List<MonthlyFlow> monthlyCashFlow(List<Transaction> txns) {
        Map<YearMonth, BigDecimal> income = new TreeMap<>();
        Map<YearMonth, BigDecimal> expense = new TreeMap<>();
        for (Transaction t : txns) {
            (t.isDebit() ? expense : income).merge(YearMonth.from(t.date()), t.amount(), BigDecimal::add);
        }
        Set<YearMonth> months = new TreeSet<>(income.keySet());
        months.addAll(expense.keySet());

        boolean showYear = months.stream().map(YearMonth::getYear).distinct().count() > 1;
        DateTimeFormatter label = DateTimeFormatter.ofPattern(showYear ? "MMM yy" : "MMM", Locale.ENGLISH);

        List<MonthlyFlow> rows = new ArrayList<>();
        for (YearMonth m : months) {
            BigDecimal in = income.getOrDefault(m, BigDecimal.ZERO);
            BigDecimal out = expense.getOrDefault(m, BigDecimal.ZERO);
            rows.add(new MonthlyFlow(m.toString(), m.format(label), in, out, in.subtract(out)));
        }
        return rows;
    }

    /** Uses recorded balances when present; otherwise a cumulative net. Same-day order is preserved. */
    public List<BalancePoint> balanceTrajectory(List<Transaction> txns) {
        List<Transaction> ordered = txns.stream().sorted(Comparator.comparing(Transaction::date)).toList();
        boolean hasBalances = ordered.stream().anyMatch(t -> t.balance() != null);

        List<BalancePoint> points = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Transaction t : ordered) {
            BigDecimal delta = t.isDebit() ? t.amount().negate() : t.amount();
            running = (hasBalances && t.balance() != null) ? t.balance() : running.add(delta);
            points.add(new BalancePoint(t.date().toString(), running));
        }
        return points;
    }

    /** Debit spend per merchant, highest first. limit <= 0 means all. Unknown merchants share one bucket. */
    public List<MerchantSpend> merchantSummary(List<Transaction> txns, int limit) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (!t.isDebit()) continue;
            String name = t.isUnknownMerchant() ? NEEDS_REVIEW : t.merchant();
            totals.merge(name, t.amount(), BigDecimal::add);
            counts.merge(name, 1, Integer::sum);
        }
        List<MerchantSpend> rows = new ArrayList<>();
        totals.forEach((name, total) -> {
            int n = counts.get(name);
            rows.add(new MerchantSpend(name, total, n,
                    total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP)));
        });
        rows.sort(Comparator.comparing(MerchantSpend::spend).reversed());
        return limit > 0 && rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    public List<KindSpend> spendByKind(List<Transaction> txns) {
        Map<TransactionKind, BigDecimal> totals = new EnumMap<>(TransactionKind.class);
        for (Transaction t : txns) {
            if (t.isDebit()) totals.merge(t.kind(), t.amount(), BigDecimal::add);
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.<TransactionKind, BigDecimal>comparingByValue().reversed())
                .map(e -> new KindSpend(e.getKey().label(), e.getValue()))
                .toList();
    }

    public List<Subscription> recurringSubscriptions(List<Transaction> txns) {
        Map<String, List<Transaction>> grouped = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (!t.isDebit()) continue;
            String identity = t.isUnknownMerchant() ? t.narration() : t.merchant();
            grouped.computeIfAbsent(identity, k -> new ArrayList<>()).add(t);
        }

        List<Subscription> result = new ArrayList<>();
        grouped.forEach((merchant, list) -> {
            if (list.size() < 2) return;
            List<Transaction> ordered = list.stream().sorted(Comparator.comparing(Transaction::date)).toList();

            List<Long> gaps = new ArrayList<>();
            for (int i = 0; i < ordered.size() - 1; i++) {
                gaps.add(java.time.temporal.ChronoUnit.DAYS.between(ordered.get(i).date(), ordered.get(i + 1).date()));
            }
            if (gaps.stream().min(Long::compare).orElseThrow() < MIN_GAP_DAYS
                    || gaps.stream().max(Long::compare).orElseThrow() > MAX_GAP_DAYS) return;

            List<BigDecimal> amounts = ordered.stream().map(Transaction::amount).toList();
            if (!isStable(amounts, typicalAmount(amounts))) return;

            BigDecimal amount = amounts.stream().sorted().toList().get(amounts.size() / 2);
            long interval = gaps.stream().sorted().toList().get(gaps.size() / 2);
            BigDecimal annual = amount.multiply(BigDecimal.valueOf(365))
                    .divide(BigDecimal.valueOf(Math.max(interval, 1)), 0, RoundingMode.HALF_UP);
            String cadence = (interval >= 25 && interval <= 40) ? "Monthly" : "Every " + interval + "d";
            result.add(new Subscription(merchant, amount, cadence, ordered.size(), amount, annual));
        });
        result.sort(Comparator.comparing(Subscription::annualCost).reversed());
        return result;
    }

    private static BigDecimal typicalAmount(List<BigDecimal> amounts) {
        Map<BigDecimal, Integer> buckets = new LinkedHashMap<>();
        for (BigDecimal a : amounts) {
            BigDecimal bucket = a.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN);
            buckets.merge(bucket, 1, Integer::sum);
        }
        BigDecimal best = null;
        int bestCount = -1;
        for (Map.Entry<BigDecimal, Integer> e : buckets.entrySet()) {
            if (e.getValue() > bestCount) { best = e.getKey(); bestCount = e.getValue(); }
        }
        return best;
    }

    private static boolean isStable(List<BigDecimal> amounts, BigDecimal typical) {
        if (typical.signum() == 0) return false;
        BigDecimal tolerance = AMOUNT_TOLERANCE.multiply(typical);
        long matched = amounts.stream().filter(a -> a.subtract(typical).abs().compareTo(tolerance) <= 0).count();
        return matched * 5 >= amounts.size() * 3L;   // matched / n >= 0.6
    }

    public Coverage coverage(List<Transaction> txns) {
        int unknown = (int) txns.stream().filter(Transaction::isUnknownMerchant).count();
        return new Coverage(txns.size() - unknown, unknown);
    }

    public List<UnknownSpend> unknownSpending(List<Transaction> txns) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (!t.isDebit() || !Transaction.UNKNOWN_MERCHANT.equals(t.merchant())) continue;
            totals.merge(t.narration(), t.amount(), BigDecimal::add);
            counts.merge(t.narration(), 1, Integer::sum);
        }
        return totals.entrySet().stream()
                .map(e -> new UnknownSpend(e.getKey(), counts.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(UnknownSpend::totalSpend).reversed())
                .toList();
    }
}