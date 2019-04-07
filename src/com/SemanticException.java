package com;

public class SemanticException extends Exception {
    private final int lineNumber;

    public SemanticException(String message) {
        this(-1, message);
    }

    public SemanticException(int lineNumber, String message) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return this.lineNumber;
    }
}
