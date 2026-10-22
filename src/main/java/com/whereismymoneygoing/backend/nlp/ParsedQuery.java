package com.whereismymoneygoing.backend.nlp;

import com.whereismymoneygoing.backend.model.TransactionKind;
import java.util.List;

/** What the language layer understood from one question. */
public record ParsedQuery(Language language, Intent intent, String merchant, TransactionKind kind,
                          List<Period> periods, boolean inherited) {

    public boolean hasPeriod() {
        return !periods.isEmpty();
    }
}
