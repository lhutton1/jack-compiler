package com.lexer;

import java.io.*;
import java.nio.charset.Charset;

public class FileScanner {
    private File file;
    private BufferedReader br;


    /**
     * Create an instance of the File Scanner and try to create a Buffered
     * Reader pointing to the filePath specified.
     * @param filePath filePath to read file from
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
    private int readCharacter() {
        int nextCharacter = -1;

        try {
            nextCharacter = (br.read());
        } catch (IOException ex) {
            System.out.println("Read failed");
        }

        return nextCharacter;
    }

    /**
     * Get the next character that is not white space.
     * If an EOF character is reached exception is thrown.
     * @return
     */
    public char getNextCharacter() throws EOFException {
        int currentCharacter = readCharacter();

        // strip white space
        while (currentCharacter == ' ') {
            currentCharacter = readCharacter();
        }

        if (currentCharacter == -1) {
            throw new EOFException("End of file reached");
        }

        return (char)currentCharacter;
    }
}
