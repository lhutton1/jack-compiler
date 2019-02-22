package com;

import com.Token;
import com.Tokenizer;

import java.io.IOException;

public class Tokenizer_test {
    public static void main(String[] args) throws IllegalArgumentException, IOException {
        Tokenizer t = new Tokenizer("in/helloworld.jack");
        Token token;

        while ((token = t.getNextToken()).type != Token.TokenTypes.EOF) {
            System.out.println(token);
        }
    }
}
