package com.lexer;

import com.lexer.Tokenizer;

public class Tokenizer_test {
    public static void main(String[] args) {
        Tokenizer t = new Tokenizer("in/helloworld.jack");
        Token token;

        while ((token = t.getNextToken()).type != Token.TokenTypes.EOF) {
            System.out.println(token);
        }

    }
}
