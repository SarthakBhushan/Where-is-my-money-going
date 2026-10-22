package com.whereismymoneygoing.backend.nlp;

/** What the user is asking for. Only intents with a data answer are computed in Java. */
public enum Intent {
    MERCHANT_SPEND, CATEGORY_SPEND, TOTAL_SPEND, INCOME, SAVINGS,
    TOP_MERCHANTS, BIGGEST_EXPENSE, SUBSCRIPTIONS, COMPARE,
    /** Open-ended: saving tips etc. Goes to the model with the full facts. */
    ADVICE,
    UNKNOWN;

    public boolean hasDataAnswer() {
        return this != ADVICE && this != UNKNOWN;
    }
}
