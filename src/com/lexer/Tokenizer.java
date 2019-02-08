package com.lexer;

import java.io.*;
import java.util.HashSet;
import java.util.Arrays;
import java.nio.charset.Charset;

public class Tokenizer {
    private BufferedReader br;
    private int lineNumber;

    private HashSet<String> keywords = new HashSet<>(Arrays.asList(
            "class",
            "constructor",
            "function",
            "method",
            "field",
            "static",
            "var",
            "int",
            "char",
            "boolean",
            "void",
            "true",
            "false",
            "null",
            "this",
            "let",
            "do",
            "if",
            "else",
            "while",
            "return"
    ));

    private HashSet<Character> symbols = new HashSet<>(Arrays.asList(
            '{',
            '}',
            '(',
            ')',
            '[',
            ']',
            '.',
            ',',
            ';',
            '+',
            '-',
            '*',
            '/',
            '&',
            '|',
            '<',
            '>',
            '=',
            '-'
    ));

    private HashSet<Character> whiteSpaceDelimiters = new HashSet<>(Arrays.asList(
            ' ',
            '\n',
            '\r',
            '\t'
    ));

    /**
     * https://stackoverflow.com/questions/3571223/how-do-i-get-the-file-extension-of-a-file-in-java
     * @param filePath
     * @return
     */
    private String getFileExtension(String filePath) {
        String extension = "";
        int i = filePath.lastIndexOf('.');
        if (i >= 0)
            extension = filePath.substring(i+1);
        return extension;
    }

    /**
     * Create an instance of the Tokenizer and try to create a Buffered
     * Reader pointing to the filePath specified.
     * @param filePath file path to read file from
     */
    Tokenizer(String filePath) throws IllegalArgumentException {
        File file;
        lineNumber = 1;

        if (!getFileExtension(filePath).equals("jack"))
            throw new IllegalArgumentException("File must be of type '.jack'");

        try {
            file = new File(filePath);
            this.br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file),
                Charset.forName("UTF-8"))
            );
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Read a single character from the file that has been referenced.
     * @return int, resembling a character
     */
    private int read() {
        int nextCharacter = -1;

        try {
            nextCharacter = (br.read());
        } catch (IOException ex) {
            System.out.println("Read failed");
        }
        // add to the line counter when
        // newline character encountered
        if (nextCharacter == '\n') {
            lineNumber++;
        }

        return nextCharacter;
    }

    /**
     * Peek first character.
     * @return int resembling a character from file.
     */
    private int peek() {
        return this.peek(1);
    }

    /**
     * Peek a character from the file that has been referenced.
     * This is done by reading a character then resetting the buffered reader.
     * @param lookAheadValue changing this value will change the 'look ahead' amount.
     * @return int resembling a character from file.
     */
    private int peek(int lookAheadValue) {
        int nextCharacter = -1;

        try {
            br.mark(lookAheadValue);
            for (int x = 0; x < lookAheadValue; x++) {
                nextCharacter = br.read();
            }
            br.reset();
        } catch (IOException ex) {
            System.out.println("Peek failed");
        }
        return nextCharacter;
    }


    /**
     * Strip any white space and comments that exist in the input stream.
     */
    private void stripWhiteSpaceAndComments() {
        HashSet<Character> detectChars = new HashSet<>(Arrays.asList(' ', '\t', '\n', '/', '\r'));

        while (detectChars.contains((char)this.peek()) && this.peek() != -1) {
            // strip comments /** or /!* only stopping when */ or EOF reached
            if (this.peek() == '/' && (this.peek(2) == '*' || this.peek(2) == '!')) {
                while((this.peek() != '*' || this.peek(2) != '/') && this.peek() != -1) {
                    this.read();
                }
                this.read();

            // strip comments // only stopping when new line or EOF reached
            } else if (this.peek() == '/' && this.peek(2) == '/') {
                while (this.peek() != '\n' && this.peek() != -1) {
                    this.read();
                }

            // remove white space characters
            } else {
                this.read();
            }
        }
    }

    private Token createToken(String lexeme) {
        return createToken(lexeme, null);
    }

    private Token createToken(String lexeme, Token.TokenTypes type) {
        Token t = new Token();
        t.lexeme = lexeme;
        t.type = type;
        t.lineNumber = this.lineNumber;
        return t;
    }

    private String getMultiCharacterLexeme(int currentCharacter) {
        int c = currentCharacter;
        StringBuilder lexeme = new StringBuilder();
        lexeme.append((char)c);

        // iterate until delimiter found
        while (!whiteSpaceDelimiters.contains((char)c) && !symbols.contains((char)c)) {
            c = (char)this.read();
            lexeme.append((char)c);
            c = this.peek();
        }

        return lexeme.toString();
    }

    private String getStringLexeme(int currentCharacter) {
        int c = currentCharacter;
        StringBuilder lexeme = new StringBuilder();
        lexeme.append((char)c);

        do {
            c = (char)this.read();
            lexeme.append((char)c);
            c = this.peek();
        } while(c != '"');

        lexeme.append((char)this.read());
        return lexeme.toString();
    }


    public Token getNextToken() throws IllegalArgumentException {
        int c;

        this.stripWhiteSpaceAndComments();
        c = this.read();

        // check if EOF token
        if (c == -1)
            return createToken(String.valueOf((char) c), Token.TokenTypes.EOF);

        // Check if string literal token
        else if (c == '"')
            return createToken(getStringLexeme(c), Token.TokenTypes.string);

        // Check is letter: keyword or identifier
        else if (Character.isLetter(c)) {
            Token t = createToken(getMultiCharacterLexeme(c));
            t.type = keywords.contains(t.lexeme) ?
                    Token.TokenTypes.keyword : Token.TokenTypes.identifier;
            return t;
        }

        // Check if integer
        else if (Character.isDigit((char)c))
            return  createToken(getMultiCharacterLexeme(c), Token.TokenTypes.integer);

        // Check if symbol
        else if (symbols.contains((char)c))
            return createToken(String.valueOf((char)c), Token.TokenTypes.symbol);

        else {
            throw new IllegalArgumentException("Unresolved symbol");
        }
    }

    public Token peekNextToken() {
        return null;
    }
}
