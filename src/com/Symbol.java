package com;

public class Symbol {
    private String name;
    private String type;
    private KindTypes kind;
    private SymbolTable childSymbolTable;
    private int index;
    private boolean initialized;

    public enum KindTypes {
        STATIC,
        FIELD,
        VAR,
        ARGUMENT,
        SUBROUTINE,
        CLASS
    }


    /**
     * Create a new symbol.
     * @param name name of the symbol.
     * @param type type that the symbol stores/returns.
     * @param kind the kind of symbol.
     * @param index the index that the symbol is placed at.
     * @param initialized whether the symbol is initialized
     * @param childSymbolTable the child symbol table if it's kind is class or subroutine.
     */
    public Symbol(String name, String type, KindTypes kind, int index, boolean initialized, SymbolTable childSymbolTable) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.index = index;
        this.childSymbolTable = childSymbolTable;
        this.initialized = initialized;
    }

    /**
     * Allow creation of a symbol if no child is needed
     */
    public Symbol(String name, String type, KindTypes kind, int index, boolean initialized) {
        this(name, type, kind, index, initialized, null);
    }

    public String getName() { return this.name; }
    public String getType() { return this.type; }
    public KindTypes getKind() { return this.kind; }
    public SymbolTable getChildSymbolTable() { return this.childSymbolTable; }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public String toString() {
        return "<Symbol " + name + ", " + type + ", " + kind + ", " + index + ", " + childSymbolTable + ", " + initialized +">";
    }
}
