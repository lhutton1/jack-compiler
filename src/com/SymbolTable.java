package com;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    private HashMap<String, Symbol> symbol;
    private SymbolTable parent;
    private int index;

    public SymbolTable() {
        this(null);
    }

    public SymbolTable(SymbolTable parent) {
        this.symbol = new HashMap<>();
        this.parent = parent;
        this.index = 0;
    }

    public SymbolTable addSymbol(String name, String type, Symbol.KindTypes kind) {
        if (kind == Symbol.KindTypes.PROCEDURE || kind == Symbol.KindTypes.CLASS) {
            SymbolTable child = new SymbolTable(this);
            symbol.put(name, new Symbol(name, type, kind, index++, child));
            return child;
        }

        // if child symbol table doesn't need creating
        symbol.put(name, new Symbol(name, type, kind, index++));
        return null;
    }

    public SymbolTable addSymbol(String name, String type, String kind) {
        Symbol.KindTypes enumKind;

        switch(kind) {
            case "argument":
                enumKind = Symbol.KindTypes.ARGUMENT;
                break;
            case "var":
                enumKind = Symbol.KindTypes.VAR;
                break;
            case "static":
                enumKind = Symbol.KindTypes.STATIC;
                break;
            case "field":
                enumKind = Symbol.KindTypes.FIELD;
                break;
            case "class":
                enumKind = Symbol.KindTypes.CLASS;
                break;
            case "procedure":
                enumKind = Symbol.KindTypes.PROCEDURE;
                break;
            default:
                enumKind = null;
                break;
        }
        return addSymbol(name, type, enumKind);
    }

    public boolean contains(String name) {
        return this.symbol.containsKey(name);
    }

    public SymbolTable restoreParent() {
        return this.parent;
    }

    public void printTable() {
        for (Map.Entry<String, Symbol> s : symbol.entrySet()) {
            System.out.println(s.getValue());
        }
    }

    public void printTables() {
        for (Map.Entry<String, Symbol> s : symbol.entrySet()) {
            System.out.println(s.getValue());

            if (s.getValue().getKind() == Symbol.KindTypes.CLASS || s.getValue().getKind() == Symbol.KindTypes.PROCEDURE) {
                System.out.println("enter");
                s.getValue().getChildSymbolTable().printTables();
                System.out.println("exit");
            }
        }
    }
}