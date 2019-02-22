package com;

import com.Token;
import com.Tokenizer;

import java.io.IOException;

public class Parser {
    private Tokenizer t;
    private XMLWriter test;

    Parser (String filePath) throws IOException {
        this.t = new Tokenizer(filePath);
        this.test = new XMLWriter();
    }

    public void startParser() throws IOException {
        while (this.t.peekNextToken().type != Token.TokenTypes.EOF) {
            this.parseClass();
        }
    }

    private void parseClass() throws IOException{
        Token token;
        // should be class keyword
        System.out.println(this.t.peekNextToken());
        System.out.println(this.t.peekNextToken().lexeme.equals("class"));
        token = this.t.getNextToken();
        System.out.println(token);
        if (!token.lexeme.equals("class"))
            throw new IOException("Expected token: class");
        else {
            System.out.println("class found");
            this.test.addElement("class", token.lexeme);
        }
    }
}
