package com;


/**
 * The token class is used to the tokens that have
 * been extracted from the lexemes after processing
 * the input file.
 */
public class Token {
    public enum TokenTypes {
        keyword,
        identifier,
        integer,
        symbol,
        EOF,
        stringConstant
    }

    public String lexeme;
    public TokenTypes type;
    public int lineNumber;

    @Override
    public String toString() {
        return "<Token \"" + lexeme + "\", " + type + ", " + lineNumber + ">";
    }
}
