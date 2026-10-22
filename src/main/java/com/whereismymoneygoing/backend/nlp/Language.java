package com.whereismymoneygoing.backend.nlp;

/** The language a question was asked in, so the answer can come back the same way. */
public enum Language {
    ENGLISH, HINGLISH, HINDI;

    /** Instruction appended to the model prompt. */
    public String modelInstruction() {
        return switch (this) {
            case HINDI -> "Reply in Hindi, written in Devanagari script.";
            case HINGLISH -> "Reply in Hinglish: Hindi written in English letters, the way people text in India.";
            case ENGLISH -> "Reply in simple English.";
        };
    }
}
