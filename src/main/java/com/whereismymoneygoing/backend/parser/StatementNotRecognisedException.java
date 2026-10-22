package com.whereismymoneygoing.backend.parser;

public class StatementNotRecognisedException extends StatementParseException{
    public StatementNotRecognisedException(String message){ super(message);}
}
