package com.lexer;

import java.io.EOFException;

public class Tokenizer_test {
    public static void main(String[] args) throws IllegalArgumentException, EOFException {
        Tokenizer t = new Tokenizer("in/helloworld.jack");
        Token token;

        while ((token = t.getNextToken()).type != Token.TokenTypes.EOF) {
            System.out.println(token);
        }
    }
}
