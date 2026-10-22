package com.whereismymoneygoing.backend.model;

public enum TransactionKind {
    PURCHASE("Lifestyle Spending"),
    INVESTMENT("Investment"),
    TRANSFER("Family/Self Transfer"),
    CASH_WITHDRAWAL("Cash Withdrawal"),
    INSURANCE("Insurance"),
    UNKNOWN("Unknown");

    private final String label;

    TransactionKind(String label){this.label= label;}
    public String label() {return label;}
}
