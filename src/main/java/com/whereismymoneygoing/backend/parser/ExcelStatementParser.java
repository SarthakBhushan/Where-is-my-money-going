package com.whereismymoneygoing.backend.parser;

import com.whereismymoneygoing.backend.model.Transaction;

import java.util.List;

public interface ExcelStatementParser {
    String bankName();

    /** @throws StatementNotRecognisedException when this isn't this bank statement layout */
    List<Transaction> parse(List<List<String>>grid);
}
