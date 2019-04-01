package com;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    private HashMap<String, Symbol> symbol;
    private SymbolTable parent;
    private Symbol.KindTypes kind;
    private String classType;
    private int index;

    /**
     * Initialise a symbol table with no parent.
     */
    public SymbolTable() {
        this(null);
    }


    /**
     * Initialise a new symbol table with a parent.
     * @param parent the parent symbol table, to keep track of the tree.
     */
    public SymbolTable(SymbolTable parent) {
        this.symbol = new HashMap<>();
        this.parent = parent;
        this.index = 0;
    }


    /**
     * Add a new symbol to the symbol table.
     * @param name the name of the symbol (identifier)
     * @param type the type of the symbol (i.e. the object/type that it returns or stores)
     * @param kind the kind of symbol (i.e. var, argument, static, field, subroutine, class)
     * @param initialized whether or not the symbol has been initialized.
     * @return the symbol table that has been created if needed.
     */
    public SymbolTable addSymbol(String name, String type, Symbol.KindTypes kind, boolean initialized) {
        if (kind == Symbol.KindTypes.SUBROUTINE || kind == Symbol.KindTypes.CLASS || kind == Symbol.KindTypes.INNER) {
            SymbolTable child = new SymbolTable(this);

            if (kind == Symbol.KindTypes.CLASS)
                child.classType = name;
            else if (kind == Symbol.KindTypes.SUBROUTINE)
                child.addSymbol("this", this.classType, Symbol.KindTypes.ARGUMENT, true);

            symbol.put(name, new Symbol(name, type, kind, index++, initialized, child));
            child.kind = kind;
            return child;
        }

        // if child symbol table doesn't need creating
        symbol.put(name, new Symbol(name, type, kind, index++, initialized));
        return null;
    }


    /**
     * Add a symbol given a string as the kind.
     */
    public SymbolTable addSymbol(String name, String type, String kind, boolean initialized) {
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
                enumKind = Symbol.KindTypes.SUBROUTINE;
                break;
            case "inner":
                enumKind = Symbol.KindTypes.INNER;
                break;
            default:
                enumKind = null;
                break;
        }
        return addSymbol(name, type, enumKind, initialized);
    }


    /**
     * Check if the symbol table contains the provided identifier locally.
     * @param name the name of the symbol.
     * @return boolean, whether the symbol table contains the symbol.
     */
    public boolean contains(String name) {
            return this.symbol.containsKey(name);
    }


    /**
     * Check if the current symbol table or and parents contain the identifier.
     * @param name the name of the symbol.
     * @return boolean, whether the symbol is contained by the current symbol table or any
     * higher up in the hierarchy.
     */
    public boolean hierarchyContains(String name) {
        return this.getGlobalSymbol(name) != null;
    }

    public Symbol getSymbol(String name) {
        if (this.contains(name))
            return this.symbol.get(name);

        return null;
    }

    public Symbol getGlobalSymbol(String name) {
        SymbolTable currentTable = this;

        // search hierarchy
        do {
            if (currentTable.contains(name))
                return currentTable.getSymbol(name);
            currentTable = currentTable.parent;
        } while (currentTable != null);

        return null;
    }

    public Symbol getSubroutineSymbol(String name) {
        SymbolTable currentTable = this;

        // search hierarchy
        do {
            if (currentTable.contains(name))
                return currentTable.getSymbol(name);
            currentTable = currentTable.parent;
        } while (currentTable != null && currentTable.getKind() != Symbol.KindTypes.CLASS);

        return null;
    }

    public boolean subroutineContains(String name) {
        return this.getSubroutineSymbol(name) != null;
    }

    /**
     * restore the parent symbol table.
     * @return parent symbol table
     */
    public SymbolTable restoreParent() {
        return this.parent;
    }


    /**
     * Print the symbol table by listing each symbol it contains.
     */
    public void printTable() {
        for (Map.Entry<String, Symbol> s : symbol.entrySet()) {
            System.out.println(s.getValue());
        }
    }

    /**
     * Print every symbol table below the current in the hierarchy.
     */
    public void printTables() {
        printTables("");
    }


    /**
     * Print every symbol table below the current in the hierarchy.
     * @param spacing the amount of spacing before each symbol listing.
     */
    private void printTables(String spacing) {
        for (Map.Entry<String, Symbol> s : symbol.entrySet()) {
            System.out.println(spacing + s.getValue());

            if (s.getValue().getKind() == Symbol.KindTypes.CLASS || s.getValue().getKind() == Symbol.KindTypes.SUBROUTINE ||
                    s.getValue().getKind() == Symbol.KindTypes.INNER) {
                s.getValue().getChildSymbolTable().printTables(spacing + "    ");
            }
        }
    }

    public Symbol.KindTypes getKind() {
        return kind;
    }
}