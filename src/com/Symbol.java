package com;

public class Symbol {
    private String name;
    private String type;
    private KindTypes kind;
    private SymbolTable childSymbolTable;
    private int index;

    public enum KindTypes {
        STATIC,
        FIELD,
        VAR,
        ARGUMENT,
        PROCEDURE,
        CLASS
    }

    public Symbol(String name, String type, KindTypes kind, int index, SymbolTable childSymbolTable) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.index = index;
        this.childSymbolTable = childSymbolTable;
    }

    public Symbol(String name, String type, KindTypes kind, int index) {
        this(name, type, kind, index, null);
    }

    public String getName() { return this.name; }
    public String getType() { return this.type; }
    public KindTypes getKind() { return this.kind; }
    public SymbolTable getChildSymbolTable() { return this.childSymbolTable; }

    @Override
    public String toString() {
        return "<Symbol " + name + ", " + type + ", " + kind + ", " + index + ", " + childSymbolTable + ">";
    }
}
