package com.whereismymoneygoing.backend.classifier;

import org.springframework.stereotype.Component;
import com.whereismymoneygoing.backend.model.TransactionKind;
import java.util.List;
import java.util.Locale;

/** Faithful port of the original substring rules. See the notes at the end of this message. */
@Component
public class TransactionClassifier {

    private static final List<String> CASH = List.of("ATM", "ATW");
    private static final List<String> INVESTMENT =
            List.of("PPF", "SSY", "ZERODHA", "UPSTOX", "JEWE", "JEWEL", "BILLDKINDIANCLEARING");
    private static final List<String> INSURANCE = List.of("ICICIPRULIFE", "MAX LIFE");
    private static final List<String> TRANSFER = List.of("SELF", "WIFE", "SHUBH RATNA", "EXPENSES");

    public TransactionKind classify(String narration) {
        String text = narration.toUpperCase(Locale.ROOT);
        if (containsAny(text, CASH)) return TransactionKind.CASH_WITHDRAWAL;
        if (containsAny(text, INVESTMENT)) return TransactionKind.INVESTMENT;
        if (containsAny(text, INSURANCE)) return TransactionKind.INSURANCE;
        if (containsAny(text, TRANSFER)) return TransactionKind.TRANSFER;
        return TransactionKind.PURCHASE;
    }

    private static boolean containsAny(String text, List<String> needles) {
        return needles.stream().anyMatch(text::contains);
    }
}