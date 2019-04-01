package com.test;

import com.Parser;
import com.ParserException;
import com.SemanticException;

import java.io.IOException;

public class Parser_test {
    public static void main(String[] args) throws ParserException, SemanticException, IOException {
        Parser parser = new Parser("in/helloworld.jack");
    }
}
