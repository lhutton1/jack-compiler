package com.lexer;


/**
 * The token class is used to the tokens that have
 * been extracted from the lexemes after processing
 * the input file.
 */
public class Token {
    public enum TokenTypes {
        keyword,
        id,
        integer,
        symbol,
        EOF
    }

    public String lexeme;
    public TokenTypes type;

    @Override
    public String toString() {
        return "<\"" + lexeme + "\", " + type +">";
    }
}
