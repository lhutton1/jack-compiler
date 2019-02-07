package com.lexer;

import com.lexer.Tokenizer;

public class Tokenizer_test {
    public static void main(String[] args) {
        Tokenizer t = new Tokenizer("in/helloworld.jack");

        System.out.println(t.getNextToken());
    }
}
