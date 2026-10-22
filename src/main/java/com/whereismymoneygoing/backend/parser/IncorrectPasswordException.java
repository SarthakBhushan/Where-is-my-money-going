package com.whereismymoneygoing.backend.parser;

public class IncorrectPasswordException extends StatementParseException{
    public IncorrectPasswordException(String message){ super(message);}
}
