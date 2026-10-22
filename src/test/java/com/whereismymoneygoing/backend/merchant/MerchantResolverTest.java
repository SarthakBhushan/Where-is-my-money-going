package com.whereismymoneygoing.backend.merchant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.whereismymoneygoing.backend.config.AppProperties;
import com.whereismymoneygoing.backend.model.TransactionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class MerchantResolverTest {

    private final MerchantResolver resolver =
            new MerchantResolver(new MerchantRuleStore(new AppProperties("target/test-rules.yml")));

    @Test
    void resolvesKnownMerchants() {
        assertEquals("Zomato", resolver.resolve("UPI-ZOMATO-12345@icici", TransactionType.DEBIT));
        assertEquals("Swiggy", resolver.resolve("UPI/SWIGGY/UPIPayment", TransactionType.DEBIT));
    }

    @Test
    void unmatchedIsUnknown() {
        assertEquals("Unknown", resolver.resolve("UPI/NOSUCHSHOPXYZ/UPIPayment", TransactionType.DEBIT));
    }

    @Test
    void keywordMatchesGluedIntoOneWord() {
        assertEquals(true, MerchantResolver.phraseMatches(List.of("PAID", "ACTFIBERNET", "LTD"), List.of("ACTFIBERNET")));
    }

    @Test
    void shortKeywordDoesNotMatchAcrossWords() {
        assertEquals(false, MerchantResolver.phraseMatches(List.of("DEBIT", "RENT"), List.of("ITR")));
    }
}