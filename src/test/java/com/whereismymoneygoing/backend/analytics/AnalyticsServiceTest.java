package com.whereismymoneygoing.backend.analytics;

import static org.junit.jupiter.api.Assertions.*;

import com.whereismymoneygoing.backend.model.Transaction;
import com.whereismymoneygoing.backend.model.TransactionKind;
import com.whereismymoneygoing.backend.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    private final AnalyticsService analytics = new AnalyticsService();

    private static Transaction t(String date, String merchant, String amt, TransactionType type) {
        return Transaction.parsed(LocalDate.parse(date), "N " + merchant, new BigDecimal(amt), type,
                null, null).enriched(merchant, TransactionKind.UNKNOWN);
    }

    @Test
    void summaryAddsDebitsAndCreditsSeparately() {
        List<Transaction> txns = List.of(
                t("2026-01-01", "Employer", "50000", TransactionType.CREDIT),
                t("2026-01-02", "Zomato", "400", TransactionType.DEBIT),
                t("2026-01-03", "Zomato", "600", TransactionType.DEBIT));
        var s = analytics.summary(txns);
        assertEquals(0, new BigDecimal("1000").compareTo(s.totalDebit()));
        assertEquals(0, new BigDecimal("50000").compareTo(s.totalCredit()));
        assertEquals(0, new BigDecimal("49000").compareTo(s.net()));
    }

    @Test
    void topMerchantsAreRankedBySpendAndCountTransactions() {
        List<Transaction> txns = List.of(
                t("2026-01-02", "Zomato", "400", TransactionType.DEBIT),
                t("2026-01-03", "Zomato", "600", TransactionType.DEBIT),
                t("2026-01-04", "Swiggy", "300", TransactionType.DEBIT));
        var top = analytics.merchantSummary(txns, 5);
        assertEquals("Zomato", top.get(0).merchant());
        assertEquals(2, top.get(0).transactions());
    }

    @Test
    void coverageIsRecognisedShareOfSpending() {
        List<Transaction> txns = List.of(
                t("2026-01-02", "Zomato", "400", TransactionType.DEBIT),
                t("2026-01-03", "Unknown", "100", TransactionType.DEBIT));
        var c = analytics.coverage(txns);
        assertEquals(2, c.total());
        assertEquals(50.0, c.percent(), 0.001);
    }

    @Test
    void monthlySameAmountDebitsAreDetectedAsASubscription() {
        List<Transaction> txns = new ArrayList<>();
        for (int m = 1; m <= 4; m++) {
            txns.add(t("2026-0" + m + "-05", "Netflix", "649", TransactionType.DEBIT));
        }
        var subs = analytics.recurringSubscriptions(txns);
        assertEquals(1, subs.size());
        assertEquals("Netflix", subs.get(0).merchant());
        assertEquals(4, subs.get(0).occurrences());
    }

    @Test
    void emptyInputDoesNotCrash() {
        assertEquals(0, analytics.coverage(List.of()).total());
        assertTrue(analytics.merchantSummary(List.of(), 5).isEmpty());
    }
}