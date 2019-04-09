package com;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class VMWriter {
    private File file;
    private BufferedWriter writer;

    public VMWriter(File jackFile) throws IOException {
        this.file = changeExtension(jackFile, ".vm");
        this.writer = Files.newBufferedWriter(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);
    }

    public static File changeExtension(File file, String newExtension) {
        String name = file.getName().substring(0, file.getName().lastIndexOf('.'));
        return new File(file.getParent() + "/" + name + "(test)" + newExtension);
    }

    public void writeLine(String line) throws IOException {
        if (this.writer == null)
            return;

        this.writer.append(line);
        this.writer.append("\n");
    }

    public void close() throws IOException {
        this.writer.flush();
        this.writer.close();
    }
}
