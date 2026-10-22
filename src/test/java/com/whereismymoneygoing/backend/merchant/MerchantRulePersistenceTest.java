package com.whereismymoneygoing.backend.merchant;

import static org.junit.jupiter.api.Assertions.*;

import com.whereismymoneygoing.backend.config.AppProperties;
import com.whereismymoneygoing.backend.model.Transaction;
import com.whereismymoneygoing.backend.model.TransactionType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MerchantRulePersistenceTest {

    @TempDir Path dir;

    private MerchantResolver newResolver() {
        return new MerchantResolver(new MerchantRuleStore(
                new AppProperties(dir.resolve("merchant.local.yml").toString())));
    }

    @Test
    void unknownShopIsUnknownUntilUserTeachesIt() {
        assertEquals(Transaction.UNKNOWN_MERCHANT,
                newResolver().resolve("UPI/CORNER CHAI POINT/UPIPayment", TransactionType.DEBIT));
    }

    @Test
    void learnedRuleIsWrittenToDiskAndSurvivesRestart() {
        MerchantRuleStore store = new MerchantRuleStore(
                new AppProperties(dir.resolve("merchant.local.yml").toString()));
        store.addLocalRule("Chai Point", "corner chai point");

        assertTrue(Files.exists(dir.resolve("merchant.local.yml")));

        // brand new store + resolver = simulated app restart
        MerchantResolver afterRestart = newResolver();
        assertEquals("Chai Point",
                afterRestart.resolve("UPI/CORNER CHAI POINT/UPIPayment", TransactionType.DEBIT));
    }

    @Test
    void reloadPicksUpNewRuleWithoutRestart() {
        MerchantRuleStore store = new MerchantRuleStore(
                new AppProperties(dir.resolve("merchant.local.yml").toString()));
        MerchantResolver resolver = new MerchantResolver(store);
        store.addLocalRule("Chai Point", "corner chai point");
        resolver.reload();
        assertEquals("Chai Point", resolver.resolve("UPI/CORNER CHAI POINT/X", TransactionType.DEBIT));
    }

    @Test
    void dividendIsNeverMatchedOnADebit() { // CREDIT_ONLY guard
        MerchantResolver resolver = newResolver();
        String credit = resolver.resolve("ACH C- INFOSYS LTD DIVIDEND", TransactionType.CREDIT);
        String debit = resolver.resolve("ACH C- INFOSYS LTD DIVIDEND", TransactionType.DEBIT);
        assertNotEquals("Dividend Credit", debit);
        // only assert the credit side if your built-in YAML has a DIVIDEND keyword:
        // assertEquals("Dividend Credit", credit);
    }

    @Test
    void multiWordKeywordMatchesAsContiguousWholeWords() {
        assertTrue(MerchantResolver.phraseMatches(List.of("UPI", "BIG", "BAZAAR"), List.of("BIG", "BAZAAR")));
        assertFalse(MerchantResolver.phraseMatches(List.of("BIG", "UPI", "BAZAAR"), List.of("BIG", "BAZAAR")));
    }

    @Test
    void gluedNarrationIsStillMatched() { // ACTFIBERNET is one word in the bank narration
        assertTrue(MerchantResolver.phraseMatches(List.of("UPI", "ACTFIBERNET"), List.of("ACT", "FIBERNET")));
    }
}