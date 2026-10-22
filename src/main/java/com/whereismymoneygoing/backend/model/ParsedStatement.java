package com.whereismymoneygoing.backend.model;

import java.io.Serializable;
import java.util.List;

public record ParsedStatement(String bank, String fileName, List<Transaction> transactions) implements Serializable {}
