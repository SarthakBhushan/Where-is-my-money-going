package com.whereismymoneygoing.backend.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.whereismymoneygoing.backend.model.Transaction;
import com.whereismymoneygoing.backend.model.TransactionType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BankParserTest {

    private static final List<List<String>> HDFC = List.of(
            List.of("HDFC BANK Ltd.", "", "", "", "", "", ""),
            List.of("Statement of accounts"),
            List.of("Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance"),
            List.of("********", "*****", "*****", "*****", "*****", "*****", "*****"),
            List.of("01/09/25", "NEFT/CR/ICIC0000045/SALARY/SEPTEMBER 2025", "", "01/09/25", "", "125000.00", "197000.00"),
            List.of("02/09/25", "UPI/SWIGGY/UPIPayment/ICICI Bank", "", "02/09/25", "745.00", "", "196255.00"));

    private static final List<List<String>> ICICI = List.of(
            List.of("DETAILED STATEMENT"),
            List.of("S No.", "Value Date", "Transaction Date", "Cheque Number", "Transaction Remarks",
                    "Withdrawal Amount(INR)", "Deposit Amount(INR)", "Balance(INR)"),
            List.of("1", "01/09/2025", "01/09/2025", "", "NEFT/CR/ICIC0000045/SALARY/SEPTEMBER 2025", "0.00", "125000.00", "197000.00"),
            List.of("2", "02/09/2025", "02/09/2025", "", "UPI/SWIGGY/UPIPayment/ICICI Bank", "745.00", "0.00", "196255.00"));

    @Test
    void hdfcParserSkipsBannerAndAsteriskRowsAndReadsTransactions() {
        List<Transaction> txns = new HdfcExcelParser().parse(HDFC);
        assertEquals(2, txns.size());
        assertEquals(TransactionType.CREDIT, txns.get(0).type());
        assertEquals(new BigDecimal("125000.00"), txns.get(0).amount());
        assertEquals(TransactionType.DEBIT, txns.get(1).type());
        assertEquals(new BigDecimal("745.00"), txns.get(1).amount());
    }

    @Test
    void iciciParserReadsSplitWithdrawalDepositColumns() {
        List<Transaction> txns = new IciciExcelParser().parse(ICICI);
        assertEquals(2, txns.size());
        assertEquals(new BigDecimal("196255.00"), txns.get(1).balance());
    }

    @Test
    void iciciParserRefusesHdfcLayout() {
        assertThrows(StatementNotRecognisedException.class, () -> new IciciExcelParser().parse(HDFC));
    }

    @Test
    void hdfcParserRefusesIciciLayout() {
        assertThrows(StatementNotRecognisedException.class, () -> new HdfcExcelParser().parse(ICICI));
    }

    /** Why StatementService tries ICICI, then HDFC, then SBI: SBI's header rule is the loosest. */
    @Test
    void sbiParserIsLooseEnoughToAcceptHdfcLayout() {
        assertFalse(new SbiExcelParser().parse(HDFC).isEmpty());
    }

    @Test
    void gridWithNoTransactionTableIsNotRecognised() {
        assertThrows(StatementNotRecognisedException.class,
                () -> new HdfcExcelParser().parse(List.of(List.of("hello", "world"))));
    }
}