package com.lexer;

public class Tokenizer {
    private Scanner scanner;

    public Token getNextToken() {
        this.scanner.getNextLexeme();
    }

    public Token peekNextToken() {
        return null;
    }

    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
