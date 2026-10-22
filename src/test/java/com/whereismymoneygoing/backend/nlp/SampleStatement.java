package com.whereismymoneygoing.backend.nlp;

import com.whereismymoneygoing.backend.model.ParsedStatement;
import com.whereismymoneygoing.backend.model.Transaction;
import com.whereismymoneygoing.backend.model.TransactionKind;
import com.whereismymoneygoing.backend.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Small fixed statement (Aug to Oct 2026) with numbers that are easy to check by hand. */
public final class SampleStatement {

    private SampleStatement() {}

    /** Latest date in the statement: "this month" means October 2026. */
    public static final LocalDate ANCHOR = LocalDate.of(2026, 10, 22);

    public static List<Transaction> transactions() {
        List<Transaction> l = new ArrayList<>();
        credit(l, "2026-08-01", "80000");
        credit(l, "2026-09-01", "80000");
        credit(l, "2026-10-01", "82000");
        // Zomato: Aug 1,070 / Sep 1,550 / Oct 1,280
        debit(l, "2026-08-05", "450", "Zomato", TransactionKind.PURCHASE);
        debit(l, "2026-08-17", "620", "Zomato", TransactionKind.PURCHASE);
        debit(l, "2026-09-03", "300", "Zomato", TransactionKind.PURCHASE);
        debit(l, "2026-09-14", "710", "Zomato", TransactionKind.PURCHASE);
        debit(l, "2026-09-25", "540", "Zomato", TransactionKind.PURCHASE);
        debit(l, "2026-10-04", "380", "Zomato", TransactionKind.PURCHASE);
        debit(l, "2026-10-18", "900", "Zomato", TransactionKind.PURCHASE);
        debit(l, "2026-08-12", "1200", "Swiggy", TransactionKind.PURCHASE);
        debit(l, "2026-10-12", "800", "Swiggy", TransactionKind.PURCHASE);
        debit(l, "2026-08-05", "649", "Netflix", TransactionKind.PURCHASE);
        debit(l, "2026-09-05", "649", "Netflix", TransactionKind.PURCHASE);
        debit(l, "2026-10-05", "649", "Netflix", TransactionKind.PURCHASE);
        debit(l, "2026-09-15", "5000", "Mutual Funds", TransactionKind.INVESTMENT);
        debit(l, "2026-10-15", "5000", "Mutual Funds", TransactionKind.INVESTMENT);
        debit(l, "2026-09-22", "1900", "Electricity Bill", TransactionKind.PURCHASE);
        debit(l, "2026-10-22", "2100", "Electricity Bill", TransactionKind.PURCHASE);
        debit(l, "2026-10-20", "7500", "Unknown", TransactionKind.UNKNOWN);
        return l;
    }

    public static ParsedStatement statement() {
        return new ParsedStatement("HDFC", "sample.xlsx", transactions());
    }

    private static void credit(List<Transaction> l, String date, String amount) {
        l.add(new Transaction(LocalDate.parse(date), "SALARY", new BigDecimal(amount), TransactionType.CREDIT,
                null, null, "Employer", TransactionKind.TRANSFER));
    }

    private static void debit(List<Transaction> l, String date, String amount, String merchant, TransactionKind kind) {
        l.add(new Transaction(LocalDate.parse(date), merchant.toUpperCase() + " PAYMENT", new BigDecimal(amount),
                TransactionType.DEBIT, null, null, merchant, kind));
    }
}
