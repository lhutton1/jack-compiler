package com;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {

    private Symbol info;
    private SymbolTable parent;
    private int argumentCount;
    private int fieldCount;
    private int staticCount;
    private int localCount;
    private int otherCount;

    private HashMap<String, Symbol> symbolMap;

    /**
     * Initialise a symbolMap table with no parent.
     */
    public SymbolTable() {
        this(null);
    }



    /**
     * Initialise a new symbolMap table with a parent.
     * @param parent the parent symbolMap table, to keep track of the tree.
     */
    public SymbolTable(SymbolTable parent) {
        this.symbolMap = new HashMap<>();
        this.parent = parent;
        this.argumentCount = 0;
        this.localCount = 0;
        this.staticCount = 0;
        this.fieldCount = 0;
        this.otherCount = 0;
    }

    public SymbolTable(SymbolTable parent, Symbol info) {
        this(parent);
        this.info = info;
    }

    /**
     * Getters
     */
    public Symbol getInfo() { return this.info; }
    public String getName() { return this.info.getName(); }
    public String getType() { return this.info.getType(); }
    public Symbol.Kind getKind() { return this.info.getKind(); }
    public int getIndex() { return this.info.getIndex(); }
    public SymbolTable getParent() { return this.parent; }
    public int getArgumentCount() { return this.argumentCount; }
    public int getLocalCount() { return this.localCount; }
    public int getStaticCount() { return this.staticCount; }
    public int getFieldCount() { return this.fieldCount; }
    public int getOtherCount() { return this.otherCount; }

    public HashMap<Integer, String> getArgumentSymbols() {
        HashMap<Integer, String> result = new HashMap<>();

        for (Symbol s : this.symbolMap.values()) {
            if (s.getKind() == Symbol.Kind.ARGUMENT)
                result.put(s.getIndex(), s.getType());
        }

        return result;
    }



    /**
     * Add a new symbolMap to the symbolMap table.
     * @param name the name of the symbolMap (identifier)
     * @param type the type of the symbolMap (i.e. the object/type that it returns or stores)
     * @param kind the kind of symbolMap (i.e. var, argument, static, field, subroutine, class)
     * @param initialized whether or not the symbolMap has been initialized.
     * @return the symbolMap table that has been created if needed.
     */
    public AddedSymbol addSymbol(String name, String type, Symbol.Kind kind, boolean initialized) {
        Symbol newSymbol;
        SymbolTable child;

        // we need to create a new symbol table if we have a method or inner statement (e.g. if statement)
        if (kind == Symbol.Kind.METHOD || kind == Symbol.Kind.CONSTRUCTOR  || kind == Symbol.Kind.FUNCTION || kind == Symbol.Kind.INNER) {
            child = new SymbolTable(this);

            if (kind == Symbol.Kind.METHOD)
                child.addSymbol("this", this.info.getType(), Symbol.Kind.ARGUMENT, true);

            newSymbol = insertSymbolWithCount(name, type, kind, initialized, child);
            child.info = newSymbol;
            return new AddedSymbol(child, newSymbol);
        }

        // if child symbolMap table doesn't need creating
        newSymbol = insertSymbolWithCount(name, type, kind, initialized, null);
        return new AddedSymbol(null, newSymbol);
    }

    public Symbol insertSymbolWithCount(String name, String type, Symbol.Kind kind, boolean initialized, SymbolTable child) {
        Symbol newSymbol;

        switch (kind) {
            case ARGUMENT:
                newSymbol = new Symbol(name, type, kind, argumentCount++, initialized, child);
                symbolMap.put(name, newSymbol);
                break;
            case LOCAL:
                newSymbol = new Symbol(name, type, kind, localCount++, initialized, child);
                symbolMap.put(name, newSymbol);
                break;
            case FIELD:
                newSymbol = new Symbol(name, type, kind, fieldCount++, initialized, child);
                symbolMap.put(name, newSymbol);
                break;
            case STATIC:
                newSymbol = new Symbol(name, type, kind, staticCount++, initialized, child);
                symbolMap.put(name, newSymbol);
                break;
            default:
                newSymbol = new Symbol(name, type, kind, otherCount++, initialized, child);
                symbolMap.put(name, newSymbol);
                break;
        }

        return newSymbol;
    }


    /**
     * Check if the symbolMap table contains the provided identifier locally.
     * @param name the name of the symbolMap.
     * @return boolean, whether the symbolMap table contains the symbolMap.
     */
    public boolean contains(String name) {
            return this.symbolMap.containsKey(name);
    }


    /**
     * Check if the current symbolMap table or and parents contain the identifier.
     * @param name the name of the symbolMap.
     * @return boolean, whether the symbolMap is contained by the current symbolMap table or any
     * higher up in the hierarchy.
     */
    public boolean hierarchyContains(String name) {
        return this.findHierarchySymbol(name) != null;
    }

    public Symbol findSymbol(String name) {
        if (this.contains(name))
            return this.symbolMap.get(name);

        return null;
    }

    public Symbol findHierarchySymbol(String name) {
        SymbolTable currentTable = this;

        do {
            if (currentTable.contains(name))
                return currentTable.findSymbol(name);
            currentTable = currentTable.parent;
        } while (currentTable != null);

        return null;
    }

    public Symbol getSubroutineSymbol(String name) {
        SymbolTable currentTable = this;

        do {
            if (currentTable.contains(name))
                return currentTable.findSymbol(name);
            currentTable = currentTable.parent;
        } while (currentTable != null && currentTable.getInfo().getKind() != Symbol.Kind.CLASS);

        return null;
    }

    public boolean subroutineContains(String name) {
        return this.getSubroutineSymbol(name) != null;
    }


    /**
     * Print the symbolMap table by listing each symbolMap it contains.
     */
    public void printTable() {
        for (Map.Entry<String, Symbol> s : symbolMap.entrySet()) {
            System.out.println(s.getValue());
        }
    }

    /**
     * Print every symbolMap table below the current in the hierarchy.
     */
    public void printTables() {
        printTables("");
    }


    /**
     * Print every symbolMap table below the current in the hierarchy.
     * @param spacing the amount of spacing before each symbolMap listing.
     */
    private void printTables(String spacing) {
        for (Map.Entry<String, Symbol> s : symbolMap.entrySet()) {
            System.out.println(spacing + s.getValue());

            Symbol.Kind kind = s.getValue().getKind();
            if (kind == Symbol.Kind.METHOD || kind == Symbol.Kind.FUNCTION || kind == Symbol.Kind.CONSTRUCTOR || kind == Symbol.Kind.INNER)
                s.getValue().getChildSymbolTable().printTables(spacing + "    ");
        }
    }
}

class AddedSymbol {
    private SymbolTable newSymbolTable;
    private Symbol newSymbol;

    public AddedSymbol(SymbolTable newSymbolTable, Symbol newSymbol) {
        this.newSymbolTable = newSymbolTable;
        this.newSymbol = newSymbol;
    }

    public SymbolTable getSymbolTable() { return this.newSymbolTable; }
    public Symbol getSymbol() { return this.newSymbol; }
    public void setSymbolTable(SymbolTable symbolTable) { this.newSymbolTable = symbolTable; }
    public void setSymbol(Symbol symbol) { this.newSymbol = symbol; }
}