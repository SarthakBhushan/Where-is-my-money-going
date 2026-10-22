package com.whereismymoneygoing.backend.model;


import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(
        LocalDate date,
        String narration,
        BigDecimal amount,
        TransactionType type,
        BigDecimal balance,
        String reference,
        String merchant,
        TransactionKind kind) implements Serializable {

    public static final String UNKNOWN_MERCHANT = "Unknown";
    public static Transaction parsed(
            LocalDate date,
            String narration,
            BigDecimal amount,
            TransactionType type,
            BigDecimal balance,
            String reference)
    {
        return new Transaction(date, narration, amount, type, balance, reference, null, TransactionKind.UNKNOWN);
    }

    public Transaction enriched(String merchant, TransactionKind kind){
        return new Transaction(date, narration, amount, type, balance, reference, merchant, kind);
    }

    public boolean isDebit(){return type == TransactionType.DEBIT;}

    public boolean isUnknownMerchant(){
        return merchant == null || merchant.isEmpty() || UNKNOWN_MERCHANT.equals(merchant);
    }
}
