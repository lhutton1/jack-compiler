package com;

import java.io.File;

public class JackCompiler {
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
                if (f.isFile() && file.getName().endsWith(".jack"))
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
        System.out.println("Compilation successfully completed!");
    }

    private static void compile(File file) {
        System.out.println(file.getName());
        System.out.println("compiling");
    }
}
