package com.whereismymoneygoing.backend.nlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.whereismymoneygoing.backend.analytics.AnalyticsService;
import com.whereismymoneygoing.backend.model.TransactionKind;
import org.junit.jupiter.api.Test;

class QueryUnderstandingTest {

    private final QueryUnderstandingService service =
            new QueryUnderstandingService(new QueryParser(), new QueryExecutor(new AnalyticsService()));

    private QueryUnderstandingService.Understanding ask(String chat, String question) {
        return service.understand(chat, question, SampleStatement.statement());
    }

    private void assertAnswerContains(String question, Intent intent, String merchant, String... expectedParts) {
        var u = ask("t-" + question, question);
        assertEquals(intent, u.query().intent(), question);
        assertEquals(merchant, u.query().merchant(), question);
        assertTrue(u.answered(), question);
        for (String part : expectedParts) {
            assertTrue(u.answer().text().contains(part), question + " -> expected '" + part + "' in: " + u.answer().text());
        }
    }

    @Test
    void merchantSpendThisMonthInEnglishHinglishAndHindi() {
        assertAnswerContains("how much did I spend on zomato this month", Intent.MERCHANT_SPEND, "Zomato",
                "₹1,280", "October 2026", "17.4% lower than September 2026");
        assertAnswerContains("is mahine zomato pe kitna kharcha hua", Intent.MERCHANT_SPEND, "Zomato",
                "₹1,280", "October 2026 mein", "kam hai");
        assertAnswerContains("इस महीने ज़ोमैटो पर कितना खर्च हुआ", Intent.MERCHANT_SPEND, "Zomato",
                "₹1,280", "अक्टूबर 2026 में", "कम है");
    }

    @Test
    void misspelledAndDevanagariMerchantNamesAreRecognised() {
        assertAnswerContains("what is the amount this month spend on zomatao", Intent.MERCHANT_SPEND, "Zomato", "₹1,280");
        assertAnswerContains("zmato kitna kharcha is mahine", Intent.MERCHANT_SPEND, "Zomato", "₹1,280");
        assertAnswerContains("ज़ोमाटो पर इस महीने कितना", Intent.MERCHANT_SPEND, "Zomato", "₹1,280");
        assertAnswerContains("pichle mahine swigy me kitna gaya", Intent.MERCHANT_SPEND, "Swiggy",
                "koi payment nahi mila");
        assertAnswerContains("स्विगी पर अगस्त में कितना खर्च हुआ", Intent.MERCHANT_SPEND, "Swiggy", "₹1,200");
    }

    @Test
    void totalsSavingsAndIncome() {
        assertAnswerContains("total spend last month", Intent.TOTAL_SPEND, null, "₹9,099", "September 2026");
        assertAnswerContains("how much did I save in september", Intent.SAVINGS, null, "₹70,901");
        assertAnswerContains("mera income kitna hai", Intent.INCOME, null, "₹2,42,000");
        assertAnswerContains("इस महीने कितनी बचत हुई", Intent.SAVINGS, null, "₹64,671", "बचाए");
    }

    @Test
    void topMerchantsBiggestPaymentAndSubscriptions() {
        var top = ask("a", "where did most of my money go");
        assertEquals(Intent.TOP_MERCHANTS, top.query().intent());
        assertTrue(top.answer().text().contains("1. Mutual Funds: ₹10,000"));

        var big = ask("b", "biggest single payment this month");
        assertEquals(Intent.BIGGEST_EXPENSE, big.query().intent());
        assertTrue(big.answer().text().contains("1. 20 Oct 2026: ₹7,500 to Unrecognised merchants"));

        var subs = ask("c", "mere subscriptions dikhao");
        assertEquals(Intent.SUBSCRIPTIONS, subs.query().intent());
        assertTrue(subs.answer().text().contains("Netflix: ₹649 har mahine"));
    }

    @Test
    void compareThisMonthWithLastMonth() {
        var u = ask("d", "compare this month with last month");
        assertEquals(Intent.COMPARE, u.query().intent());
        assertTrue(u.answer().text().contains("₹17,329"));
        assertTrue(u.answer().text().contains("₹9,099"));
        assertTrue(u.answer().text().contains("90.4% higher"));
        assertTrue(u.answer().text().contains("Unrecognised merchants: +₹7,500"));
    }

    @Test
    void categoryQuestion() {
        var u = ask("e", "how much did I invest in october");
        assertEquals(Intent.CATEGORY_SPEND, u.query().intent());
        assertEquals(TransactionKind.INVESTMENT, u.query().kind());
        assertTrue(u.answer().text().contains("₹5,000"));
    }

    @Test
    void adviceAndSmallTalkGoToTheModel() {
        var advice = ask("f", "how can I save more money");
        assertEquals(Intent.ADVICE, advice.query().intent());
        assertFalse(advice.answered());
        assertEquals(Intent.ADVICE, ask("g", "paisa kaise bachau").query().intent());
        assertEquals(Intent.UNKNOWN, ask("h", "hello").query().intent());
        assertEquals(Intent.UNKNOWN, ask("i", "show me more").query().intent());   // "more" is not a merchant
    }

    @Test
    void periodOutsideTheStatementIsExplained() {
        var u = ask("j", "zomato march 2026");
        assertTrue(u.answered());
        assertTrue(u.answer().text().contains("I found no transactions in March 2026"));
    }

    @Test
    void followUpQuestionsReuseMerchantAndPeriod() {
        ask("chat", "zomato kitna kharcha hua is mahine");
        var lastMonth = ask("chat", "aur pichle mahine?");
        assertTrue(lastMonth.query().inherited());
        assertEquals("Zomato", lastMonth.query().merchant());
        assertTrue(lastMonth.answer().text().contains("₹1,550"));

        var swiggy = ask("chat", "what about swiggy");
        assertEquals("Swiggy", swiggy.query().merchant());
        assertTrue(swiggy.answer().text().contains("September 2026"));   // keeps the month under discussion

        service.forget("chat");
        assertNull(service.lastData("chat"));
        assertNotNull(ask("chat", "hello").query());
    }
}
