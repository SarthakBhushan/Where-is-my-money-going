package com.whereismymoneygoing.backend.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.whereismymoneygoing.backend.classifier.TransactionClassifier;
import com.whereismymoneygoing.backend.config.AppProperties;
import com.whereismymoneygoing.backend.enrichment.TransactionEnricher;
import com.whereismymoneygoing.backend.merchant.MerchantResolver;
import com.whereismymoneygoing.backend.merchant.MerchantRuleStore;
import com.whereismymoneygoing.backend.model.ParsedStatement;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatementServiceTest {

    @TempDir Path dir;

    private static final List<List<String>> HDFC = List.of(
            List.of("Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance"),
            List.of("02/09/25", "UPI/ZOMATO/UPIPayment/ICICI Bank", "", "02/09/25", "898.00", "", "195357.00"));

    /** Fake reader: tests routing/enrichment without needing real .xlsx bytes. */
    private StatementService service(List<List<String>> grid) {
        ExcelGridReader reader = new ExcelGridReader() {
            @Override public List<List<String>> read(byte[] bytes, String password) { return grid; }
        };
        MerchantResolver resolver = new MerchantResolver(new MerchantRuleStore(
                new AppProperties(dir.resolve("m.yml").toString())));
        return new StatementService(reader, new IciciExcelParser(), new HdfcExcelParser(),
                new SbiExcelParser(), new TransactionEnricher(resolver, new TransactionClassifier()));
    }

    @Test
    void detectsHdfcAndEnrichesMerchant() {
        ParsedStatement s = service(HDFC).parse("hdfc.xlsx", new byte[0], null);
        assertEquals("HDFC", s.bank());
        assertEquals("Zomato", s.transactions().get(0).merchant());
    }

    @Test
    void pdfIsRejectedWithFriendlyMessage() {
        StatementParseException e = assertThrows(StatementParseException.class,
                () -> service(HDFC).parse("x.pdf", new byte[0], null));
        assertTrue(e.getMessage().contains("PDF"));
    }

    @Test
    void unrecognisedSheetGivesNoParserMessage() {
        StatementParseException e = assertThrows(StatementParseException.class,
                () -> service(List.of(List.of("random", "sheet"))).parse("x.xlsx", new byte[0], null));
        assertEquals("No parser could recognise this statement.", e.getMessage());
    }
}