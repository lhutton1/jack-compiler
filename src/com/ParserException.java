package com;

/**
 * A parser exception is thrown when the parser runs into an issue regarding
 * checking the input file against the grammar of the jack language.
 */
public class ParserException extends Exception {
    private final int lineNumber;

    public ParserException(String message) {
        this(-1, message);
    }

    public ParserException(int lineNumber, String message) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return this.lineNumber;
    }
}
