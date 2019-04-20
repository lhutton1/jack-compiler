package com;

/**
 * The token class is used to the tokens that have
 * been extracted from the lexemes after processing
 * the input file.
 */
public class Token {

    // The possible types that a token can represent
    public enum Types {
        KEYWORD,
        IDENTIFIER,
        INTEGER,
        SYMBOL,
        EOF,
        STRING_CONSTANT
    }

    public String lexeme;       // The text stored in the identifier
    public Types type;          // The type that the identifier represents i.e. keyword, symbol, ...
    public int lineNumber;      // The line number that the token is located at

    @Override
    public String toString() {
        return "<Token \"" + lexeme + "\", " + type + ", " + lineNumber + ">";
    }
}
