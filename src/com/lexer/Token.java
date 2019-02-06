package com.lexer;


/**
 * The token class is used to the tokens that have
 * been extracted from the lexemes after processing
 * the input file.
 */
public class Token {
    private enum TokenTypes {
        keyword,
        id,
        assignop,
        addop,
        mulop,
        num
    }

    public String lexeme;
    public TokenTypes type;
}
