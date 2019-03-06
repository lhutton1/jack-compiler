package com;

import java.io.IOException;

public class Parser_test {
    public static void main(String[] args) throws ParserException, IOException {
        Parser parser = new Parser("in/Jack Programs/Set 1/Fraction/Fraction.jack");
        parser.startParser();
    }
}
