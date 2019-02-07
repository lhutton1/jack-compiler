package com.lexer;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Arrays;

public class FileScanner {
    private File file;
    private BufferedReader br;


    /**
     * Create an instance of the File Scanner and try to create a Buffered
     * Reader pointing to the filePath specified.
     * @param filePath file path to read file from
     */
    FileScanner(String filePath) {
        try {
            this.file = new File(filePath);
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
        HashSet<Character> detectChars = new HashSet<>(Arrays.asList(' ', '\t', '\n', '/'));

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

    /**
     * Get the next character.
     * If an EOF character is reached exception is thrown.
     * @return
     */
    public String getNextLexeme() {
        int currentCharacter;
        String lexeme = "";

        this.stripWhiteSpaceAndComments();

        // get the while lexeme
        while (this.peek() != ' ' && this.peek() != -1) {
            lexeme += (char)this.read();
        }

        return lexeme;
    }
}
