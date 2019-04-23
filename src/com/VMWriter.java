package com;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * The VMWriter writes lines to a new file replaced with the .vm extension.
 * Note: the file still has the same name.
 */
public class VMWriter {
    private File file;
    private BufferedWriter writer;
    private StringBuilder code;

    /**
     * Create the VMWriter object which provides a way to write the vm code
     * to a new file with the same name and .vm extension.
     * @param jackFile the jack file that is being compiled.
     * @throws IOException throws an exception if the jack file cannot be opened.
     */
    public VMWriter(File jackFile) throws IOException {
        this.file = changeExtension(jackFile, ".vm");
        this.writer = Files.newBufferedWriter(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);
        this.code = new StringBuilder();
    }

    /**
     * Change the extension of a file.
     * @param file the file to have its extension changed.
     * @param newExtension the new extension that will replace the current one.
     * @return the new file.
     */
    public static File changeExtension(File file, String newExtension) {
        String name = file.getName().substring(0, file.getName().lastIndexOf('.'));
        return new File(file.getParent() + "/" + name + "2" + newExtension);
    }

    /**
     * Write a new line to the .vm file.
     * @param line the line to write.
     * @throws IOException thrown if stream could not be written to.
     */
    public void writeLine(String line) throws IOException {
        this.writer.append(line);
        this.writer.append("\n");
    }

    /**
     * Close the writer, making sure that all data has
     * been written before hand.
     * @throws IOException thrown if stream could not be flushed/closed.
     */
    public void close() throws IOException {
        this.writer.flush();
        this.writer.close();
    }

    /**
     * Write a new line into a buffer which will be written to the file once
     * writeNow() has been called.
     * @param line the line to write.
     */
    public void writeLater(String line) {
        this.code.append(line);
        this.code.append("\n");
    }

    /**
     * Write the buffer to the file.
     * @throws IOException thrown if stream could not be written to.
     */
    public void writeNow() throws IOException {
        this.writer.append(this.code.toString());
        this.code.setLength(0);
    }

    public void deleteFile() {
        if (!this.file.delete()) {
            System.err.println("Unable to clean up " + this.file.getName());
        }
    }
}
