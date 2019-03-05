package com;

import java.io.IOException;

public class Parser_test {
    public static void main(String[] args) throws IOException {
        Parser parser = new Parser("in/Jack Programs/Set 1/List/List.jack");
        //Parser parser = new Parser("in/helloworld.jack");
        parser.startParser();
    }
}
