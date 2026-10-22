package com.whereismymoneygoing.backend.nlp;

/** {@code text} is shown to the user as is; {@code data} is the labelled version given to the model. */
public record Answer(String text, String data) {}
