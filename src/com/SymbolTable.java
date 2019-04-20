package com;

import java.util.HashMap;
import java.util.Map;

/**
 * The symbol table is a data structure used to organise and store all
 * the symbols that have been declared in the .jack source file.
 */
public class SymbolTable {
    private Symbol symbol;          // The symbol that is associated with the current symbol table.
    private SymbolTable parent;     // The symbol table that is parent to the current symbol table.

    private int argumentCount;      // The argument count, incremented when an argument is added to the table.
    private int fieldCount;         // The field count, incremented when a field is added to the table.
    private int staticCount;        // The static count, incremented when a static is added to the table.
    private int localCount;         // The local count, incremented when a local is added to the table.
    private int otherCount;         // The other count, incremented when another symbol is added to the table.

    private HashMap<String, Symbol> symbolMap;  // The symbols in the symbol table.

    /**
     * Initialise a new symbol table with a parent.
     * @param parent the parent symbol table, to keep track of the tree.
     */
    public SymbolTable(SymbolTable parent, Symbol symbol) {
        this.parent = parent;
        this.symbol = symbol;

        this.symbolMap = new HashMap<>();
        this.argumentCount = 0;
        this.localCount = 0;
        this.staticCount = 0;
        this.fieldCount = 0;
        this.otherCount = 0;
    }

    /**
     * Getters
     */
    public Symbol getSymbol() { return this.symbol; }
    public String getName() { return this.symbol.getName(); }
    public String getType() { return this.symbol.getType(); }
    public Symbol.Kind getKind() { return this.symbol.getKind(); }
    public int getIndex() { return this.symbol.getIndex(); }
    public SymbolTable getParent() { return this.parent; }
    public int getArgumentCount() { return this.argumentCount; }
    public int getLocalCount() { return this.localCount; }
    public int getStaticCount() { return this.staticCount; }
    public int getFieldCount() { return this.fieldCount; }

    /**
     * Get all symbols in the table that consist of an argument kind.
     * @return HashMap with all argument symbols.
     */
    public HashMap<Integer, String> getArgumentSymbols() {
        HashMap<Integer, String> result = new HashMap<>();

        for (Symbol s : this.symbolMap.values()) {
            if (s.getKind() == Symbol.Kind.ARGUMENT)
                result.put(s.getIndex(), s.getType());
        }

        return result;
    }

    /**
     * Add a new symbol to the symbol table.
     * @param name the name of the symbol (identifier).
     * @param type the type of the symbol (i.e. the object/type that it returns or stores).
     * @param kind the kind of symbol (i.e. var, argument, static, field, subroutine, class).
     * @param initialized whether or not the symbol has been initialized.
     * @return the symbol table that has been created if needed.
     */
    public ChildSymbol addSymbol(String name, String type, Symbol.Kind kind, boolean initialized) {
        Symbol newSymbol;
        SymbolTable child;

        // we need to create a new symbol table if we have a method or inner statement (e.g. if statement)
        if (kind == Symbol.Kind.METHOD || kind == Symbol.Kind.CONSTRUCTOR  || kind == Symbol.Kind.FUNCTION || kind == Symbol.Kind.INNER) {
            child = new SymbolTable(this, null);

            if (kind == Symbol.Kind.METHOD)
                child.addSymbol("this", this.symbol.getType(), Symbol.Kind.POINTER, true);
            else if (kind == Symbol.Kind.CONSTRUCTOR)
                child.addSymbol("this", this.symbol.getType(), Symbol.Kind.POINTER, 0,true);

            newSymbol = symbolWithCount(name, type, kind, initialized, child);
            child.symbol = newSymbol;
            return new ChildSymbol(child, newSymbol);
        }

        // if child symbol table doesn't need creating
        newSymbol = symbolWithCount(name, type, kind, initialized, null);
        return new ChildSymbol(null, newSymbol);
    }

