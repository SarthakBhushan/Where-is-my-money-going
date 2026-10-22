package com.whereismymoneygoing.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.whereismymoneygoing.backend.analytics.AnalyticsService;
import com.whereismymoneygoing.backend.model.ParsedStatement;
import com.whereismymoneygoing.backend.nlp.SampleStatement;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinanceFactsBuilderTest {

    @Test
    void rupeesUseIndianGrouping() {
        assertEquals("₹0", FinanceFactsBuilder.rs(BigDecimal.ZERO));
        assertEquals("₹999", FinanceFactsBuilder.rs(new BigDecimal("999")));
        assertEquals("₹1,000", FinanceFactsBuilder.rs(new BigDecimal("1000")));
        assertEquals("₹1,00,000", FinanceFactsBuilder.rs(new BigDecimal("100000")));
        assertEquals("₹85,85,000", FinanceFactsBuilder.rs(new BigDecimal("8585000")));
        assertEquals("-₹1,251", FinanceFactsBuilder.rs(new BigDecimal("-1250.50")));
        assertEquals("n/a", FinanceFactsBuilder.rs(null));
    }

    @Test
    void templateCharactersAreStrippedFromStatementText() {
        assertEquals("UPI/(weird)/ note", FinanceFactsBuilder.clean("UPI/{weird}\\ note"));
        assertEquals("a b", FinanceFactsBuilder.clean("a\nb"));
        assertEquals("", FinanceFactsBuilder.clean(null));
    }

    @Test
    void factsContainLabelledPerMerchantMonthlyNumbers() {
        String facts = new FinanceFactsBuilder(new AnalyticsService()).build(SampleStatement.statement());
        assertTrue(facts.contains("SPEND ON EACH TOP MERCHANT, MONTH BY MONTH"));
        assertTrue(facts.contains("Zomato: Aug 2026 ₹1,070, Sep 2026 ₹1,550, Oct 2026 ₹1,280"), facts);
        assertTrue(facts.contains("ALL MERCHANTS RECOGNISED IN THIS STATEMENT:"));
        assertTrue(facts.contains("Zomato"));
        assertFalse(facts.contains("{"));
    }

    @Test
    void emptyStatementIsHandled() {
        String facts = new FinanceFactsBuilder(new AnalyticsService())
                .build(new ParsedStatement("HDFC", "x.xlsx", List.of()));
        assertEquals("The statement contains no transactions.", facts);
    }
}
