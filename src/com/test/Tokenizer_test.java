package com.test;

import com.Token;
import com.Tokenizer;

import java.io.IOException;

public class Tokenizer_test {
    public static void main(String[] args) throws IllegalArgumentException, IOException {
        Tokenizer t = new Tokenizer("in/Main.jack");
        Token token;

        while ((token = t.getNextToken()).type != Token.Types.EOF) {
            System.out.println(token);
        }
    }
}
