package com;

import java.io.File;
import java.io.IOException;

public class JackCompiler {
    /**
     * Accept a single file path as an argument and compile either the file
     * provided or the files within the directory that end in '.jack'. The
     * file, once compiled, will be output as a '.vm' file which can then be
     * used to run compiled jack programs.
     *
     * @param args a file path to compile.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Please provide a single file path argument.");
            System.exit(1);
        }

        File file = new File(args[0]);

        if (!file.exists()) {
            System.err.println("The file doesn't exist.");
            System.exit(1);
        }

        // we need to compile every file in the directory
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                if (f.isFile() && f.getName().endsWith(".jack"))
                    compile(f);
            }
        // we only compile the single file
        } else if (file.isFile()) {
            if (!file.getName().endsWith(".jack"))  {
                System.err.println("Please provide a file name ending with .jack");
                System.exit(1);
            } else {
                compile(file);
            }
        }
    }

    /**
     * Compile the file provided turning it into virtual machine code to be
     * used and read by the jack assembler. Compilation involves tokenizing
     * the file input stream and then parsing each token using a top down parser.
     * The parser also provides semantic analysis on the input '.jack' file. Once
     * parsed the code will then be turned into vm code ready for the jack assembler.
     *
     * @param file the file to be compiled
     */
    private static void compile(File file) {
        System.out.println("[Compiling] " + file.getAbsolutePath());

        try {
            CompilationEngine compilationEngine = new CompilationEngine(file);

            // Try running the compiler
            try {
                compilationEngine.run();
            } catch (ParserException e) {
                compilationEngine.deleteVMCode();
                System.err.println("[Parsing error] Line " + e.getLineNumber() + ": " + e.getMessage());
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("[IO Error] " + e.getMessage());
            System.exit(1);
        }
    }
}
