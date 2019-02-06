package com.lexer;

import com.lexer.FileScanner;

import java.io.EOFException;

public class Tokenizer {
    private FileScanner fileScanner;

    public Token getNextToken() {
        return null;
    }

    public Token peekNextToken() {
        return null;
    }

    public static void main(String[] args) {
        FileScanner sc = new FileScanner("in/helloworld.jack");

        try {
            while(true){System.out.println(sc.getNextCharacter());}
        } catch (EOFException ex) {
            // swallow exception since end of file reached
        }
    }
}
