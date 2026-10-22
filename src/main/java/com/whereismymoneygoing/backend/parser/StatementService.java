package com.whereismymoneygoing.backend.parser;

import com.whereismymoneygoing.backend.enrichment.TransactionEnricher;
import com.whereismymoneygoing.backend.model.ParsedStatement;
import com.whereismymoneygoing.backend.model.Transaction;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class StatementService {

    private final ExcelGridReader reader;
    private final List<ExcelStatementParser> parsers;
    private final TransactionEnricher enricher;

    public StatementService(ExcelGridReader reader, IciciExcelParser icici, HdfcExcelParser hdfc,
                            SbiExcelParser sbi, TransactionEnricher enricher) {
        this.reader = reader;
        this.parsers = List.of(icici, hdfc, sbi);   // same order as the original app
        this.enricher = enricher;
    }

    public ParsedStatement parse(String fileName, byte[] bytes, String password) {
        String name = fileName == null || fileName.isBlank() ? "statement" : fileName;
        if (name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new StatementParseException(
                    "PDF statements are not supported yet. Please upload the .xls / .xlsx export.");
        }

        List<List<String>> grid = reader.read(bytes, password);
        for (ExcelStatementParser parser : parsers) {
            try {
                List<Transaction> raw = parser.parse(grid);
                return new ParsedStatement(parser.bankName(), name, enricher.enrichAll(raw));
            } catch (StatementNotRecognisedException ignored) {
                // fall through to the next bank
            }
        }
        throw new StatementParseException("No parser could recognise this statement.");
    }
}