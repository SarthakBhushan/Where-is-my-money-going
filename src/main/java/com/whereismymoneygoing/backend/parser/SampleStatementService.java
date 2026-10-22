package com.whereismymoneygoing.backend.parser;

import com.whereismymoneygoing.backend.model.ParsedStatement;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class SampleStatementService {

    public static final List<String> BANKS = List.of("ICICI", "HDFC");

    private final StatementService statements;

    public SampleStatementService(StatementService statements) {
        this.statements = statements;
    }

    public ParsedStatement load(String bank) {
        if (!BANKS.contains(bank)) throw new StatementParseException("Unknown sample: " + bank);
        String name = "sample_statement_" + bank.toLowerCase() + ".xlsx";
        try (InputStream in = new ClassPathResource("sample_data/" + name).getInputStream()) {
            return statements.parse(name, in.readAllBytes(), null);
        } catch (IOException e) {
            throw new StatementParseException("Sample statement is missing: " + name, e);
        }
    }
}