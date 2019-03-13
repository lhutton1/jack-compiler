package com;

import java.util.HashMap;

public class SymbolTable {
    private HashMap<String, Symbol> classScope;
    private HashMap<String, Symbol> methodScope;
    private int staticIndex;
    private int fieldIndex;
    private int argumentIndex;
    private int varIndex;

    public SymbolTable() {
        this.classScope = new HashMap<>();
        this.methodScope = new HashMap<>();
        this.startNewClass();
        this.startNewMethod();
    }

    public void startNewMethod() {
        this.methodScope.clear();
        this.argumentIndex = 0;
        this.varIndex = 0;
    }

    public void startNewClass() {
        this.classScope.clear();
        this.fieldIndex = 0;
        this.staticIndex = 0;
    }

    public void addSymbol(String name, String type, String kind) {
        switch (kind) {
            case "static":
                classScope.put(name, new Symbol(name, type, kind, this.staticIndex++));
                break;
            case "field":
                classScope.put(name, new Symbol(name, type, kind, this.fieldIndex++));
                break;
            case "argument":
                methodScope.put(name, new Symbol(name, type, kind, this.argumentIndex++));
                break;
            case "var":
                methodScope.put(name, new Symbol(name, type, kind, this.varIndex++));
                break;
            default:
                System.err.println("The kind you input has not yet been implemented");
                System.exit(-1);
        }
    }

    public Symbol getSymbol(String name) {
        if (methodScope.containsKey(name))
            return methodScope.get(name);
        else
            return classScope.getOrDefault(name, null);
    }

    public void printTables() {
        System.out.println(this.classScope);
        System.out.println(this.methodScope);
    }
}
