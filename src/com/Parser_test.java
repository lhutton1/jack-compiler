package com;

import java.io.IOException;

public class Parser_test {
    public static void main(String[] args) throws IOException {
        Parser parser = new Parser("in/helloworld.jack");
        //Parser parser = new Parser("in/Jack Programs/Set 4/Output.jack");
        parser.startParser();
    }
}
