package com.whereismymoneygoing.backend.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.whereismymoneygoing.backend.model.Transaction;
import com.whereismymoneygoing.backend.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatementRowMapperTest {

    @Test
    void parsesCommonIndianDateFormats() {
        assertEquals(LocalDate.of(2025, 9, 1), StatementRowMapper.parseDate("01/09/25"));
        assertEquals(LocalDate.of(2025, 9, 1), StatementRowMapper.parseDate("01/09/2025"));
        assertEquals(LocalDate.of(2025, 9, 1), StatementRowMapper.parseDate("1-Sep-2025"));
        assertEquals(LocalDate.of(2025, 9, 1), StatementRowMapper.parseDate("2025-09-01"));
    }

    @Test
    void rejectsImpossibleAndGarbageDates() {
        assertNull(StatementRowMapper.parseDate("31/02/2024")); // fails until STRICT fix
        assertNull(StatementRowMapper.parseDate("not a date"));
        assertNull(StatementRowMapper.parseDate(""));
    }

    @Test
    void parsesAmountsWithIndianGroupingSymbolsAndDrCr() {
        assertEquals(new BigDecimal("123456.78"), StatementRowMapper.parseAmount("1,23,456.78"));
        assertEquals(new BigDecimal("500"), StatementRowMapper.parseAmount("₹ 500"));
        assertEquals(new BigDecimal("2500.00"), StatementRowMapper.parseAmount("2,500.00 Dr"));
    }

    @Test
    void blankOrNonNumericAmountsAreNullNotExceptions() {
        assertNull(StatementRowMapper.parseAmount(""));
        assertNull(StatementRowMapper.parseAmount("abc"));
        assertNull(StatementRowMapper.parseAmount(null));
    }

    @Test
    void cleanStripsControlCharsAndNonBreakingSpaces() {
        assertEquals("UPI ZOMATO", StatementRowMapper.clean("\u00A0UPI ZOMATO\u0000 "));
        assertEquals("", StatementRowMapper.clean(null));
    }

    @Test
    void debitRowBecomesDebitTransaction() {
        Transaction t = StatementRowMapper.toTransaction(Map.of(
                "post_date", "02/09/25", "details", "UPI/SWIGGY",
                "debit", "745.00", "credit", "", "balance", "196255.00"));
        assertNotNull(t);
        assertEquals(TransactionType.DEBIT, t.type());
        assertEquals(new BigDecimal("745.00"), t.amount());
        assertEquals(new BigDecimal("196255.00"), t.balance());
    }

    @Test
    void creditRowBecomesCreditTransaction() {
        Transaction t = StatementRowMapper.toTransaction(Map.of(
                "post_date", "01/09/25", "details", "SALARY",
                "debit", "0.00", "credit", "125000.00"));
        assertNotNull(t);
        assertEquals(TransactionType.CREDIT, t.type());
    }

    @Test
    void bannerTotalAndBlankRowsAreSkipped() {
        assertNull(StatementRowMapper.toTransaction(Map.of("details", "Opening Balance")));
        assertNull(StatementRowMapper.toTransaction(Map.of("post_date", "01/09/25", "details", "x",
                "debit", "0.00", "credit", "0.00")));
        assertNull(StatementRowMapper.toTransaction(Map.of()));
    }
}