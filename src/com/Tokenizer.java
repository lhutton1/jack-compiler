package com;

import java.io.*;
import java.util.HashSet;
import java.util.Arrays;
import java.nio.charset.Charset;

public class Tokenizer {
    private BufferedReader br;
    private int lineNumber;
    private Token previousToken;
    private boolean peeked;

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
            '-',
            '~'
    ));

    /**
     * Create an instance of the Tokenizer and try to create a Buffered
     * Reader pointing to the filePath specified.
     * @param filePath file path to read file from
     */
    public Tokenizer(String filePath) throws IllegalArgumentException {
        File file;
        this.lineNumber = 1;
        this.peeked = false;

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

    public Tokenizer(File file) throws IllegalArgumentException, FileNotFoundException {
        this.lineNumber = 1;
        this.peeked = false;

        this.br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file),
                        Charset.forName("UTF-8"))
        );
    }

    /**
     * Get the extension of a filePath.
     * Modified from: https://stackoverflow.com/questions/3571223/
     * how-do-i-get-the-file-extension-of-a-file-in-java
     *
     * @param filePath string containing the relative path of the file
     * @return the file extension
     */
    private String getFileExtension(String filePath) {
        String extension = "";
        int i = filePath.lastIndexOf('.');
        if (i >= 0)
            extension = filePath.substring(i + 1);
        return extension;
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
        // add to the line counter when newline character encountered
        if (nextCharacter == '\n')
            lineNumber++;
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
     * @exception EOFException throw EOF exception if the end of the file has been reached unexpectedly
     */
    private void stripWhiteSpaceAndComments() throws EOFException {
        // while comments or white space need to be removed loop.
        while (true) {
            // strip comments /** or /!* only stopping when */ or EOF reached
            if (this.peek() == '/' && (this.peek(2) == '*' || this.peek(2) == '!')) {
                while((this.peek() != '*' || this.peek(2) != '/')) {
                    if (this.peek() == -1)
                        throw new EOFException("Error, line: " + this.lineNumber + ", Unexpected end of file while scanning multiline comment");
                    this.read();
                }
                this.read();
                this.read();

            // strip comments // only stopping when new line or EOF reached
            } else if (this.peek() == '/' && this.peek(2) == '/') {
                while (this.peek() != '\n' && this.peek() != -1) {
                    this.read();
                }

            // remove white space characters
            } else if (Character.isWhitespace(this.peek())) {
                this.read();
            } else {
                break;
            }
        }
    }

    /**
     * Create a new token with no type initialized.
     * @param lexeme String
     * @return Token
     */
    private Token createToken(String lexeme) {
        return createToken(lexeme, null);
    }

    /**
     * Create a new token given the lexeme and the token type.
     * The lineNumber is added here also.
     * @param lexeme String
     * @param type TokenType
     * @return Token
     */
    private Token createToken(String lexeme, Token.TokenTypes type) {
        Token t = new Token();
        t.lexeme = lexeme;
        t.type = type;
        t.lineNumber = this.lineNumber;
        return t;
    }

    /**
     * Override, giving default argument for isStringConstant.
     */
    private String getMultiCharacterLexeme(int currentCharacter) throws EOFException {
        return this.getMultiCharacterLexeme(currentCharacter, false);
    }

    /**
     * If a lexeme begins with a letter or integer, then there may well
     * be more than one character. This method finds the rest of the
     * characters and builds a lexeme. If the function runs into an EOF
     * character, something has gone wrong.
     * @param currentCharacter The character currently being compared
     * @param isStringConstant if a string has been detected in getNextToken,
     *        then we need to know to only look out for a '"' and no other delimiters
     * @return String containing lexeme
     * @exception EOFException Thrown when the entire file has been read unexpectedly.
     */
    private String getMultiCharacterLexeme(int currentCharacter, boolean isStringConstant) throws EOFException {
        int c = currentCharacter;
        boolean isIntegerToken = false;
        StringBuilder lexeme = new StringBuilder();

        // if the current character is an integer then we need to
        // make sure that a letter placed after it is separated into a new token.
        if (Character.isDigit(c))
            isIntegerToken = true;

        // iterate until delimiter found
        while (detectDelimiter((char)this.peek(), isStringConstant, isIntegerToken)) {
            // ending string literal not found before EOF
            if ((c == -1 || c == 65535) && isStringConstant)
                throw new EOFException("Error, line: " + this.lineNumber + ", Unexpected end of file while scanning string literal");
            // possible grammar error but not dealt with yet
            else if (c == -1 || c == 65535)
                return lexeme.toString();

            lexeme.append((char)c);
            c = (char)this.read();
        }
        // append last character and return lexeme
        lexeme.append((char)c);
        return lexeme.toString();
    }

    /**
     * Detect a delimiter dependent upon whether a string is being
     * detected or an identifier/keyword.
     * @param c the current character
     * @param isStringConstant determine whether to look for '"' or whitespace and symbols
     * @param isIntegerToken determine if a character placed after integer is valid
     * @return false if end of statement has not been reached
     */
    private boolean detectDelimiter(int c, boolean isStringConstant, boolean isIntegerToken) {
        if (isIntegerToken)
            return !Character.isWhitespace(c) && !symbols.contains((char)c) && !Character.isLetter(c);
        else if (isStringConstant)
            return c != '"';
        else
            return !Character.isWhitespace(c) && !symbols.contains((char)c);
    }

    /**
     * Get the next token by reading the next values in the input stream.
     * @return Token
     * @throws IOException if the end of the file is reached, or problem with buffered reader
     * @throws IllegalArgumentException if the reader comes across a character that is unexpected.
     */
    public Token getNextToken() throws IllegalArgumentException, IOException {
        int c;
        Token t;

        if (this.peeked) {
            this.peeked = false;
            return this.previousToken;
        }

        this.stripWhiteSpaceAndComments();
        c = this.read();

        if (c == -1) { // check if EOF token
            t = createToken(String.valueOf((char) c), Token.TokenTypes.EOF);
            this.br.close();
        } else if (c == '"') { // Check if string literal token
            c = this.read();
            t = createToken(getMultiCharacterLexeme(c, true), Token.TokenTypes.stringConstant);
            this.read(); // discard last '"'
        }

        // Check for letter: keyword or identifier
        else if (Character.isLetter(c) || c == '_') {
            t = createToken(getMultiCharacterLexeme(c));
            t.type = keywords.contains(t.lexeme) ?
                    Token.TokenTypes.keyword : Token.TokenTypes.identifier;
        }

        else if (Character.isDigit((char)c)) // Check if integer
            t = createToken(getMultiCharacterLexeme(c), Token.TokenTypes.integer);
        else if (symbols.contains((char)c)) // Check if symbol
            t = createToken(String.valueOf((char)c), Token.TokenTypes.symbol);
        else // Invalid symbol not supported by the jack compiler
            throw new IllegalArgumentException("Error, line: " + this.lineNumber + ", Unresolved symbol \"" + (char)c + "\" found.");

        // store current token to enable peek to function
        this.peeked = false;
        this.previousToken = t;
        return t;
    }

    /**
     * Peek the next token and return the result, without
     * reading ahead to the next token.
     * @return The next token.
     * @throws IllegalArgumentException if the reader comes across a character that is unexpected.
     * @throws IOException if the end of the file is reached, or problem with buffered reader
     */
    public Token peekNextToken() throws IllegalArgumentException, IOException {
        if (this.peeked)
            return this.previousToken;

        Token nextToken = this.getNextToken();
        this.peeked = true;
        return nextToken;
    }
}