    /**
     * Add a new symbol to the symbol table whilst specifying the index
     * @param name the name of the symbol (identifier).
     * @param type the type of the symbol (i.e. the object/type that it returns or stores).
     * @param kind the kind of symbol (i.e. var, argument, static, field, subroutine, class).
     * @param index the index of the symbol.
     * @param initialized whether or not the symbol has been initialized.
     */
    public void addSymbol(String name, String type, Symbol.Kind kind, int index, boolean initialized) {
        symbolMap.put(name, new Symbol(name, type, kind, index, initialized));
    }

    /**
     * Create a new symbol with an index created by using the relevant counter and incrementing it.
     * @param name the name of the symbol (identifier).
     * @param type the type of the symbol (i.e. the object/type that it returns or stores).
     * @param kind the kind of symbol (i.e. var, argument, static, field, subroutine, class).
     * @param initialized whether or not the symbol has been initialized.
     * @param child the child symbol table.
     * @return a symbol with an associated index.
     */
    public Symbol symbolWithCount(String name, String type, Symbol.Kind kind, boolean initialized, SymbolTable child) {
        Symbol newSymbol;

        switch (kind) {
            case ARGUMENT:
            case POINTER:
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
     * Check if the symbol table contains the provided identifier locally.
     * @param name the name of the symbol.
     * @return boolean, whether the symbol table contains the symbol.
     */
    public boolean contains(String name) {
            return this.symbolMap.containsKey(name);
    }

    /**
     * Check if the current symbol table along with its parents contain the identifier.
     * @param name the name of the symbol.
     * @return boolean, whether the symbol is contained by the current symbol table or any
     * higher up in the hierarchy.
     */
    public boolean scopeContains(String name) {
        return this.scopeFindSymbol(name) != null;
    }

    /**
     * Chick if the current symbol contains the provided name along with checking parents
     * up until the subroutine is reached.
     * @param name the name of the symbol
     * @return boolean, whether the symbol is contained by the current symbol table or any up until
     * a subroutine.
     */
    public boolean subroutineContains(String name) { return this.subroutineFindSymbol(name) != null; }

    /**
     * Find a symbol in the symbol table.
     * @param name the name of the symbol to find.
     * @return the symbol.
     */
    public Symbol findSymbol(String name) {
        if (this.contains(name))
            return this.symbolMap.get(name);

        return null;
    }

    /**
     * Find a symbol in the symbol table / parent symbol table(s).
     * @param name the name of the symbol to find.
     * @return the symbol.
     */
    public Symbol scopeFindSymbol(String name) {
        SymbolTable currentTable = this;

        do {
            if (currentTable.contains(name))
                return currentTable.findSymbol(name);
            currentTable = currentTable.parent;
        } while (currentTable != null);

        return null;
    }

    /**
     * Find a symbol in the current subroutine.
     * @param name the name of the symbol.
     * @return the symbol.
     */
    public Symbol subroutineFindSymbol(String name) {
        SymbolTable currentTable = this;

        do {
            if (currentTable.contains(name))
                return currentTable.findSymbol(name);
            currentTable = currentTable.parent;
        } while (currentTable != null && currentTable.getSymbol().getKind() != Symbol.Kind.CLASS);

        return null;
    }

    /**
     * Print the symbol table by listing each symbol it contains.
     */
    public void printTable() {
        for (Map.Entry<String, Symbol> s : symbolMap.entrySet()) {
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
        for (Map.Entry<String, Symbol> s : symbolMap.entrySet()) {
            System.out.println(spacing + s.getValue());

            Symbol.Kind kind = s.getValue().getKind();
            if (kind == Symbol.Kind.METHOD || kind == Symbol.Kind.FUNCTION || kind == Symbol.Kind.CONSTRUCTOR || kind == Symbol.Kind.INNER)
                s.getValue().getChildSymbolTable().printTables(spacing + "    ");
        }
    }
}

class ChildSymbol {
    private SymbolTable newSymbolTable;
    private Symbol newSymbol;

    public ChildSymbol(SymbolTable newSymbolTable, Symbol newSymbol) {
        this.newSymbolTable = newSymbolTable;
        this.newSymbol = newSymbol;
    }

    public SymbolTable getSymbolTable() { return this.newSymbolTable; }
    public Symbol getSymbol() { return this.newSymbol; }
}